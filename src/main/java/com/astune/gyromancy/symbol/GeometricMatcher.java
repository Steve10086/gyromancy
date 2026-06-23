package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.SymbolTemplate;
import com.astune.gyromancy.util.GeometryUtils;
import com.astune.gyromancy.util.GeometryUtils.SkeletonGraph;
import com.astune.gyromancy.util.GeometryUtils.GraphNode;
import com.astune.gyromancy.util.GeometryUtils.NodeType;

import java.util.List;

/**
 * Pure 2D topological shape matching via skeleton graph geometry.
 *
 * <p>Three-phase pipeline:
 * <ol>
 *   <li><b>Hard cycle gate</b> — enclosed region count must match exactly.</li>
 *   <li><b>Minimum skeleton length penalty</b> — rejects noise traces.</li>
 *   <li><b>Graph geometry matching</b> — multi-feature comparison using only
 *       2D skeleton data: node pairwise distances, edge length/curvature
 *       distributions, node type counts. No 1D contour descriptors.</li>
 * </ol>
 *
 * <p>Shape Context histogram approach was tried and rejected: at 32×32
 * resolution the 60-bin log-polar histograms have insufficient discriminative
 * power — noise scored 0.94 on wind. The raw sorted-distance approach below
 * preserves more information at the available resolution.
 */
public final class GeometricMatcher {

    // Hungarian dummy cost — penalizes unmatched nodes
    private static final double DUMMY_COST = 0.6;

    private GeometricMatcher() {}

    public record MatchResult(
            float confidence,
            float rotationDegrees,
            boolean mirrored,
            float scale,
            float tfScore
    ) {
        public static final MatchResult NONE = new MatchResult(0f, 0f, false, 1f, 0f);
    }

    public static MatchResult match(int[][] drawn, SymbolTemplate template) {
        int[][] normDrawn = GeometryUtils.normalize(drawn, 32, 32);

        // Detect cycles on normalized (pre-thinned) — consistent with SymbolTemplate
        int drawnCycles = GeometryUtils.detectTrueCycles(normDrawn);
        int drawnMaxArea = GeometryUtils.maxEnclosedArea(normDrawn);

        // Upscale to ≥128×128 with 8-connectivity, then anti-alias
        int[][] hiresDrawn = GeometryUtils.upscaleConnectivityPreserving(normDrawn, 128);
        int k = (128 + Math.min(normDrawn.length, normDrawn[0].length) - 1)
                / Math.min(normDrawn.length, normDrawn[0].length);
        int[][] smoothDrawn = GeometryUtils.gaussianSmoothBinary(hiresDrawn, k / 3.0);
        int[][] normSkel = GeometryUtils.thin(smoothDrawn);
        int[][] normPruned = GeometryUtils.pruneSkeleton(normSkel, 0.04);
        SkeletonGraph drawnGraph = GeometryUtils.buildSkeletonGraph(normPruned);

        SkeletonGraph tplGraph = template.skeletonGraph();
        int tplCycles = template.trueCycleCount();
        int tplMaxArea = GeometryUtils.maxEnclosedArea(template.pattern());

        float confidence = geometricConfidence(
                drawnGraph, drawnCycles, drawnMaxArea,
                tplGraph, tplCycles, tplMaxArea, tplGraph.totalLength());

        return new MatchResult(confidence, 0f, false, 1f, confidence);
    }

