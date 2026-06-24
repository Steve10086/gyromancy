package com.astune.gyromancy.symbol;

import com.astune.gyromancy.util.GeometryUtils;
import com.astune.gyromancy.util.GeometryPreprocessUtils;
import com.astune.gyromancy.util.GeometryPreprocessUtils.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import org.junit.jupiter.api.Test;

/**
 * Multi-layer skeleton-based symbol matching.
 *
 * <p>Full skeleton pipeline per image (same as {@link SkeletonExportTest}):
 * <pre>
 *   PNG → fillSmallHoles → upscaleConnectivityPreserving(≥128)
 *       → gaussianSmoothBinary(σ=K/3) → skeletonize
 *       → cropToForeground → extractNodes → traceEdges
 *       → mergeZeroEdges → pruneShortBranches(max(w,h)/10)
 *       → splitEdgesAtSupportPoints
 * </pre>
 *
 * <h3>Matching layers</h3>
 * <ol>
 *   <li><b>Hard — open-line count</b>: number of endpoint nodes (degree-1). Reject if mismatch.</li>
 *   <li><b>Hard — closed-loop count</b>: graph cycles + pure-cycle components. Reject if mismatch.</li>
 *   <li><b>Hard — outer/inner open-line count</b>: classify each open line as outer
 *       (endpoint outside closed-loop polygon) or inner (endpoint inside).
 *       Reject if counts differ.</li>
 *   <li><b>Soft — open-line relative-angle</b>: for each open line, compute the
 *       straight-line angle from endpoint to the nearest junction (degree≥3).
 *       Sort angles to form a rotation-invariant signature.
 *       Permutation search finds the best cyclic alignment between template
 *       and target angle sets. Normalised L1 loss on [0, 2π).</li>
 * </ol>
 *
 * <p>Output: {@code build/skeleton_viz/hard_match/{testName}.txt} —
 * one file per test image listing all templates that passed the hard layers
 * with their soft-match confidence.
 */
public class SymbolMatcherTest {

    @Test
    public void matchAll() throws IOException { run(); }

    public static void main(String[] args) throws IOException { run(); }

    // ═══════════════════════ Entry point ═══════════════════════

    private static void run() throws IOException {
        File outDir = new File("build/skeleton_viz/hard_match");
        if (outDir.exists()) {
            File[] old = outDir.listFiles();
            if (old != null) for (File f : old) f.delete();
        }
        outDir.mkdirs();

        File symbolTplDir = new File("src/main/resources/assets/gyromancy/textures/symbol");
        File testDir = new File("src/test/resources/test_images/symbol");

        int count = matchDir(testDir, symbolTplDir, outDir);
        System.out.println("[SymbolMatcher] Done. " + count + " test cases → " + outDir.getAbsolutePath());
    }

    // ═══════════════════════ Core matching ═══════════════════════

    private static int matchDir(File testDir, File tplDir, File outDir) {
        File[] testFiles = testDir.listFiles((d, n) -> n.endsWith(".png"));
        File[] tplFiles  = tplDir.listFiles((d, n) -> n.endsWith(".png"));
        if (testFiles == null || tplFiles == null) return 0;

        // Pre-compute skeleton stats for every template (done once)
        Map<String, SkeletonStats> tplStats = new LinkedHashMap<>();
        for (File f : tplFiles) {
            int[][] raw = loadPng(f);
            if (raw == null) continue;
            SkeletonStats s = computeStats(raw);
            if (s != null) tplStats.put(noExt(f.getName()), s);
        }

        int count = 0;
        for (File testFile : testFiles) {
            String testName = noExt(testFile.getName());
            int[][] raw = loadPng(testFile);
            if (raw == null) continue;
            SkeletonStats testStats = computeStats(raw);
            if (testStats == null) continue;

            // ── Hard-match screening + soft-match confidence ──
            Map<String, double[]> passed = new LinkedHashMap<>();
            for (var e : tplStats.entrySet()) {
                SkeletonStats tpl = e.getValue();
                // Layer 1: open-line count must match
                if (testStats.openLines != tpl.openLines) continue;
                // Layer 2: closed-loop count must match
                if (testStats.closedLoops != tpl.closedLoops) continue;
                // Layer 2b: outer/inner open-line counts must match
                if (testStats.outerOpenLines != tpl.outerOpenLines) continue;
                if (testStats.innerOpenLines != tpl.innerOpenLines) continue;
                // Layer 3 (soft): open-line shape similarity
                double shapeConf = openLineConfidence(tpl.openLineData, testStats.openLineData);
                // Layer 3b (soft): open-line relative-angle similarity
                double angleConf = openLineAngleConfidence(tpl.openLineAngles, testStats.openLineAngles);
                double combined = 0.40 * shapeConf + 0.60 * angleConf;
                passed.put(e.getKey(), new double[]{shapeConf, angleConf, combined});
            }

            writeResult(new File(outDir, testName + ".txt"),
                    testName, testStats, tplStats, passed);
            count++;
        }
        return count;
    }

