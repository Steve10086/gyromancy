package com.astune.gyromancy.symbol;

import com.astune.gyromancy.util.GeometryUtils;
import com.astune.gyromancy.util.GeometryPreprocessUtils;
import com.astune.gyromancy.util.GeometryPreprocessUtils.*;
import com.astune.gyromancy.util.TemplateLoader;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Skeleton-graph-based symbol matcher.
 *
 * <p>Each template is pre-processed once (32×32 PNG → skeleton graph → feature vector).
 * At matching time the input image runs through the same skeleton pipeline, then
 * is compared against all templates via multi-layer hard+soft screening.
 *
 * <h3>Hard layers (mismatch → immediate reject)</h3>
 * <ol>
 *   <li>Open-line (endpoint) count</li>
 *   <li>Closed-loop (cycle) count</li>
 *   <li>Outer / inner open-line classification count</li>
 * </ol>
 *
 * <h3>Soft layers (confidence scoring)</h3>
 * <ol>
 *   <li>Open-line shape (width%, length%) — permutation L1 loss</li>
 *   <li>Open-line relative-angle — cyclic permutation L1 loss</li>
 * </ol>
 */
public final class SkeletonMatcher {

    private static final SkeletonMatcher INSTANCE = new SkeletonMatcher();

    private final Map<ResourceLocation, SkeletonStats> templateStats = new LinkedHashMap<>();
    private boolean initialized = false;

    private SkeletonMatcher() {}

    public static SkeletonMatcher getInstance() { return INSTANCE; }

    // ═══════════════════════ Initialization ═══════════════════════

    /** Registers a template from a classpath PNG resource. Idempotent. */
    public void registerTemplate(ResourceLocation id, String resourcePath) {
        if (templateStats.containsKey(id)) return;
        int[][] raw = TemplateLoader.load(resourcePath);
        SkeletonStats s = computeStats(raw);
        if (s != null) templateStats.put(id, s);
    }

    /** Call after all templates are registered. Marks the matcher ready. */
    public void init() { initialized = true; }

    /** Returns an unmodifiable view of all registered template stats. */
    public Map<ResourceLocation, SkeletonStats> templates() {
        return Collections.unmodifiableMap(templateStats);
    }

    // ═══════════════════════ Recognition API ═══════════════════════

    /**
     * Runs the skeleton pipeline on the given image and returns the best match
     * per template, sorted by confidence descending.
     *
     * @param image raw binary image
     * @return sorted list of (templateId, confidence) pairs for all passing templates
     */
    public List<Match> recognize(int[][] image) {
        if (!initialized) return List.of();
        SkeletonStats target = computeStats(image);
        if (target == null) return List.of();

        List<Match> results = new ArrayList<>();
        for (var e : templateStats.entrySet()) {
            SkeletonStats tpl = e.getValue();
            if (target.openLines != tpl.openLines) continue;
            if (target.closedLoops != tpl.closedLoops) continue;
            if (target.outerOpenLines != tpl.outerOpenLines) continue;
            if (target.innerOpenLines != tpl.innerOpenLines) continue;

            double shapeConf = openLineConfidence(tpl.openLineData, target.openLineData);
            double angleConf = openLineAngleConfidence(tpl.openLineAngles, target.openLineAngles);
            double combined = 0.40 * shapeConf + 0.60 * angleConf;

            results.add(new Match(e.getKey(), (float) combined));
        }
        results.sort((a, b) -> Float.compare(b.confidence, a.confidence));
        return results;
    }

    /**
     * Backward-compatible single-template match (used by GeometricMatcher wrapper).
     *
     * @param image raw binary image
     * @param id    template ResourceLocation to match against
     * @return confidence [0, 1], or 0 if template not registered
     */
    public float matchOne(int[][] image, ResourceLocation id) {
        SkeletonStats tpl = templateStats.get(id);
        if (tpl == null) return 0f;
        SkeletonStats target = computeStats(image);
        if (target == null) return 0f;

        if (target.openLines != tpl.openLines) return 0f;
        if (target.closedLoops != tpl.closedLoops) return 0f;
        if (target.outerOpenLines != tpl.outerOpenLines) return 0f;
        if (target.innerOpenLines != tpl.innerOpenLines) return 0f;

        double shapeConf = openLineConfidence(tpl.openLineData, target.openLineData);
        double angleConf = openLineAngleConfidence(tpl.openLineAngles, target.openLineAngles);
        return (float) (0.40 * shapeConf + 0.60 * angleConf);
    }

    /** Result of {@link #recognize}. */
    public record Match(ResourceLocation templateId, float confidence) {}

    // ═══════════════════════ Skeleton pipeline ═══════════════════════