    /**
     * Package-private: used by MLSymbolMatcher for two-stage pipeline.
     * Computes [0,1] geometric confidence that a drawn skeleton graph
     * matches a template skeleton graph, without needing SymbolTemplate.
     *
     * <p>Three phases: hard cycle gate → skeleton length penalty → graph geometry score.
     */
    static float geometricConfidence(
            SkeletonGraph drawnGraph, int drawnCycles, int drawnMaxArea,
            SkeletonGraph tplGraph, int tplCycles, int tplMaxArea, int tplTotalLen) {

        // ═══ Phase 1: Hard cycle gate ═══
        if (drawnCycles != tplCycles) return 0f;
        if (drawnCycles == 0 && tplCycles > 0) return 0f;

        // Cycle quality: noise often has tiny accidental holes
        float cycleQuality = 1.0f;
        if (tplCycles > 0 && drawnCycles > 0) {
            if (tplMaxArea > 0 && drawnMaxArea < tplMaxArea / 5) cycleQuality = 0.20f;
            else if (tplMaxArea > 0 && drawnMaxArea < tplMaxArea / 3) cycleQuality = 0.50f;
        }

        // ═══ Phase 2: Min skeleton length penalty ═══
        float minLenPenalty = 1.0f;
        int dLen = drawnGraph.totalLength();
        if (tplTotalLen > 8 && dLen < tplTotalLen / 4) minLenPenalty = 0.05f;
        else if (tplTotalLen > 8 && dLen < tplTotalLen / 3) minLenPenalty = 0.15f;
        else if (tplTotalLen > 8 && dLen < tplTotalLen / 2) minLenPenalty = 0.45f;

        // ═══ Phase 3: Graph geometry score (pure 2D skeleton features) ═══
        float geoScore = computeGraphGeometryScore(drawnGraph, tplGraph);

        // ═══ Phase 4: Structural complexity — noise has jagged graph topology ═══
        float complexity = 1.0f;
        if (drawnCycles == 0) {
            int dEdges = drawnGraph.edgeCount();
            if (dEdges == 0) return 0f;
            // Extreme edge density → thinning artifacts from scribble noise
            // Legitimate 0-cycle symbols max out at ~0.23; noise often exceeds 0.25
            float edgeDensity = (float) dEdges / Math.max(1, dLen);
            if (edgeDensity > 0.30f) complexity = 0.02f;
            else if (edgeDensity > 0.25f) complexity = 0.05f;
            // Very short skeleton with few edges → can't be a meaningful symbol
            if (dLen < 10 && dEdges <= 2) complexity = 0.05f;
        }

        // ═══ Phase 5: Combined score ═══
        return geoScore * cycleQuality * minLenPenalty * complexity;
    }

    // ═══════════════════ Graph Geometry Scoring ═══════════════════

    /**
     * Computes a [0,1] similarity score based purely on skeleton graph
     * geometry features: node pairwise distance distribution, edge length
     * distribution, edge curvature distribution, node type counts.
     *
     * <p>All features are 2D — derived from skeleton pixel geometry,
     * not from 1D contour resampling (no TF, CDF, curv DTW).
     */
    private static float computeGraphGeometryScore(SkeletonGraph drawn, SkeletonGraph tpl) {
        int nD = drawn.nodeCount(), nT = tpl.nodeCount();
        int eD = drawn.edgeCount(), eT = tpl.edgeCount();

        if (nD == 0 && nT == 0) return 1.0f;
        if (eD == 0 && eT == 0) return 0.7f;
        if (eD == 0 || eT == 0) return 0.05f;

        // ── Feature 1: Node pairwise distance signature (Hungarian) ──
        float fDist = computeNodeDistanceScore(drawn, tpl);

        // ── Feature 2: Edge length distribution KS ──
        float fEdgeLen = ksScore(edgeLengths(drawn), edgeLengths(tpl));

        // ── Feature 3: Edge curvature distribution KS ──
        float fEdgeCurv = ksScore(edgeCurvTotals(drawn), edgeCurvTotals(tpl));

        // ── Feature 4: Endpoint count ratio ──
        int epsD = drawn.endpointCount(), epsT = tpl.endpointCount();
        float fEps = (epsD == 0 && epsT == 0) ? 1.0f
                   : (epsD == 0 || epsT == 0) ? 0.10f
                   : softRatio(epsD, epsT);

        // ── Feature 5: Edge count ratio ──
        float fEdgeCnt = softRatio(eD, eT);

        // ── Feature 6: Junction+corner node count ratio ──
        int jncD = (int) drawn.nodes().stream()
                .filter(n -> n.type() == NodeType.JUNCTION || n.type() == NodeType.CORNER).count();
        int jncT = (int) tpl.nodes().stream()
                .filter(n -> n.type() == NodeType.JUNCTION || n.type() == NodeType.CORNER).count();
        float fJnc = (jncD == 0 && jncT == 0) ? 1.0f : softRatio(Math.max(1, jncD), Math.max(1, jncT));

        // ── Feature 7: Total skeleton length ratio ──
        float fTotalLen = softRatio(drawn.totalLength(), tpl.totalLength());

        return 0.24f * fDist + 0.20f * fEdgeLen + 0.16f * fEdgeCurv
             + 0.16f * fEps + 0.12f * fEdgeCnt + 0.06f * fJnc + 0.06f * fTotalLen;
    }