    // ═══════════════════════ Skeleton pipeline ═══════════════════════

    /** Runs the full skeleton-graph pipeline and extracts matching features. */
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

        // Feature 1 — open lines: each endpoint (degree-1 node) represents one
        // open-curve arm radiating from a junction or terminating a simple curve.
        int openLines = 0;
        for (SkelNode nd : nodes)
            if (nd.isEndpoint()) openLines++;

        // Feature 2 — closed loops: graph cycles + pure-cycle skeleton components.
        int graphCycles = countGraphCycles(nodes, edges);
        int pureCycles  = countPureCycleComponents(cropped, nodes);
        int closedLoops = graphCycles + pureCycles;

        // Feature 3 — open-line shapes and angles for soft matching
        List<OpenLineData> openLineData = new ArrayList<>();
        List<Double> openAngles = new ArrayList<>();
        List<int[]> openEndpoints = new ArrayList<>();        // endpoint pixel for classification
        extractOpenLines(nodes, edges, openLineData, openAngles, openEndpoints);
        double[] anglesArr = openAngles.stream().mapToDouble(Double::doubleValue).toArray();

        // Feature 4 — outer / inner open-line classification.
        // For shapes with a closed loop: an open line is "outer" if the endpoint
        // is farther from the loop's centroid than its junction is; "inner" otherwise.
        int outerOpen = 0, innerOpen = 0;
        if (graphCycles > 0) {
            double[] ctr = cycleCentroid(nodes, edges);
            if (!Double.isNaN(ctr[0])) {
                for (int i = 0; i < openEndpoints.size(); i++) {
                    int[] ep = openEndpoints.get(i);
                    // Find the junction this open line connects to (via angleData ordering)
                    SkelNode junc = findOpenLineJunction(nodes, edges, ep[0], ep[1]);
                    if (junc != null) {
                        double epDist = dist2(ep[0], ep[1], ctr[0], ctr[1]);
                        double jcDist = dist2(junc.x(), junc.y(), ctr[0], ctr[1]);
                        if (epDist > jcDist) outerOpen++;
                        else innerOpen++;
                    } else {
                        outerOpen++; // fallback
                    }
                }
            } else {
                outerOpen = openLines;
            }
        } else {
            // No closed loop: all open lines are "outer"
            outerOpen = openLines;
        }

