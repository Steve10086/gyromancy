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
 * <h3>Matching layers</h3>
 * <ol>
 *   <li><b>Hard — open-line count</b>: number of endpoint nodes (degree-1). Reject if mismatch.</li>
 *   <li><b>Hard — closed-loop count</b>: graph cycles + pure-cycle components. Reject if mismatch.</li>
 *   <li><b>Soft — minimum-graph edit + edge correspondence</b>: merge cycle edges
 *       on the pre-split graph until both sides have identical junction degree signatures,
 *       then Hungarian-match surviving edges and compare post-split sub-edge chains
 *       for unedited matched pairs.</li>
 * </ol>
 *
 * <p>Output: {@code build/skeleton_viz/hard_match/{testName}.txt}
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
        File symbolTestDir = new File("src/test/resources/test_images/symbol");

        int count = 0;
        count += matchDir(symbolTestDir, symbolTplDir, outDir);
        System.out.println("[SymbolMatcher] Done. " + count + " test cases → " + outDir.getAbsolutePath());
    }

    // ═══════════════════════ Core matching ═══════════════════════

    private static int matchDir(File testDir, File tplDir, File outDir) {
        File[] testFiles = testDir.listFiles((d, n) -> n.endsWith(".png"));
        File[] tplFiles  = tplDir.listFiles((d, n) -> n.endsWith(".png"));
        if (testFiles == null || tplFiles == null) return 0;

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

            Map<String, double[]> passed = new LinkedHashMap<>();
            for (var e : tplStats.entrySet()) {
                SkeletonStats tpl = e.getValue();

                // ── Hard layers ──
                if (testStats.openLines != tpl.openLines) continue;
                if (testStats.closedLoops != tpl.closedLoops) continue;
                if (testStats.outerOpenLines != tpl.outerOpenLines) continue;
                if (testStats.innerOpenLines != tpl.innerOpenLines) continue;

                // ── Soft: minimum-graph edit + edge correspondence ──
                double conf = minGraphEditMatch(tpl, testStats);
                if (Double.isNaN(conf)) conf = 1.0;
                passed.put(e.getKey(), new double[]{conf});
            }

            writeResult(new File(outDir, testName + ".txt"),
                    testName, testStats, tplStats, passed);
            count++;
        }
        return count;
    }

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

        // ── Snapshot minimum graph (pre-split) ──
        int[][] preSkel = deepCopySkel(cropped);
        List<SkelNode> preNodes = copyNodes(nodes);
        List<SkelEdge> preEdges = copyEdges(edges);
        int preNodeCount = preNodes.size();
        int preEdgeCount = preEdges.size();

        // ── Split with parentage tracking ──
        int[] parentEdgeMap = splitWithParentage(nodes, edges,
                cropped[0].length, cropped.length, preNodeCount, preEdgeCount);

        int openLines = 0;
        for (SkelNode nd : preNodes) if (nd.isEndpoint()) openLines++;
        int graphCycles = countGraphCycles(preNodes, preEdges);
        int pureCycles  = countPureCycleComponents(preSkel, preNodes);
        int closedLoops = graphCycles + pureCycles;
        int[] openEdgeClasses = classifyOpenEdges(preNodes, preEdges);

        return new SkeletonStats(preNodeCount, preEdgeCount, openLines, closedLoops,
                openEdgeClasses[0], openEdgeClasses[1],
                preNodes, preEdges, nodes, edges, parentEdgeMap);
    }

    // ═══════════════════════ Split with parentage tracking ═══════════════════════

    /** See {@link GeometryPreprocessUtils#splitEdgesAtSupportPoints}. Returns parent array. */
    private static int[] splitWithParentage(List<SkelNode> nodes, List<SkelEdge> edges,
            int imgW, int imgH, int preNodeCount, int preEdgeCount) {
        int nextId = nodes.size();
        List<SkelNode> newNodes = new ArrayList<>(nodes);
        List<SkelEdge> newEdges = new ArrayList<>();
        List<Integer> parentList = new ArrayList<>();

        int minEndDist = Math.max(imgW, imgH) / 5;
        java.util.HashSet<Long> existingPos = new java.util.HashSet<>();
        for (SkelNode nd : newNodes)
            existingPos.add(((long) nd.x() << 32) | (nd.y() & 0xFFFF_FFFFL));

        for (int oldEi = 0; oldEi < preEdgeCount; oldEi++) {
            SkelEdge e = edges.get(oldEi);
            SkelNode a = newNodes.get(e.from()), b = newNodes.get(e.to());
            List<int[]> path = e.path();
            if (path.size() < 4) { newEdges.add(e); parentList.add(oldEi); continue; }

            double mx = (a.x() + b.x()) / 2.0, my = (a.y() + b.y()) / 2.0;
            double dx = b.x() - a.x(), dy = b.y() - a.y();
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len < 4) { newEdges.add(e); parentList.add(oldEi); continue; }
            double ux = dx / len, uy = dy / len, vx = -uy, vy = ux;

            int bestPosU = -1, bestNegU = -1, bestPosV = -1, bestNegV = -1;
            double maxPosU = -1, maxNegU = -1, maxPosV = -1, maxNegV = -1;
            for (int i = 0; i < path.size(); i++) {
                int[] p = path.get(i);
                double wx = p[0] - mx, wy = p[1] - my;
                double u = wx * ux + wy * uy;
                double v = wx * vx + wy * vy;
                if (u > 0 && u > maxPosU) { maxPosU = u; bestPosU = i; }
                if (u < 0 && -u > maxNegU) { maxNegU = -u; bestNegU = i; }
                if (v > 0 && v > maxPosV) { maxPosV = v; bestPosV = i; }
                if (v < 0 && -v > maxNegV) { maxNegV = -v; bestNegV = i; }
            }

            java.util.BitSet splitIdxs = new java.util.BitSet(path.size());
            if (bestPosU >= 0 && bestPosU > 2 && bestPosU < path.size() - 3) splitIdxs.set(bestPosU);
            if (bestNegU >= 0 && bestNegU > 2 && bestNegU < path.size() - 3) splitIdxs.set(bestNegU);
            if (bestPosV >= 0 && bestPosV > 1 && bestPosV < path.size() - 2) splitIdxs.set(bestPosV);
            if (bestNegV >= 0 && bestNegV > 1 && bestNegV < path.size() - 2) splitIdxs.set(bestNegV);

            if (splitIdxs.isEmpty()) { newEdges.add(e); parentList.add(oldEi); continue; }

            int prevId = e.from();
            List<int[]> segment = new ArrayList<>();
            for (int i = 0; i < path.size(); i++) {
                segment.add(path.get(i));
                if (splitIdxs.get(i)) {
                    int[] sp = path.get(i);
                    double dxA = a.x() - sp[0], dyA = a.y() - sp[1];
                    double dxB = b.x() - sp[0], dyB = b.y() - sp[1];
                    double lenA = Math.sqrt(dxA*dxA + dyA*dyA);
                    double lenB = Math.sqrt(dxB*dxB + dyB*dyB);
                    boolean sharp = true;
                    if (lenA > 1e-6 && lenB > 1e-6) {
                        double cos = (dxA*dxB + dyA*dyB) / (lenA * lenB);
                        cos = Math.max(-1, Math.min(1, cos));
                        sharp = Math.toDegrees(Math.acos(cos)) < 160 || Math.toDegrees(Math.acos(cos)) > 200;
                    }
                    boolean farEnough = true;
                    for (SkelNode nd : newNodes) {
                        int ndx = sp[0] - nd.x(), ndy = sp[1] - nd.y();
                        if (ndx*ndx + ndy*ndy < minEndDist * minEndDist) { farEnough = false; break; }
                    }
                    long posKey = ((long) sp[0] << 32) | (sp[1] & 0xFFFF_FFFFL);
                    boolean novel = existingPos.add(posKey);
                    if (sharp && farEnough && novel) {
                        int splitId = nextId++;
                        newNodes.add(new SkelNode(splitId, sp[0], sp[1], 2, false));
                        newEdges.add(new SkelEdge(prevId, splitId, segment));
                        parentList.add(oldEi);
                        prevId = splitId;
                        segment = new ArrayList<>();
                    }
                }
            }
            if (!segment.isEmpty()) {
                newEdges.add(new SkelEdge(prevId, e.to(), segment));
                parentList.add(oldEi);
            }
        }

        nodes.clear(); nodes.addAll(newNodes);
        edges.clear(); edges.addAll(newEdges);
        return parentList.stream().mapToInt(Integer::intValue).toArray();
    }

    // ═══════════════════════ Minimum-graph edit + edge correspondence ═══════════════════════

    record MutableNode(int id, double x, double y, int degree, boolean isEndpoint) {}
    record MutableEdge(int id, int from, int to, double length, double angle) {}

    /**
     * Minimum-graph-edit matching between two pre-split graphs.
     * Merges cycle edges until junction degree signatures match, then
     * Hungarian-matches surviving edges and compares post-split shapes.
     */
    static double minGraphEditMatch(SkeletonStats tpl, SkeletonStats target) {
        // Build mutable copies of pre-split graphs
        List<MutableNode> tplN = toMutable(tpl.preNodes);
        List<MutableNode> tgtN = toMutable(target.preNodes);
        List<MutableEdge> tplE = toMutableEdges(tpl.preEdges, tpl.preNodes);
        List<MutableEdge> tgtE = toMutableEdges(target.preEdges, target.preNodes);

        // Track which original pre-split edges survive unedited
        Set<Integer> tplEdited = new HashSet<>();
        Set<Integer> tgtEdited = new HashSet<>();

        // ── Edit loop: merge cycle edges until junction degree signatures match ──
        int maxSteps = 20;
        for (int step = 0; step < maxSteps; step++) {
            int[] tplSig = junctionDegSig(tplN);
            int[] tgtSig = junctionDegSig(tgtN);

            if (graphStructureEquals(tplN, tplE, tplEdited, tgtN, tgtE, tgtEdited)) break;

            if (tplSig.length > tgtSig.length) {
                if (!mergeCheapestCycleEdge(tplN, tplE, tplEdited)) break;
            } else if (tgtSig.length > tplSig.length) {
                if (!mergeCheapestCycleEdge(tgtN, tgtE, tgtEdited)) break;
            } else {
                // Same count but different degrees — merge on whichever has
                // the more "deviant" junction
                if (!mergeCheapestCycleEdge(tplN, tplE, tplEdited)) break;
            }
        }

        // ── Hungarian match surviving edges ──
        // Build lists of unedited edges from each side
        List<Integer> tplLive = new ArrayList<>();
        List<Integer> tgtLive = new ArrayList<>();
        for (int i = 0; i < tplE.size(); i++) if (!tplEdited.contains(i)) tplLive.add(i);
        for (int i = 0; i < tgtE.size(); i++) if (!tgtEdited.contains(i)) tgtLive.add(i);

        int nT = tplLive.size(), nG = tgtLive.size();
        if (nT == 0 || nG == 0) return 1.0;     // no edges to compare

        // Rectangular Hungarian: pad to square with high cost
        int N = Math.max(nT, nG);
        double[][] cost = new double[N][N];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                if (i >= nT || j >= nG) {
                    cost[i][j] = 10;             // dummy
                } else {
                    int tplOrigIdx = tplLive.get(i);
                    int tgtOrigIdx = tgtLive.get(j);
                    cost[i][j] = 1.0 - compareEdgeChains(tpl, target, tplOrigIdx, tgtOrigIdx);
                }
            }
        }
        int[] assignment = hungarianAssignment(cost);

        // ── Compute similarity on matched unedited pairs ──
        double totalSim = 0;
        int validPairs = 0;
        for (int i = 0; i < N; i++) {
            int j = assignment[i];
            if (i >= nT || j >= nG) continue;    // dummy match
            int tplOrigIdx = tplLive.get(i);
            int tgtOrigIdx = tgtLive.get(j);

            double sim = compareEdgeChains(tpl, target, tplOrigIdx, tgtOrigIdx);
            totalSim += sim;
            validPairs++;
        }

        return validPairs > 0 ? Math.max(0.0, totalSim / validPairs) : 1.0;
    }

    // ── Graph edit operations ──

    /** Multiset of junction degrees, sorted. */
    private static int[] junctionDegSig(List<MutableNode> nodes) {
        List<Integer> degs = new ArrayList<>();
        for (MutableNode n : nodes)
            if (n.degree >= 3) degs.add(n.degree);
        int[] arr = degs.stream().mapToInt(Integer::intValue).sorted().toArray();
        return arr;
    }

    /**
     * Finds and merges the cheapest cycle edge.
     * Cost = how "straight" (1 - sharpest turn angle at merging endpoint)
     * making cheaper edges more likely to be the artificial split.
     */
    private static boolean mergeCheapestCycleEdge(List<MutableNode> nodes,
            List<MutableEdge> edges, Set<Integer> editedSet) {
        // Find cycle edge (both ends degree ≥ 3) with minimum cost
        int bestEi = -1;
        double bestCost = Double.MAX_VALUE;
        for (int ei = 0; ei < edges.size(); ei++) {
            if (editedSet.contains(ei)) continue;
            MutableEdge e = edges.get(ei);
            MutableNode a = nodes.get(e.from), b = nodes.get(e.to);
            if (a.degree < 3 || b.degree < 3) continue;     // not a cycle edge

            // Cost = 1 - cos(incident_angle_closest_to_π).
            // Find the pair of incident edges at A (other than e) whose angle
            // with e is closest to π (straight line continuation).
            // Find the pair of incident edges at B similarly.
            // Sum both costs.
            double costA = junctionMergeCost(nodes, edges, ei, e.from, editedSet);
            double costB = junctionMergeCost(nodes, edges, ei, e.to, editedSet);
            double cost = costA + costB;
            if (cost < bestCost) { bestCost = cost; bestEi = ei; }
        }
        if (bestEi < 0) return false;

        // Execute merge
        MutableEdge doomed = edges.get(bestEi);
        MutableNode ja = nodes.get(doomed.from), jb = nodes.get(doomed.to);

        // New merged junction: midpoint, degree = deg(A) + deg(B) - 2
        double mx = (ja.x + jb.x) / 2.0, my = (ja.y + jb.y) / 2.0;
        MutableNode merged = new MutableNode(nodes.size(), mx, my,
                ja.degree + jb.degree - 2, false);
        int mergedId = nodes.size();
        nodes.add(merged);

        // Mark doomed edge as edited
        editedSet.add(bestEi);

        // Rewire: all edges incident to ja or jb (except doomed) now point to merged
        for (int ei = 0; ei < edges.size(); ei++) {
            if (editedSet.contains(ei)) continue;
            MutableEdge e = edges.get(ei);
            if (e.from == ja.id || e.from == jb.id) e = new MutableEdge(e.id, mergedId, e.to, e.length, e.angle);
            if (e.to == ja.id || e.to == jb.id)   e = new MutableEdge(e.id, e.from, mergedId, e.length, e.angle);
            // Recompute angle from updated endpoints
            MutableNode fn = (e.from == mergedId) ? merged : nodes.get(e.from);
            MutableNode tn = (e.to == mergedId) ? merged : nodes.get(e.to);
            double dx = tn.x - fn.x, dy = tn.y - fn.y;
            double len = Math.sqrt(dx*dx + dy*dy);
            double ang = Math.atan2(dy, dx);
            if (ang < 0) ang += Math.PI;
            if (ang >= Math.PI) ang -= Math.PI;
            edges.set(ei, new MutableEdge(e.id, e.from, e.to, len, ang));
        }
        return true;
    }

    /** Cost of merging at one junction end: how far is the best continuation from π. */
    private static double junctionMergeCost(List<MutableNode> nodes, List<MutableEdge> edges,
            int mergeEdge, int nodeId, Set<Integer> editedSet) {
        MutableEdge me = edges.get(mergeEdge);
        // Find incident edges at nodeId other than mergeEdge
        List<Integer> incident = new ArrayList<>();
        for (int ei = 0; ei < edges.size(); ei++) {
            if (editedSet.contains(ei) || ei == mergeEdge) continue;
            MutableEdge e = edges.get(ei);
            if (e.from == nodeId || e.to == nodeId) incident.add(ei);
        }
        if (incident.isEmpty()) return 0;

        MutableNode nd = nodes.get(nodeId);
        double meAngle = me.angle;
        // Normalize merge edge direction relative to node
        // me goes from a → b. At node A, the arm direction comes from A outward (angle + π).
        boolean isFrom = (me.from == nodeId);
        double armDir = isFrom ? meAngle : meAngle + Math.PI;
        if (armDir >= Math.PI) armDir -= Math.PI;
        if (armDir < 0) armDir += Math.PI;

        double bestCos = -1;
        for (int ei : incident) {
            MutableEdge ie = edges.get(ei);
            boolean ieFrom = (ie.from == nodeId);
            double ieDir = ieFrom ? ie.angle : ie.angle + Math.PI;
            if (ieDir >= Math.PI) ieDir -= Math.PI;
            if (ieDir < 0) ieDir += Math.PI;
            double ad = Math.abs(armDir - ieDir);
            if (ad > Math.PI/2) ad = Math.PI - ad;
            double cos = Math.cos(ad);
            if (cos > bestCos) bestCos = cos;
        }
        return bestCos < -0.9 ? 10 : 1.0 - bestCos;
    }

    private static double edgeAngleDist(double a, double b) {
        double d = Math.abs(a - b);
        if (d > Math.PI / 2) d = Math.PI - d;
        return d / (Math.PI / 2);
    }

    private static boolean graphStructureEquals(List<MutableNode> aNodes, List<MutableEdge> aEdges,
            Set<Integer> aEdited, List<MutableNode> bNodes, List<MutableEdge> bEdges,
            Set<Integer> bEdited) {
        LiveGraph a = buildLiveGraph(aNodes, aEdges, aEdited);
        LiveGraph b = buildLiveGraph(bNodes, bEdges, bEdited);
        if (a.degrees.length != b.degrees.length || a.edgeCount != b.edgeCount) return false;

        int[] aDeg = a.degrees.clone();
        int[] bDeg = b.degrees.clone();
        Arrays.sort(aDeg);
        Arrays.sort(bDeg);
        if (!Arrays.equals(aDeg, bDeg)) return false;

        int n = a.degrees.length;
        int[] order = nodeMatchOrder(a);
        int[] mapAtoB = new int[n];
        int[] mapBtoA = new int[n];
        Arrays.fill(mapAtoB, -1);
        Arrays.fill(mapBtoA, -1);
        return isomorphicBacktrack(a, b, order, 0, mapAtoB, mapBtoA);
    }

    record LiveGraph(int[] degrees, boolean[][] adj, int edgeCount) {}

    private static LiveGraph buildLiveGraph(List<MutableNode> nodes, List<MutableEdge> edges,
            Set<Integer> edited) {
        Map<Integer, Integer> liveNodeIndex = new HashMap<>();
        List<MutableNode> liveNodes = new ArrayList<>();
        for (MutableNode n : nodes) {
            boolean used = false;
            for (int ei = 0; ei < edges.size(); ei++) {
                if (edited.contains(ei)) continue;
                MutableEdge e = edges.get(ei);
                if (e.from == n.id || e.to == n.id) { used = true; break; }
            }
            if (used) {
                liveNodeIndex.put(n.id, liveNodes.size());
                liveNodes.add(n);
            }
        }

        boolean[][] adj = new boolean[liveNodes.size()][liveNodes.size()];
        int edgeCount = 0;
        for (int ei = 0; ei < edges.size(); ei++) {
            if (edited.contains(ei)) continue;
            MutableEdge e = edges.get(ei);
            Integer a = liveNodeIndex.get(e.from);
            Integer b = liveNodeIndex.get(e.to);
            if (a == null || b == null || a.equals(b)) continue;
            if (!adj[a][b]) {
                adj[a][b] = true;
                adj[b][a] = true;
                edgeCount++;
            }
        }

        int[] degrees = new int[liveNodes.size()];
        for (int i = 0; i < liveNodes.size(); i++) degrees[i] = liveNodes.get(i).degree;
        return new LiveGraph(degrees, adj, edgeCount);
    }

    private static int[] nodeMatchOrder(LiveGraph g) {
        Integer[] order = new Integer[g.degrees.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> {
            int degreeCmp = Integer.compare(g.degrees[b], g.degrees[a]);
            if (degreeCmp != 0) return degreeCmp;
            return Integer.compare(neighborDegreeSum(g, b), neighborDegreeSum(g, a));
        });
        return Arrays.stream(order).mapToInt(Integer::intValue).toArray();
    }

    private static int neighborDegreeSum(LiveGraph g, int node) {
        int sum = 0;
        for (int i = 0; i < g.degrees.length; i++) if (g.adj[node][i]) sum += g.degrees[i];
        return sum;
    }

    private static boolean isomorphicBacktrack(LiveGraph a, LiveGraph b, int[] order,
            int depth, int[] mapAtoB, int[] mapBtoA) {
        if (depth == order.length) return true;

        int ai = order[depth];
        for (int bi = 0; bi < b.degrees.length; bi++) {
            if (mapBtoA[bi] >= 0) continue;
            if (a.degrees[ai] != b.degrees[bi]) continue;
            if (neighborDegreeSum(a, ai) != neighborDegreeSum(b, bi)) continue;
            if (!isConsistentMapping(a, b, ai, bi, mapAtoB)) continue;

            mapAtoB[ai] = bi;
            mapBtoA[bi] = ai;
            if (isomorphicBacktrack(a, b, order, depth + 1, mapAtoB, mapBtoA)) return true;
            mapAtoB[ai] = -1;
            mapBtoA[bi] = -1;
        }
        return false;
    }

    private static boolean isConsistentMapping(LiveGraph a, LiveGraph b,
            int ai, int bi, int[] mapAtoB) {
        for (int aj = 0; aj < a.degrees.length; aj++) {
            int bj = mapAtoB[aj];
            if (bj < 0) continue;
            if (a.adj[ai][aj] != b.adj[bi][bj]) return false;
        }
        return true;
    }

    // ── Build mutable graph ──

    private static List<MutableNode> toMutable(List<SkelNode> src) {
        List<MutableNode> out = new ArrayList<>(src.size());
        for (SkelNode n : src)
            out.add(new MutableNode(n.id(), n.x(), n.y(), n.degree(), n.isEndpoint()));
        return out;
    }

    private static List<MutableEdge> toMutableEdges(List<SkelEdge> edges, List<SkelNode> nodes) {
        List<MutableEdge> out = new ArrayList<>(edges.size());
        for (int ei = 0; ei < edges.size(); ei++) {
            SkelEdge e = edges.get(ei);
            SkelNode a = nodes.get(e.from()), b = nodes.get(e.to());
            double dx = b.x() - a.x(), dy = b.y() - a.y();
            double len = Math.sqrt(dx * dx + dy * dy);
            double ang = Math.atan2(dy, dx);
            if (ang < 0) ang += Math.PI;
            if (ang >= Math.PI) ang -= Math.PI;
            out.add(new MutableEdge(ei, e.from(), e.to(), len, ang));
        }
        return out;
    }

    // ═══════════════════════ Sub-edge chain shape comparison ═══════════════════════

    /**
     * Compares the post-split shapes of two pre-split edges.
     * Features: number of sub-edges (support-point segments), total pixel
     * path length ratio, turning-angle profile between sub-edges.
     */
    private static double compareEdgeChains(SkeletonStats tpl, SkeletonStats target,
            int tplPreIdx, int tgtPreIdx) {
        List<Integer> tplSubs = findSubEdges(tpl.parentEdgeMap, tplPreIdx);
        List<Integer> tgtSubs = findSubEdges(target.parentEdgeMap, tgtPreIdx);

        int sT = tplSubs.size(), sG = tgtSubs.size();

        // Segment count similarity
        double segSim = (sT > 0 && sG > 0)
                ? (double) Math.min(sT, sG) / Math.max(sT, sG)
                : (sT == 0 && sG == 0 ? 1.0 : 0.5);

        // Total pixel path length ratio
        double tplLen = totalChainLength(tpl.splitEdges, tplSubs);
        double tgtLen = totalChainLength(target.splitEdges, tgtSubs);
        double lenSim = (tplLen > 0 && tgtLen > 0)
                ? Math.min(tplLen, tgtLen) / Math.max(tplLen, tgtLen)
                : 1.0;

        // Turning-angle profile similarity (support-point angles)
        double turnSim = compareTurningProfiles(tpl.splitEdges, target.splitEdges, tplSubs, tgtSubs);

        return 0.30 * segSim + 0.30 * lenSim + 0.40 * turnSim;
    }

    private static List<Integer> findSubEdges(int[] parentMap, int parentIdx) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < parentMap.length; i++)
            if (parentMap[i] == parentIdx) result.add(i);
        return result;
    }

    private static double totalChainLength(List<SkelEdge> edges, List<Integer> subIndices) {
        double total = 0;
        for (int si : subIndices) total += edges.get(si).path().size();
        return total;
    }

    private static double compareTurningProfiles(List<SkelEdge> tplEdges, List<SkelEdge> tgtEdges,
            List<Integer> tplSubs, List<Integer> tgtSubs) {
        List<Double> tplTurns = new ArrayList<>(), tgtTurns = new ArrayList<>();
        collectTurningAngles(tplEdges, tplSubs, tplTurns);
        collectTurningAngles(tgtEdges, tgtSubs, tgtTurns);

        int nt = tplTurns.size(), ng = tgtTurns.size();
        if (nt == 0 && ng == 0) return 1.0;
        if (nt == 0 || ng == 0) return 0.5;

        double[] shorter = nt <= ng ? toArray(tplTurns) : toArray(tgtTurns);
        double[] longer  = nt <= ng ? toArray(tgtTurns) : toArray(tplTurns);
        int ns = shorter.length, nl = longer.length;

        double best = Double.MAX_VALUE;
        for (int s = 0; s <= nl - ns; s++) {
            double sum = 0;
            for (int i = 0; i < ns; i++)
                sum += Math.abs(shorter[i] - longer[s + i]) / 180.0;
            best = Math.min(best, sum / ns);
        }
        return Math.max(0.0, 1.0 - best);
    }

    private static void collectTurningAngles(List<SkelEdge> edges, List<Integer> subIndices,
            List<Double> out) {
        if (subIndices.size() < 2) return;
        for (int k = 1; k < subIndices.size(); k++) {
            SkelEdge prev = edges.get(subIndices.get(k - 1));
            SkelEdge next = edges.get(subIndices.get(k));
            List<int[]> pp = prev.path(), np = next.path();

            int[] d2pos = null;
            if (prev.to() == next.from()) d2pos = pp.get(pp.size() - 1);
            else if (prev.from() == next.to()) d2pos = pp.get(0);
            else if (prev.to() == next.to()) d2pos = pp.get(pp.size() - 1);
            else if (prev.from() == next.from()) d2pos = pp.get(0);
            if (d2pos == null) continue;

            int[] p0 = (prev.to() == next.from() || prev.to() == next.to())
                    ? pp.get(Math.max(0, pp.size() - 2)) : pp.get(0);
            double inDx = d2pos[0] - p0[0], inDy = d2pos[1] - p0[1];

            int[] n1 = (next.from() == prev.to() || next.from() == prev.from())
                    ? np.get(Math.min(1, np.size() - 1)) : np.get(np.size() - 1);
            double outDx = n1[0] - d2pos[0], outDy = n1[1] - d2pos[1];

            double inLen = Math.sqrt(inDx*inDx + inDy*inDy);
            double outLen = Math.sqrt(outDx*outDx + outDy*outDy);
            if (inLen < 1e-6 || outLen < 1e-6) continue;

            double cos = (inDx*outDx + inDy*outDy) / (inLen * outLen);
            cos = Math.max(-1, Math.min(1, cos));
            out.add(Math.toDegrees(Math.acos(cos)));
        }
    }

    private static double[] toArray(List<Double> list) {
        return list.stream().mapToDouble(Double::doubleValue).toArray();
    }

    // ═══════════════════════ Graph utilities ═══════════════════════

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
            stack.push(i); visited[i] = true;
            while (!stack.isEmpty()) {
                for (int nb : adj.get(stack.pop()))
                    if (!visited[nb]) { visited[nb] = true; stack.push(nb); }
            }
        }
        return Math.max(0, edges.size() - n + components);
    }

    private static int countPureCycleComponents(int[][] skel, List<SkelNode> nodes) {
        int h = skel.length, w = skel[0].length;
        boolean[][] isNode = new boolean[h][w];
        for (SkelNode nd : nodes) isNode[nd.y()][nd.x()] = true;
        boolean[][] visited = new boolean[h][w];
        int pureCycles = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (skel[y][x] == 0 || visited[y][x]) continue;
            Deque<int[]> q = new ArrayDeque<>();
            q.add(new int[]{x, y}); visited[y][x] = true;
            boolean hasNode = isNode[y][x];
            while (!q.isEmpty()) {
                int[] p = q.poll(); int cx = p[0], cy = p[1];
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = cx + dx, ny = cy + dy;
                    if (ny < 0 || ny >= h || nx < 0 || nx >= w) continue;
                    if (skel[ny][nx] == 0 || visited[ny][nx]) continue;
                    visited[ny][nx] = true;
                    if (isNode[ny][nx]) hasNode = true;
                    q.add(new int[]{nx, ny});
                }
            }
            if (!hasNode) pureCycles++;
        }
        return pureCycles;
    }

    private static int[] classifyOpenEdges(List<SkelNode> nodes, List<SkelEdge> edges) {
        double[] center = cycleCenter(nodes, edges);
        int outer = 0, inner = 0;
        for (SkelEdge e : edges) {
            SkelNode a = nodes.get(e.from());
            SkelNode b = nodes.get(e.to());
            if (a.isEndpoint() == b.isEndpoint()) continue;

            SkelNode endpoint = a.isEndpoint() ? a : b;
            SkelNode anchor = a.isEndpoint() ? b : a;
            if (center == null) {
                outer++;
                continue;
            }

            double endDist = squaredDistance(endpoint.x(), endpoint.y(), center[0], center[1]);
            double anchorDist = squaredDistance(anchor.x(), anchor.y(), center[0], center[1]);
            if (endDist >= anchorDist) outer++;
            else inner++;
        }
        return new int[]{outer, inner};
    }

    private static double[] cycleCenter(List<SkelNode> nodes, List<SkelEdge> edges) {
        double sx = 0, sy = 0;
        int count = 0;
        for (SkelEdge e : edges) {
            SkelNode a = nodes.get(e.from());
            SkelNode b = nodes.get(e.to());
            if (a.isEndpoint() || b.isEndpoint()) continue;
            sx += a.x() + b.x();
            sy += a.y() + b.y();
            count += 2;
        }
        if (count == 0) return null;
        return new double[]{sx / count, sy / count};
    }

    private static double squaredDistance(double ax, double ay, double bx, double by) {
        double dx = ax - bx, dy = ay - by;
        return dx * dx + dy * dy;
    }

    // ═══════════════════════ Hungarian assignment ═══════════════════════

    static int[] hungarianAssignment(double[][] cost) {
        int n = cost.length;
        if (n == 0) return new int[0];
        double[] u = new double[n + 1], v = new double[n + 1];
        int[] p = new int[n + 1], way = new int[n + 1];
        for (int i = 1; i <= n; i++) {
            p[0] = i;
            int j0 = 0;
            double[] minv = new double[n + 1];
            Arrays.fill(minv, Double.MAX_VALUE);
            boolean[] used = new boolean[n + 1];
            do {
                used[j0] = true;
                int i0 = p[j0], j1 = 0;
                double delta = Double.MAX_VALUE;
                for (int j = 1; j <= n; j++) {
                    if (!used[j]) {
                        double cur = cost[i0 - 1][j - 1] - u[i0] - v[j];
                        if (cur < minv[j]) { minv[j] = cur; way[j] = j0; }
                        if (minv[j] < delta) { delta = minv[j]; j1 = j; }
                    }
                }
                for (int j = 0; j <= n; j++) {
                    if (used[j]) { u[p[j]] += delta; v[j] -= delta; }
                    else minv[j] -= delta;
                }
                j0 = j1;
            } while (p[j0] != 0);
            do { int j1 = way[j0]; p[j0] = p[j1]; j0 = j1; } while (j0 != 0);
        }
        int[] result = new int[n];
        for (int j = 1; j <= n; j++)
            if (p[j] != 0) result[p[j] - 1] = j - 1;
        return result;
    }

    // ═══════════════════════ Node/edge copy ═══════════════════════

    private static List<SkelNode> copyNodes(List<SkelNode> src) {
        List<SkelNode> dst = new ArrayList<>(src.size());
        for (SkelNode n : src)
            dst.add(new SkelNode(n.id(), n.x(), n.y(), n.degree(), n.isEndpoint()));
        return dst;
    }

    private static List<SkelEdge> copyEdges(List<SkelEdge> src) {
        List<SkelEdge> dst = new ArrayList<>(src.size());
        for (SkelEdge e : src) {
            List<int[]> pathCopy = new ArrayList<>(e.path().size());
            for (int[] p : e.path()) pathCopy.add(new int[]{p[0], p[1]});
            dst.add(new SkelEdge(e.from(), e.to(), pathCopy));
        }
        return dst;
    }

    private static int[][] deepCopySkel(int[][] src) {
        int h = src.length, w = src[0].length;
        int[][] dst = new int[h][w];
        for (int y = 0; y < h; y++) System.arraycopy(src[y], 0, dst[y], 0, w);
        return dst;
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

        sb.append("  Pre-split junction degrees:");
        for (SkelNode n : testStats.preNodes)
            if (n.degree() >= 3) sb.append(" ").append(n.degree());
        sb.append("\n  Pre-split edges:");
        for (SkelEdge e : testStats.preEdges) {
            int d1 = testStats.preNodes.get(e.from()).degree();
            int d2 = testStats.preNodes.get(e.to()).degree();
            sb.append(String.format(" [%s%s]", d1 == 1 ? "E" : "J", d2 == 1 ? "E" : "J"));
        }
        sb.append("\n\n");

        sb.append("Templates:\n");
        for (var e : tplStats.entrySet()) {
            SkeletonStats s = e.getValue();
            sb.append(String.format("  %-22s  openLines=%d(outer=%d inner=%d)  closedLoops=%d  (nodes=%d  edges=%d)",
                    e.getKey(), s.openLines, s.outerOpenLines, s.innerOpenLines,
                    s.closedLoops, s.nodes, s.edges));
            sb.append("\n    junction degs:");
            for (SkelNode n : s.preNodes)
                if (n.degree() >= 3) sb.append(" ").append(n.degree());
            sb.append("\n");
        }

        sb.append("\nPassed hard layers + edge-edit confidence:\n");
        if (passed.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            sb.append(String.format("  %-22s  %8s%n", "name", "editConf"));
            var sorted = new ArrayList<>(passed.entrySet());
            sorted.sort((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]));
            for (var e2 : sorted)
                sb.append(String.format("  %-22s  %8.4f%n", e2.getKey(), e2.getValue()[0]));
        }

        try {
            FileWriter fw = new FileWriter(out);
            fw.write(sb.toString());
            fw.close();
        } catch (IOException ignored) {}
    }

    // ═══════════════════════ Data classes ═══════════════════════

    record SkeletonStats(int nodes, int edges, int openLines, int closedLoops,
                         int outerOpenLines, int innerOpenLines,
                         List<SkelNode> preNodes, List<SkelEdge> preEdges,
                         List<SkelNode> splitNodes, List<SkelEdge> splitEdges,
                         int[] parentEdgeMap) {}

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