    // ═══════════════════ Node Pairwise Distance Matching ═══════════════════

    /**
     * Compares the sorted pairwise distance vectors of both graphs.
     * For each node, computes its distances to all other nodes, sorts them.
     * Then uses Hungarian algorithm to match nodes across graphs, with
     * cost = element-wise L1 on sorted distance vectors.
     *
     * <p>Rotation/translation/scale invariant: distances between nodes
     * are invariant to translation, pairwise sorted is invariant to
     * permutation, and normalization by max distance gives scale invariance.
     */
    private static float computeNodeDistanceScore(SkeletonGraph drawn, SkeletonGraph tpl) {
        int nD = drawn.nodeCount(), nT = tpl.nodeCount();
        if (nD <= 1 && nT <= 1) return 1.0f;
        if (nD <= 1 || nT <= 1) {
            // Single-node graphs: compare edge lengths and total curvatures instead
            return softRatio(drawn.totalLength(), tpl.totalLength());
        }

        // Compute sorted pairwise distance vectors per node
        double[][] dVecs = computeSortedDistVectors(drawn);
        double[][] tVecs = computeSortedDistVectors(tpl);

        // Build Hungarian cost matrix: L1 distance between sorted vectors
        // Strong penalty for cross-type matches (endpoint vs junction)
        int N = nD, M = nT;
        double[][] cost = new double[N][M];
        for (int i = 0; i < N; i++) {
            var dNode = drawn.nodes().get(i);
            for (int j = 0; j < M; j++) {
                var tNode = tpl.nodes().get(j);
                double typePenalty = (dNode.type() == tNode.type()) ? 0.0 : 0.5;
                cost[i][j] = vectorL1(dVecs[i], tVecs[j]) + typePenalty;
            }
        }

        double[] matchCosts = hungarian(cost);
        double totalCost = 0;
        for (double c : matchCosts) totalCost += c;
        int K = Math.max(N, M);
        double normalized = totalCost / (K * DUMMY_COST);

        return (float) Math.max(0.01, 1.0 - Math.min(1.0, normalized));
    }