        return new SkeletonStats(nodes.size(), edges.size(), openLines, closedLoops,
                outerOpen, innerOpen, openLineData, anglesArr);
    }

    /**
     * Graph-theoretic cycle count: edges − nodes + connectedComponents.
     * Each connected component contributes (edges − nodes + 1) independent cycles.
     */
    private static int countGraphCycles(List<SkelNode> nodes, List<SkelEdge> edges) {
        int n = nodes.size();
        if (n == 0) return 0;

        List<List<Integer>> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
        for (SkelEdge e : edges) {
            adj.get(e.from()).add(e.to());
            adj.get(e.to()).add(e.from());
        }

        boolean[] visited = new boolean[n];
        int components = 0;
        for (int i = 0; i < n; i++) {
            if (visited[i]) continue;
            components++;
            Deque<Integer> stack = new ArrayDeque<>();
            stack.push(i);
            visited[i] = true;
            while (!stack.isEmpty()) {
                int v = stack.pop();
                for (int nb : adj.get(v)) {
                    if (!visited[nb]) { visited[nb] = true; stack.push(nb); }
                }
            }
        }
        return Math.max(0, edges.size() - n + components);
    }

    /**
     * Counts 8-connected skeleton components that contain zero extracted nodes.
     * These are pure cycles (every pixel is degree-2), so the graph layer sees
     * nothing, but they still represent one closed loop each.
     */
    private static int countPureCycleComponents(int[][] skel, List<SkelNode> nodes) {
        int h = skel.length, w = skel[0].length;
        boolean[][] isNode = new boolean[h][w];
        for (SkelNode nd : nodes) isNode[nd.y()][nd.x()] = true;

        boolean[][] visited = new boolean[h][w];
        int pureCycles = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (skel[y][x] == 0 || visited[y][x]) continue;

                Deque<int[]> q = new ArrayDeque<>();
                q.add(new int[]{x, y});
                visited[y][x] = true;
                boolean hasNode = isNode[y][x];

                while (!q.isEmpty()) {
                    int[] p = q.poll();
                    int cx = p[0], cy = p[1];
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0) continue;
                            int nx = cx + dx, ny = cy + dy;
                            if (ny < 0 || ny >= h || nx < 0 || nx >= w) continue;
                            if (skel[ny][nx] == 0 || visited[ny][nx]) continue;
                            visited[ny][nx] = true;
                            if (isNode[ny][nx]) hasNode = true;
                            q.add(new int[]{nx, ny});
                        }
                    }
                }
                if (!hasNode) pureCycles++;
            }
        }
        return pureCycles;
    }

    // ═══════════════════════ Cycle polygon extraction ═══════════════════════

    /**
     * Computes the centroid of the closed-loop cycle in the skeleton graph.
     * Strips tree edges (incident to endpoints), then collects all pixel
     * positions from the remaining cycle edges.
     */
    private static double[] cycleCentroid(List<SkelNode> nodes, List<SkelEdge> edges) {
        int n = nodes.size();
        boolean[] isTree = new boolean[edges.size()];
        int[] deg = new int[n];
        List<List<Integer>> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
        for (int ei = 0; ei < edges.size(); ei++) {
            SkelEdge e = edges.get(ei);
            adj.get(e.from()).add(ei);
            adj.get(e.to()).add(ei);
            deg[e.from()]++; deg[e.to()]++;
        }
        Deque<Integer> leaves = new ArrayDeque<>();
        for (int i = 0; i < n; i++) if (deg[i] == 1) leaves.add(i);
        while (!leaves.isEmpty()) {
            int leaf = leaves.poll();
            for (int ei : adj.get(leaf)) {
                if (isTree[ei]) continue;
                isTree[ei] = true;
                SkelEdge e = edges.get(ei);
                int other = (e.from() == leaf) ? e.to() : e.from();
                deg[other]--;
                if (deg[other] == 1) leaves.add(other);
            }
        }
        // Collect pixel positions from all non-tree edges
        double sx = 0, sy = 0;
        int count = 0;
        for (int ei = 0; ei < edges.size(); ei++) {
            if (isTree[ei]) continue;
            for (int[] p : edges.get(ei).path()) { sx += p[0]; sy += p[1]; count++; }
        }
        if (count == 0) return new double[]{Double.NaN, Double.NaN};
        return new double[]{sx / count, sy / count};
    }

    /**
     * Finds the junction (degree ≥ 3) that the open line starting at the given
     * endpoint connects to, by walking through d2 chains.
     */
    private static SkelNode findOpenLineJunction(List<SkelNode> nodes, List<SkelEdge> edges,
            int epX, int epY) {
        int n = nodes.size();
        List<List<Integer>> a = new ArrayList<>(n);
        for (int i = 0; i < n; i++) a.add(new ArrayList<>());
        for (int ei = 0; ei < edges.size(); ei++) {
            SkelEdge e = edges.get(ei);
            a.get(e.from()).add(ei);
            a.get(e.to()).add(ei);
        }
        // Find the endpoint node at (epX, epY)
        for (SkelNode nd : nodes) {
            if (!nd.isEndpoint() || nd.x() != epX || nd.y() != epY) continue;
            // Walk from endpoint through d2 chain to junction
            int curId = nd.id();
            int curEdge = a.get(curId).get(0);
            SkelEdge ce = edges.get(curEdge);
            int nid = (ce.from() == curId) ? ce.to() : ce.from();
            while (nodes.get(nid).degree() == 2) {
                int nextEdge = -1;
                for (int ei : a.get(nid)) if (ei != curEdge) { nextEdge = ei; break; }
                if (nextEdge < 0) break;
                ce = edges.get(nextEdge);
                int nn = (ce.from() == nid) ? ce.to() : ce.from();
                curEdge = nextEdge;
                nid = nn;
            }
            return nodes.get(nid);
        }
        return null;
    }

    private static double dist2(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2, dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    // ═══════════════════════ Open-line shape extraction ═══════════════════════

    /**
     * Shape descriptor for one open curve arm.
     *
     * @param widthPct  max perpendicular distance from path to the endpoint→junction
     *                  straight line, as % of that straight-line length
     * @param lengthPct total skeleton-path length from endpoint to junction,
     *                  as % of the straight-line length (can exceed 100% for spirals)
     */
    record OpenLineData(double widthPct, double lengthPct) {
        @Override public String toString() {
            return String.format("(w=%.0f%%, l=%.0f%%)", widthPct, lengthPct);
        }
    }

    /**
     * For each endpoint (degree-1), follows edges through degree-2 support-point
     * nodes until reaching the first junction (degree≥3) or another endpoint.
     * Appends shape descriptors and straight-line angles to the given lists.
     */
    static void extractOpenLines(List<SkelNode> nodes, List<SkelEdge> edges,
            List<OpenLineData> shapedata, List<Double> angleData,
            List<int[]> endpointData) {

        int nn = nodes.size();
        if (nn == 0) return;

        // Build adjacency: nodeId → list of edge indices
        List<List<Integer>> adj = new ArrayList<>(nn);
        for (int i = 0; i < nn; i++) adj.add(new ArrayList<>());
        for (int ei = 0; ei < edges.size(); ei++) {
            SkelEdge e = edges.get(ei);
            adj.get(e.from()).add(ei);
            adj.get(e.to()).add(ei);
        }

        for (SkelNode nd : nodes) {
            if (!nd.isEndpoint()) continue;             // only start from endpoints

            // ── walk from endpoint through degree-2 chain to junction ──
            int prevNode = nd.id();
            int curEdge  = adj.get(prevNode).get(0);     // endpoint has exactly 1 edge
            SkelEdge firstEdge = edges.get(curEdge);
            int curNode = (firstEdge.from() == prevNode) ? firstEdge.to() : firstEdge.from();

            List<int[]> fullPath = new ArrayList<>();
            fullPath.add(new int[]{nd.x(), nd.y()});     // include endpoint itself

            // Collect first edge path in the correct direction
            appendEdgePath(fullPath, firstEdge, prevNode);

            // Follow through degree-2 nodes
            while (nodes.get(curNode).degree() == 2) {
                // Find the other edge from this support-point node
                int nextEdge = -1;
                for (int ei : adj.get(curNode)) {
                    if (ei != curEdge) { nextEdge = ei; break; }
                }
                if (nextEdge < 0) break;                  // dead end (shouldn't happen)

                SkelEdge e = edges.get(nextEdge);
                int nextNode = (e.from() == curNode) ? e.to() : e.from();

                // Skip the first point (it's curNode's position, already in path)
                appendEdgePathSkipFirst(fullPath, e, curNode);

                curEdge = nextEdge;
                curNode = nextNode;
            }

            // The terminal node is a junction (degree≥3) or another endpoint
            SkelNode junction = nodes.get(curNode);
            // The last pixel of the path should already be the junction position
            // (traceEdges includes the target node in the path). If not, add it.
            if (!fullPath.isEmpty()) {
                int[] last = fullPath.get(fullPath.size() - 1);
                if (last[0] != junction.x() || last[1] != junction.y()) {
                    fullPath.add(new int[]{junction.x(), junction.y()});
                }
            }

            // ── compute width, length, and angle ──
            double dx = junction.x() - nd.x();
            double dy = junction.y() - nd.y();
            double straightLen = Math.sqrt(dx * dx + dy * dy);
            if (straightLen < 0.5) continue;            // degenerate

            double maxPerp = 0;
            for (int[] p : fullPath) {
                // perpendicular distance from p to line (nd → junction)
                double perp = Math.abs((p[0] - nd.x()) * dy - (p[1] - nd.y()) * dx) / straightLen;
                if (perp > maxPerp) maxPerp = perp;
            }

            double widthPct  = maxPerp / straightLen * 100.0;
            double lengthPct = (double) fullPath.size() / straightLen * 100.0;

            shapedata.add(new OpenLineData(widthPct, lengthPct));
            endpointData.add(new int[]{nd.x(), nd.y()});      // endpoint position for classification

            // Straight-line angle endpoint → junction, normalised to [0, 2π)
            double angle = Math.atan2(dy, dx);
            if (angle < 0) angle += 2 * Math.PI;
            angleData.add(angle);
        }

        // Sort by width for stable comparison
        Integer[] idx = new Integer[shapedata.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparingDouble(i -> shapedata.get(i).widthPct));
        // Reorder all three lists consistently
        List<OpenLineData> sorted = new ArrayList<>(shapedata.size());
        List<Double> sortedAngles = new ArrayList<>(angleData.size());
        List<int[]> sortedEps = new ArrayList<>(endpointData.size());
        for (int i : idx) { sorted.add(shapedata.get(i)); sortedAngles.add(angleData.get(i)); sortedEps.add(endpointData.get(i)); }
        shapedata.clear(); shapedata.addAll(sorted);
        angleData.clear(); angleData.addAll(sortedAngles);
        endpointData.clear(); endpointData.addAll(sortedEps);
    }

    /** Appends an edge's path to {@code dest}, oriented from {@code fromNode} outward. */
    private static void appendEdgePath(List<int[]> dest, SkelEdge e, int fromNode) {
        List<int[]> path = e.path();
        if (e.from() == fromNode) {
            dest.addAll(path);                               // forward
        } else {
            for (int i = path.size() - 1; i >= 0; i--)       // reverse
                dest.add(path.get(i));
        }
    }

    /** Like {@link #appendEdgePath} but skips the first point (the node itself, already in dest). */
    private static void appendEdgePathSkipFirst(List<int[]> dest, SkelEdge e, int fromNode) {
        List<int[]> path = e.path();
        if (e.from() == fromNode) {
            for (int i = 1; i < path.size(); i++) dest.add(path.get(i));
        } else {
            for (int i = path.size() - 2; i >= 0; i--) dest.add(path.get(i));
        }
    }

    // ═══════════════════════ Soft-match: open-line shape ═══════════════════════

    /**
     * Computes the best-match confidence between two sets of open lines.
     *
     * <p>Tries every permutation of the target lines and picks the one that
     * minimises L1 loss on (width, length) pairs, normalised by the maximum
     * width and length values across both sets.
     *
     * @return confidence in [0, 1]; 1.0 if zero open lines.
     */
    static double openLineConfidence(List<OpenLineData> tpl, List<OpenLineData> target) {
        int n = tpl.size();
        if (n == 0) return 1.0;
        // tpl and target should have the same count (hard match already checked)

        // Collect max values for normalisation
        double maxW = 0, maxL = 0;
        for (OpenLineData d : tpl) {
            if (d.widthPct > maxW) maxW = d.widthPct;
            if (d.lengthPct > maxL) maxL = d.lengthPct;
        }
        for (OpenLineData d : target) {
            if (d.widthPct > maxW) maxW = d.widthPct;
            if (d.lengthPct > maxL) maxL = d.lengthPct;
        }
        double norm = maxW + maxL;
        if (norm < 0.001) return 1.0;                     // all lines are degenerate

        // Brute-force permutation search (N ≤ 6, worst 6! = 720)
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) perm[i] = i;

        double bestLoss = Double.MAX_VALUE;
        do {
            double wLoss = 0, lLoss = 0;
            for (int i = 0; i < n; i++) {
                wLoss += Math.abs(tpl.get(i).widthPct  - target.get(perm[i]).widthPct);
                lLoss += Math.abs(tpl.get(i).lengthPct - target.get(perm[i]).lengthPct);
            }
            double loss = (wLoss + lLoss) / norm;
            if (loss < bestLoss) bestLoss = loss;
        } while (nextPermutation(perm));

        return Math.max(0.0, 1.0 - bestLoss);
    }

    // ═══════════════════════ Soft-match: open-line relative angle ═══════════════════════

    /**
     * Confidence based on relative angles between open lines.
     *
     * <p>Each open line's straight-line angle (endpoint → junction) is treated
     * as a feature. The set is rotation-invariant — sorted angles capture the
     * relative pattern. Brute-force cyclic-permutation search finds the best
     * alignment between template and target.
     *
     * <p>Loss = average angular distance across matched pairs, normalised to [0,1]
     * by dividing by π (worst-case: 180° per pair).
     */
    static double openLineAngleConfidence(double[] tplAngles, double[] tgtAngles) {
        int n = tplAngles.length;
        if (n == 0) return 1.0;                              // no open lines

        // Brute-force permutation + cyclic shift for rotation invariance
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) perm[i] = i;

        double bestLoss = Double.MAX_VALUE;
        do {
            // For each permutation, try all cyclic shifts of target
            for (int shift = 0; shift < n; shift++) {
                double sum = 0;
                for (int i = 0; i < n; i++) {
                    int j = (perm[i] + shift) % n;
                    sum += angleDistance(tplAngles[i], tgtAngles[j]);
                }
                double loss = sum / (n * Math.PI);            // normalise to [0,1]
                if (loss < bestLoss) bestLoss = loss;
            }
        } while (nextPermutation(perm));

        return Math.max(0.0, 1.0 - bestLoss);
    }

    /** Shortest unsigned angle difference on circle [0, π]. */
    private static double angleDistance(double a, double b) {
        double d = Math.abs(a - b);
        return d > Math.PI ? 2 * Math.PI - d : d;
    }

    /** Lexicographic next permutation. Returns false when wrapped to identity. */
    private static boolean nextPermutation(int[] a) {
        int n = a.length;
        // Find longest decreasing suffix
        int i = n - 2;
        while (i >= 0 && a[i] >= a[i + 1]) i--;
        if (i < 0) return false;                            // last permutation
        // Find rightmost element > a[i] in suffix
        int j = n - 1;
        while (a[j] <= a[i]) j--;
        // Swap and reverse suffix
        int tmp = a[i]; a[i] = a[j]; a[j] = tmp;
        for (int l = i + 1, r = n - 1; l < r; l++, r--) { tmp = a[l]; a[l] = a[r]; a[r] = tmp; }
        return true;
    }

    // ═══════════════════════ Output ═══════════════════════

    private static void writeResult(File out, String testName,
            SkeletonStats testStats, Map<String, SkeletonStats> tplStats,
            Map<String, double[]> passed) {
        StringBuilder sb = new StringBuilder();
        sb.append("Test: ").append(testName).append("\n");
        sb.append("  openLines=").append(testStats.openLines)
          .append(" (outer=").append(testStats.outerOpenLines)
          .append(" inner=").append(testStats.innerOpenLines).append(")")
          .append("  closedLoops=").append(testStats.closedLoops)
          .append("  (nodes=").append(testStats.nodes)
          .append("  edges=").append(testStats.edges).append(")\n");
        sb.append("  open-line shapes:");
        for (int i = 0; i < testStats.openLineData.size(); i++)
            sb.append(" ").append(testStats.openLineData.get(i));
        if (testStats.openLineAngles.length > 0) {
            sb.append("  angles:");
            for (double a : testStats.openLineAngles)
                sb.append(String.format(" %.0f°", Math.toDegrees(a)));
        }
        sb.append("\n\n");

        sb.append("Templates:\n");
        for (var e : tplStats.entrySet()) {
            SkeletonStats s = e.getValue();
            sb.append(String.format("  %-22s  openLines=%d(outer=%d inner=%d)  closedLoops=%d  (nodes=%d  edges=%d)",
                    e.getKey(), s.openLines, s.outerOpenLines, s.innerOpenLines,
                    s.closedLoops, s.nodes, s.edges));
            if (!s.openLineData.isEmpty()) {
                sb.append("\n    shapes:");
                for (int i = 0; i < s.openLineData().size(); i++)
                    sb.append(" ").append(s.openLineData().get(i));
                sb.append("\n    angles:");
                for (double a : s.openLineAngles())
                    sb.append(String.format(" %.0f°", Math.toDegrees(a)));
            }
            sb.append("\n");
        }

        sb.append("\nPassed hard layers + soft confidence:\n");
        if (passed.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            sb.append(String.format("  %-22s  %8s  %8s  %8s%n",
                    "name", "shape", "angle", "combined"));
            var sorted = new ArrayList<>(passed.entrySet());
            sorted.sort((a, b) -> Double.compare(b.getValue()[2], a.getValue()[2]));
            for (var e : sorted) {
                double[] scores = e.getValue();
                sb.append(String.format("  %-22s  %8.4f  %8.4f  %8.4f%n",
                        e.getKey(), scores[0], scores[1], scores[2]));
            }
        }

        try {
            FileWriter fw = new FileWriter(out);
            fw.write(sb.toString());
            fw.close();
        } catch (IOException ignored) {}
    }

    // ═══════════════════════ Data classes ═══════════════════════

    /** Feature vector extracted from a skeleton graph. */
    record SkeletonStats(int nodes, int edges, int openLines, int closedLoops,
                         int outerOpenLines, int innerOpenLines,
                         List<OpenLineData> openLineData,
                         double[] openLineAngles) {}

    // ═══════════════════════ Helpers ═══════════════════════

    private static int[][] loadPng(File f) {
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) return null;
            int w = img.getWidth(), h = img.getHeight();
            int[][] raw = new int[h][w];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    raw[y][x] = ((img.getRGB(x, y) & 0xFFFFFF) < 0x202020) ? 1 : 0;
            return raw;
        } catch (IOException e) { return null; }
    }

    private static String noExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