    private static SkeletonStats computeStats(int[][] raw) {
        int[][] filled  = GeometryPreprocessUtils.fillSmallHoles(raw);
        int minDim = Math.min(filled.length, filled[0].length);
        int K = (128 + minDim - 1) / minDim;

        int[][] up     = GeometryPreprocessUtils.upscaleConnectivityPreserving(filled, 128);
        int[][] smooth = GeometryPreprocessUtils.gaussianSmoothBinary(up, K / 3.0);
        int[][] skel   = GeometryPreprocessUtils.skeletonize(smooth);
        int[][] cropped = GeometryUtils.cropToForeground(skel);

        List<SkelNode> nodes = new ArrayList<>(GeometryPreprocessUtils.extractNodes(cropped));
        List<SkelEdge> edges = new ArrayList<>(GeometryPreprocessUtils.traceEdges(cropped, nodes));
        GeometryPreprocessUtils.mergeZeroEdges(nodes, edges);
        int minBranch = Math.max(cropped[0].length, cropped.length) / 10;
        GeometryPreprocessUtils.pruneShortBranches(nodes, edges, minBranch);
        GeometryPreprocessUtils.splitEdgesAtSupportPoints(nodes, edges,
                cropped[0].length, cropped.length);

        int openLines = 0;
        for (SkelNode nd : nodes) if (nd.isEndpoint()) openLines++;

        int graphCycles = countGraphCycles(nodes, edges);
        int pureCycles  = countPureCycleComponents(cropped, nodes);
        int closedLoops = graphCycles + pureCycles;

        List<OpenLineData> openLineData = new ArrayList<>();
        List<Double> openAngles = new ArrayList<>();
        List<int[]> openEndpoints = new ArrayList<>();
        extractOpenLines(nodes, edges, openLineData, openAngles, openEndpoints);
        double[] anglesArr = openAngles.stream().mapToDouble(Double::doubleValue).toArray();

        int outerOpen = 0, innerOpen = 0;
        if (graphCycles > 0) {
            double[] ctr = cycleCentroid(nodes, edges);
            if (!Double.isNaN(ctr[0])) {
                for (int i = 0; i < openEndpoints.size(); i++) {
                    int[] ep = openEndpoints.get(i);
                    SkelNode junc = findOpenLineJunction(nodes, edges, ep[0], ep[1]);
                    if (junc != null) {
                        if (dist2(ep[0], ep[1], ctr[0], ctr[1]) > dist2(junc.x(), junc.y(), ctr[0], ctr[1]))
                            outerOpen++;
                        else innerOpen++;
                    } else {
                        outerOpen++;
                    }
                }
            } else {
                outerOpen = openLines;
            }
        } else {
            outerOpen = openLines;
        }

        return new SkeletonStats(nodes.size(), edges.size(), openLines, closedLoops,
                outerOpen, innerOpen, openLineData, anglesArr);
    }

    // ═══════════════════════ Graph utilities ═══════════════════════

    private static int countGraphCycles(List<SkelNode> nodes, List<SkelEdge> edges) {
        int n = nodes.size();
        if (n == 0) return 0;
        List<List<Integer>> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
        for (SkelEdge e : edges) { adj.get(e.from()).add(e.to()); adj.get(e.to()).add(e.from()); }
        boolean[] visited = new boolean[n];
        int comp = 0;
        for (int i = 0; i < n; i++) {
            if (visited[i]) continue; comp++;
            Deque<Integer> s = new ArrayDeque<>(); s.push(i); visited[i] = true;
            while (!s.isEmpty())
                for (int nb : adj.get(s.pop()))
                    if (!visited[nb]) { visited[nb] = true; s.push(nb); }
        }
        return Math.max(0, edges.size() - n + comp);
    }