    /** Sorted distance vectors for all nodes in a graph. */
    private static double[][] computeSortedDistVectors(SkeletonGraph g) {
        var nodes = g.nodes();
        int n = nodes.size();
        double[][] result = new double[n][];
        // Compute all pairwise distances (full N×N matrix)
        double[][] dists = new double[n][n];
        double maxDist = 1e-6;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double dx = nodes.get(i).x() - nodes.get(j).x();
                double dy = nodes.get(i).y() - nodes.get(j).y();
                dists[i][j] = dists[j][i] = Math.sqrt(dx*dx + dy*dy);
                if (dists[i][j] > maxDist) maxDist = dists[i][j];
            }
        }
        if (maxDist < 1e-6) maxDist = 1.0;

        // For each node: collect distances to all others, sort
        for (int i = 0; i < n; i++) {
            double[] vec = new double[n - 1];
            int idx = 0;
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                vec[idx++] = dists[i][j] / maxDist;
            }
            java.util.Arrays.sort(vec);
            result[i] = vec;
        }
        return result;
    }

    /** L1 distance between two sorted vectors, element-by-element with padding. */
    private static double vectorL1(double[] a, double[] b) {
        int maxLen = Math.max(a.length, b.length);
        double sum = 0;
        for (int i = 0; i < maxLen; i++) {
            double va = (i < a.length) ? a[i] : 0.0;
            double vb = (i < b.length) ? b[i] : 0.0;
            sum += Math.abs(va - vb);
        }
        return sum / maxLen;
    }

    // ═══════════════════ Hungarian Algorithm ═══════════════════

    /** Hungarian (Kuhn-Munkres) for min-cost assignment on N×M cost matrix. */
    private static double[] hungarian(double[][] cost) {
        int N = cost.length;
        int M = N > 0 ? cost[0].length : 0;
        int K = Math.max(N, M);
        if (K == 0) return new double[0];

        double[][] a = new double[K + 1][K + 1];
        for (int i = 1; i <= K; i++)
            for (int j = 1; j <= K; j++)
                a[i][j] = (i <= N && j <= M) ? cost[i - 1][j - 1] : DUMMY_COST;

        double[] u = new double[K + 1];
        double[] v = new double[K + 1];
        int[] p = new int[K + 1];
        int[] way = new int[K + 1];

        for (int i = 1; i <= K; i++) {
            p[0] = i;
            int j0 = 0;
            double[] minv = new double[K + 1];
            boolean[] used = new boolean[K + 1];
            for (int j = 1; j <= K; j++) minv[j] = Double.MAX_VALUE;
            do {
                used[j0] = true;
                int i0 = p[j0];
                double delta = Double.MAX_VALUE;
                int j1 = 0;
                for (int j = 1; j <= K; j++) {
                    if (!used[j]) {
                        double cur = a[i0][j] - u[i0] - v[j];
                        if (cur < minv[j]) { minv[j] = cur; way[j] = j0; }
                        if (minv[j] < delta) { delta = minv[j]; j1 = j; }
                    }
                }
                for (int j = 0; j <= K; j++) {
                    if (used[j]) { u[p[j]] += delta; v[j] -= delta; }
                    else minv[j] -= delta;
                }
                j0 = j1;
            } while (p[j0] != 0);
            do {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            } while (j0 > 0);
        }

        double[] matchCosts = new double[N];
        for (int j = 1; j <= K; j++) {
            int row = p[j];
            if (row >= 1 && row <= N) {
                if (j <= M) matchCosts[row - 1] = cost[row - 1][j - 1];
                else matchCosts[row - 1] = DUMMY_COST;
            }
        }
        return matchCosts;
    }

    // ═══════════════════ Feature Extractors ═══════════════════

    private static double[] edgeLengths(SkeletonGraph g) {
        var es = g.edges(); double[] a = new double[es.size()]; int i = 0;
        for (var e : es) a[i++] = e.pathLength(); return a;
    }
    private static double[] edgeCurvTotals(SkeletonGraph g) {
        var es = g.edges(); double[] a = new double[es.size()]; int i = 0;
        for (var e : es) a[i++] = e.totalCurvature(); return a;
    }

    /** KS distance between two sorted max-normalized arrays. Returns [0,1]. */
    private static float ksScore(double[] a, double[] b) {
        int na = a.length, nb = b.length;
        if (na == 0 && nb == 0) return 1f;
        if (na == 0 || nb == 0) return 0.2f;
        double mxA = 0, mxB = 0;
        for (double v : a) if (v > mxA) mxA = v;
        for (double v : b) if (v > mxB) mxB = v;
        if (mxA < 1e-9) mxA = 1; if (mxB < 1e-9) mxB = 1;
        double[] naA = new double[na], naB = new double[nb];
        for (int i = 0; i < na; i++) naA[i] = a[i] / mxA;
        for (int i = 0; i < nb; i++) naB[i] = b[i] / mxB;
        java.util.Arrays.sort(naA); java.util.Arrays.sort(naB);
        double maxDiff = 0; int p = 0, q = 0;
        while (p < na && q < nb) {
            double diff = Math.abs((double)p/na - (double)q/nb);
            if (diff > maxDiff) maxDiff = diff;
            if (naA[p] <= naB[q]) p++; else q++;
        }
        while (p < na) { double diff = Math.abs((double)p/na - 1.0); if (diff > maxDiff) maxDiff = diff; p++; }
        while (q < nb) { double diff = Math.abs(1.0 - (double)q/nb); if (diff > maxDiff) maxDiff = diff; q++; }
        return (float)(1.0 - maxDiff);
    }

    private static float softRatio(double a, double b) {
        double sum = a + b;
        if (sum < 1e-9) return 1f;
        double r = Math.min(a, b) / Math.max(a, b);
        return (float)(r * r);
    }
}