    private static int countPureCycleComponents(int[][] skel, List<SkelNode> nodes) {
        int h = skel.length, w = skel[0].length;
        boolean[][] isNode = new boolean[h][w];
        for (SkelNode nd : nodes) isNode[nd.y()][nd.x()] = true;
        boolean[][] visited = new boolean[h][w];
        int pure = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (skel[y][x] == 0 || visited[y][x]) continue;
            Deque<int[]> q = new ArrayDeque<>(); q.add(new int[]{x, y}); visited[y][x] = true;
            boolean hasNode = isNode[y][x];
            while (!q.isEmpty()) {
                int[] p = q.poll(); int cx = p[0], cy = p[1];
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = cx+dx, ny = cy+dy;
                    if (ny<0||ny>=h||nx<0||nx>=w) continue;
                    if (skel[ny][nx]==0||visited[ny][nx]) continue;
                    visited[ny][nx] = true;
                    if (isNode[ny][nx]) hasNode = true;
                    q.add(new int[]{nx, ny});
                }
            }
            if (!hasNode) pure++;
        }
        return pure;
    }

    private static double[] cycleCentroid(List<SkelNode> nodes, List<SkelEdge> edges) {
        int n = nodes.size();
        boolean[] isTree = new boolean[edges.size()];
        int[] deg = new int[n];
        List<List<Integer>> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
        for (int ei = 0; ei < edges.size(); ei++) {
            SkelEdge e = edges.get(ei);
            adj.get(e.from()).add(ei); adj.get(e.to()).add(ei);
            deg[e.from()]++; deg[e.to()]++;
        }
        Deque<Integer> leaves = new ArrayDeque<>();
        for (int i = 0; i < n; i++) if (deg[i] == 1) leaves.add(i);
        while (!leaves.isEmpty()) {
            int leaf = leaves.poll();
            for (int ei : adj.get(leaf)) {
                if (isTree[ei]) continue; isTree[ei] = true;
                SkelEdge e = edges.get(ei);
                int other = (e.from() == leaf) ? e.to() : e.from();
                deg[other]--;
                if (deg[other] == 1) leaves.add(other);
            }
        }
        double sx = 0, sy = 0; int cnt = 0;
        for (int ei = 0; ei < edges.size(); ei++) {
            if (isTree[ei]) continue;
            for (int[] p : edges.get(ei).path()) { sx += p[0]; sy += p[1]; cnt++; }
        }
        if (cnt == 0) return new double[]{Double.NaN, Double.NaN};
        return new double[]{sx / cnt, sy / cnt};
    }

    private static SkelNode findOpenLineJunction(List<SkelNode> nodes, List<SkelEdge> edges,
            int epX, int epY) {
        int n = nodes.size();
        List<List<Integer>> a = new ArrayList<>(n);
        for (int i = 0; i < n; i++) a.add(new ArrayList<>());
        for (int ei = 0; ei < edges.size(); ei++) {
            SkelEdge e = edges.get(ei);
            a.get(e.from()).add(ei); a.get(e.to()).add(ei);
        }
        for (SkelNode nd : nodes) {
            if (!nd.isEndpoint() || nd.x() != epX || nd.y() != epY) continue;
            int curId = nd.id(), curEdge = a.get(curId).get(0);
            SkelEdge ce = edges.get(curEdge);
            int nid = (ce.from() == curId) ? ce.to() : ce.from();
            while (nodes.get(nid).degree() == 2) {
                int ne = -1;
                for (int ei : a.get(nid)) if (ei != curEdge) { ne = ei; break; }
                if (ne < 0) break;
                ce = edges.get(ne);
                int nn = (ce.from() == nid) ? ce.to() : ce.from();
                curEdge = ne; nid = nn;
            }
            return nodes.get(nid);
        }
        return null;
    }

    // ═══════════════════════ Open-line extraction ═══════════════════════

    record OpenLineData(double widthPct, double lengthPct) {}
    record SkeletonStats(int nodes, int edges, int openLines, int closedLoops,
                         int outerOpenLines, int innerOpenLines,
                         List<OpenLineData> openLineData,
                         double[] openLineAngles) {}

    private static void extractOpenLines(List<SkelNode> nodes, List<SkelEdge> edges,
            List<OpenLineData> shapedata, List<Double> angleData,
            List<int[]> endpointData) {
        int nn = nodes.size();
        if (nn == 0) return;
        List<List<Integer>> adj = buildAdj(nn, edges);
        for (SkelNode nd : nodes) {
            if (!nd.isEndpoint()) continue;
            int prevNode = nd.id();
            int curEdge = adj.get(prevNode).get(0);
            SkelEdge firstEdge = edges.get(curEdge);
            int curNode = (firstEdge.from() == prevNode) ? firstEdge.to() : firstEdge.from();
            List<int[]> fullPath = new ArrayList<>();
            fullPath.add(new int[]{nd.x(), nd.y()});
            appendEdgePath(fullPath, firstEdge, prevNode);
            while (nodes.get(curNode).degree() == 2) {
                int nextEdge = -1;
                for (int ei : adj.get(curNode)) if (ei != curEdge) { nextEdge = ei; break; }
                if (nextEdge < 0) break;
                SkelEdge e = edges.get(nextEdge);
                int nn2 = (e.from() == curNode) ? e.to() : e.from();
                appendEdgePathSkipFirst(fullPath, e, curNode);
                curEdge = nextEdge; curNode = nn2;
            }
            SkelNode junction = nodes.get(curNode);
            if (!fullPath.isEmpty()) {
                int[] last = fullPath.get(fullPath.size() - 1);
                if (last[0] != junction.x() || last[1] != junction.y())
                    fullPath.add(new int[]{junction.x(), junction.y()});
            }
            double dx = junction.x() - nd.x(), dy = junction.y() - nd.y();
            double straightLen = Math.sqrt(dx * dx + dy * dy);
            if (straightLen < 0.5) continue;
            double maxPerp = 0;
            for (int[] p : fullPath)
                maxPerp = Math.max(maxPerp,
                        Math.abs((p[0] - nd.x()) * dy - (p[1] - nd.y()) * dx) / straightLen);
            shapedata.add(new OpenLineData(maxPerp / straightLen * 100.0,
                    (double) fullPath.size() / straightLen * 100.0));
            endpointData.add(new int[]{nd.x(), nd.y()});
            double angle = Math.atan2(dy, dx);
            if (angle < 0) angle += 2 * Math.PI;
            angleData.add(angle);
        }
        // Sort by width
        Integer[] idx = new Integer[shapedata.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparingDouble(i -> shapedata.get(i).widthPct));
        List<OpenLineData> s1 = new ArrayList<>(); List<Double> s2 = new ArrayList<>();
        List<int[]> s3 = new ArrayList<>();
        for (int i : idx) { s1.add(shapedata.get(i)); s2.add(angleData.get(i)); s3.add(endpointData.get(i)); }
        shapedata.clear(); shapedata.addAll(s1);
        angleData.clear(); angleData.addAll(s2);
        endpointData.clear(); endpointData.addAll(s3);
    }

    private static List<List<Integer>> buildAdj(int n, List<SkelEdge> edges) {
        List<List<Integer>> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
        for (int ei = 0; ei < edges.size(); ei++) {
            SkelEdge e = edges.get(ei);
            adj.get(e.from()).add(ei); adj.get(e.to()).add(ei);
        }
        return adj;
    }

    private static void appendEdgePath(List<int[]> dest, SkelEdge e, int fromNode) {
        List<int[]> p = e.path();
        if (e.from() == fromNode) dest.addAll(p);
        else for (int i = p.size()-1; i >= 0; i--) dest.add(p.get(i));
    }

    private static void appendEdgePathSkipFirst(List<int[]> dest, SkelEdge e, int fromNode) {
        List<int[]> p = e.path();
        if (e.from() == fromNode) {
            for (int i = 1; i < p.size(); i++) dest.add(p.get(i));
        } else {
            for (int i = p.size()-2; i >= 0; i--) dest.add(p.get(i));
        }
    }

    // ═══════════════════════ Soft matching ═══════════════════════

    static double openLineConfidence(List<OpenLineData> tpl, List<OpenLineData> target) {
        int n = tpl.size();
        if (n == 0) return 1.0;
        double maxW = 0, maxL = 0;
        for (OpenLineData d : tpl) { if (d.widthPct > maxW) maxW = d.widthPct; if (d.lengthPct > maxL) maxL = d.lengthPct; }
        for (OpenLineData d : target) { if (d.widthPct > maxW) maxW = d.widthPct; if (d.lengthPct > maxL) maxL = d.lengthPct; }
        double norm = maxW + maxL;
        if (norm < 0.001) return 1.0;
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) perm[i] = i;
        double best = Double.MAX_VALUE;
        do {
            double wL = 0, lL = 0;
            for (int i = 0; i < n; i++) {
                wL += Math.abs(tpl.get(i).widthPct - target.get(perm[i]).widthPct);
                lL += Math.abs(tpl.get(i).lengthPct - target.get(perm[i]).lengthPct);
            }
            best = Math.min(best, (wL + lL) / norm);
        } while (nextPermutation(perm));
        return Math.max(0.0, 1.0 - best);
    }

    static double openLineAngleConfidence(double[] tplAngles, double[] tgtAngles) {
        int n = tplAngles.length;
        if (n == 0) return 1.0;
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) perm[i] = i;
        double best = Double.MAX_VALUE;
        do {
            for (int shift = 0; shift < n; shift++) {
                double sum = 0;
                for (int i = 0; i < n; i++)
                    sum += angleDist(tplAngles[i], tgtAngles[(perm[i] + shift) % n]);
                best = Math.min(best, sum / (n * Math.PI));
            }
        } while (nextPermutation(perm));
        return Math.max(0.0, 1.0 - best);
    }

    private static double angleDist(double a, double b) {
        double d = Math.abs(a - b);
        return d > Math.PI ? 2 * Math.PI - d : d;
    }

    private static boolean nextPermutation(int[] a) {
        int n = a.length, i = n - 2;
        while (i >= 0 && a[i] >= a[i + 1]) i--;
        if (i < 0) return false;
        int j = n - 1;
        while (a[j] <= a[i]) j--;
        int t = a[i]; a[i] = a[j]; a[j] = t;
        for (int l = i+1, r = n-1; l < r; l++, r--) { t = a[l]; a[l] = a[r]; a[r] = t; }
        return true;
    }

    private static double dist2(double x1, double y1, double x2, double y2) {
        double dx = x1-x2, dy = y1-y2;
        return dx*dx + dy*dy;
    }
}
