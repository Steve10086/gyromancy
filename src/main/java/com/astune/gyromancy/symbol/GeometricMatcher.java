package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.SymbolTemplate;
import com.astune.gyromancy.util.GeometryUtils;
import com.astune.gyromancy.util.GeometryUtils.Corner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Skeleton-graph-primary shape matching for hand-drawn symbol recognition.
 *
 * <p>Three-phase pipeline:
 * <ol>
 *   <li><b>Hard cycle gate</b> — true enclosed region count must match exactly.
 *       Micro-closures (≤2 px hollow area) from thinning artifacts are filtered.</li>
 *   <li><b>Primary graph structural score</b> (55%) — 6-feature KS + ratio matching
 *       on edge length, edge curvature, degree sequence, endpoint/edge counts.</li>
 *   <li><b>Supplementary descriptors</b> — curvDTW (30%) + cdfL2 (15%) for
 *       within-cycle-class shape discrimination.</li>
 * </ol>
 *
 * <p>Key innovation v8: true cycle detection replaces Euler formula;
 * cycle mismatch is a HARD REJECT. This eliminates the two remaining
 * failure modes: noise (0 cycles) vs symbols (≥1), and water (1 cycle)
 * vs fire (2 cycles).
 */
public final class GeometricMatcher {

    private static final double LAMBDA_CURV = 5.0;   // DTW curvature — softer for raw binary
    private static final double LAMBDA_CDF = 3.0;    // softer — boost CDF discrimination
    private static final double CURV_SIGMA = 1.5;
    private static final int DTW_BAND = 8;
    private static final int TF_SAMPLES = 72;
    private static final int CORNER_WINDOW = 3;

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
        // ═══ Phase 1: Hard cycle gate (on raw binary — before thinning) ═══
        int drawnCycles = GeometryUtils.detectTrueCycles(drawn);
        int tplCycles = template.trueCycleCount();

        if (drawnCycles == 0 && tplCycles > 0) {
            return MatchResult.NONE;
        }
        if (drawnCycles != tplCycles) {
            return MatchResult.NONE;
        }

        // Cycle quality check: noise often has tiny accidental holes.
        // If drawn's largest enclosed area is < 15% of template's, penalize.
        float cycleQuality = 1.0f;
        if (tplCycles > 0 && drawnCycles > 0) {
            int drawnMaxArea = GeometryUtils.maxEnclosedArea(drawn);
            int tplMaxArea = GeometryUtils.maxEnclosedArea(template.pattern());
            if (tplMaxArea > 0 && drawnMaxArea < tplMaxArea / 5) {
                // Tiny accidental hole → not a real cycle
                cycleQuality = 0.20f;
            } else if (tplMaxArea > 0 && drawnMaxArea < tplMaxArea / 3) {
                cycleQuality = 0.50f;
            }
        }

        // Prune #1: clean raw skeleton (removes drawing noise before normalize)
        int[][] rawSkel = GeometryUtils.thin(drawn);
        int[][] rawPruned = GeometryUtils.pruneSkeleton(rawSkel, 0.04);

        // Normalize, then Prune #2: build graph from normalized + cleaned skeleton
        int[][] normDrawn = GeometryUtils.normalize(drawn, 32, 32);
        int[][] normSkel = GeometryUtils.thin(normDrawn);
        int[][] normPruned = GeometryUtils.pruneSkeleton(normSkel, 0.04);
        GeometryUtils.SkeletonGraph drawnGraph = GeometryUtils.buildSkeletonGraph(normPruned);

        // ═══ Phase 2: Composite graph score (WL kernel + degree sequence + structural penalty) ═══
        GeometryUtils.SkeletonGraph tplGraph = template.skeletonGraph();
        float wlScore = computeWLGraphScore(drawnGraph, tplGraph);
        float degSeqScore = computeDegreeSequenceScore(drawnGraph, tplGraph);
        float rawGraphScore = 0.65f * wlScore + 0.35f * degSeqScore;
        float structPenalty = computeStructuralComplexityPenalty(drawnGraph, tplGraph);

        // Minimum skeleton length penalty: noise produces tiny skeletons
        // that coincidentally pass cycle/endpoint checks
        float minLenPenalty = 1.0f;
        int dLen = drawnGraph.totalLength();
        int tLen = tplGraph.totalLength();
        if (tLen > 8 && dLen < tLen / 4) {
            minLenPenalty = 0.05f;   // extreme: noise skeleton is tiny
        } else if (tLen > 8 && dLen < tLen / 3) {
            minLenPenalty = 0.15f;
        } else if (tLen > 8 && dLen < tLen / 2) {
            minLenPenalty = 0.45f;
        }

        float graphScore = rawGraphScore * structPenalty * structPenalty * minLenPenalty;

        // ═══ Phase 3: Supplementary descriptors (from normalized image) ═══
        GeometryUtils.Contour contour = GeometryUtils.traceContour(normDrawn);
        double[] tfDrawn = GeometryUtils.turningFunction(contour, TF_SAMPLES);
        double[] cdfDrawn = GeometryUtils.centroidDistanceFunction(contour, TF_SAMPLES);

        double[] tfTpl = template.turningFunction();
        double[] cdfTpl = template.centroidDistanceFunction();

        // Curvature DTW — use precomputed curvTpl, apply complexity normalization
        double[] curvDrawn = GeometryUtils.curvatureFromTurningFunction(tfDrawn, CURV_SIGMA);
        double[] curvTpl = template.curvature();

        double curvDist = GeometryUtils.cyclicDtwDistance(curvDrawn, curvTpl, DTW_BAND);
        if (template.allowMirror()) {
            double[] curvDrawnMir = new double[TF_SAMPLES];
            for (int i = 0; i < TF_SAMPLES; i++)
                curvDrawnMir[i] = -curvDrawn[TF_SAMPLES - 1 - i];
            double mirrorDist = GeometryUtils.cyclicDtwDistance(curvDrawnMir, curvTpl, DTW_BAND);
            curvDist = Math.min(curvDist, mirrorDist);
        }
        // Complexity normalization: penalize if drawn and tpl have different curvature energy
        double curvEnergyDrawn = 0;
        for (double c : curvDrawn) curvEnergyDrawn += c * c;
        curvEnergyDrawn = Math.sqrt(curvEnergyDrawn / TF_SAMPLES);
        double curvComplexity = Math.min(curvEnergyDrawn, template.curvEnergy())
                              / Math.max(curvEnergyDrawn, template.curvEnergy());
        float curvScore = (float) (Math.exp(-LAMBDA_CURV * curvDist) * curvComplexity);

        // CDF L2
        double cdfDist = GeometryUtils.tfDistance(cdfDrawn, cdfTpl, false);
        if (template.allowMirror()) {
            double[] cdfDrawnMir = new double[TF_SAMPLES];
            for (int i = 0; i < TF_SAMPLES; i++)
                cdfDrawnMir[i] = cdfDrawn[TF_SAMPLES - 1 - i];
            double mirrorDist = GeometryUtils.tfDistance(cdfDrawnMir, cdfTpl, false);
            cdfDist = Math.min(cdfDist, mirrorDist);
        }
        // CDF complexity normalization — fire has wider CDF range than water
        double cdfEnergyDrawn = 0;
        for (double c : cdfDrawn) cdfEnergyDrawn += c * c;
        cdfEnergyDrawn = Math.sqrt(cdfEnergyDrawn / TF_SAMPLES);
        double cdfComplexity = Math.min(cdfEnergyDrawn, template.cdfEnergy())
                             / Math.max(cdfEnergyDrawn, template.cdfEnergy());
        float cdfScore = (float) (Math.exp(-LAMBDA_CDF * cdfDist) * cdfComplexity);

        // CDF skewness — asymmetry check (water droplet: pos skew; fire: near zero)
        double skewDrawn = computeSkewness(cdfDrawn);
        double skewTpl = computeSkewness(cdfTpl);
        float skewScore = (float) Math.max(0, 1.0 - Math.abs(skewDrawn - skewTpl) / 1.5);

        // CDF range ratio — fire has wider range (multiple bumps) vs water (single bump)
        double cdfRangeDrawn = cdfRange(cdfDrawn);
        double cdfRangeTpl = cdfRange(cdfTpl);
        float rangeScore = (float) Math.max(0, 1.0 - Math.abs(cdfRangeDrawn - cdfRangeTpl) / 2.0);

        // Pixel count ratio — both raw → same shape → similar area
        int cntD = countPixels(drawn);
        int cntT = countPixels(template.pattern());
        float pixelRatio = (float) Math.min(cntD, cntT) / Math.max(cntD, cntT);

        // ═══ Phase 4: Combined score — graph-dominant with per-cycle-class adaptation ═══
        boolean isZeroCycle = (tplCycles == 0);

        float confidence;
        if (isZeroCycle) {
            // Zero-cycle shapes: cycle gate can't filter noise → graph MUST dominate
            confidence = 0.50f * graphScore
                       + 0.12f * curvScore
                       + 0.10f * cdfScore
                       + 0.08f * skewScore
                       + 0.06f * rangeScore
                       + 0.14f * pixelRatio;
        } else {
            // ≥1 cycle: cycle gate already rejects noise → balanced
            // Apply cycle quality penalty: tiny accidental holes → lower score
            confidence = cycleQuality * (
                          0.35f * graphScore
                        + 0.16f * curvScore
                        + 0.14f * cdfScore
                        + 0.12f * skewScore
                        + 0.08f * rangeScore
                        + 0.15f * pixelRatio);
        }

        return new MatchResult(confidence, 0f, false, 1f, graphScore);
    }

    // ═══════════════════ Primary Graph Scoring ═══════════════════

    /**
     * Primary graph structural score — used AFTER the cycle gate has passed.
     *
     * <p>Six features capture the structural topology of the skeleton graph.
     * Each is inherently rotation/scale invariant (KS on sorted normalized
     * distributions, and ratios).
     */
    private static float computePrimaryGraphScore(
            GeometryUtils.SkeletonGraph drawn, GeometryUtils.SkeletonGraph tpl,
            float[] tplWeights) {
        int nD = drawn.nodeCount(), nT = tpl.nodeCount();
        int eD = drawn.edgeCount(), eT = tpl.edgeCount();
        if (nD == 0 && nT == 0) return 1f;
        if (eD == 0 && eT == 0) return 0.7f;
        if (eD == 0 || eT == 0) return 0.2f;

        // Feature 1: Edge count ratio (r⁴ — strict)
        float fEdgeCnt = sharpRatio(eD, eT);

        // Feature 2: Endpoint count ratio (r⁴ — strict topological invariant)
        int epsD = drawn.endpointCount(), epsT = tpl.endpointCount();
        float fEps = (epsD == 0 && epsT == 0) ? 1f
                    : (epsD == 0 || epsT == 0) ? 0.2f : sharpRatio(epsD, epsT);

        // Feature 3: Edge length distribution KS
        double[] dLens = edgeLengths(drawn), tLens = edgeLengths(tpl);
        float fEdgeLen = ksScore(dLens, tLens);

        // Feature 4: Edge total-curvature distribution KS
        double[] dCurv = edgeCurvTotals(drawn), tCurv = edgeCurvTotals(tpl);
        float fEdgeCurv = ksScore(dCurv, tCurv);

        // Feature 5: Node degree distribution KS
        double[] dDeg = degreeDistribution(drawn), tDeg = degreeDistribution(tpl);
        float fDegree = ksScore(dDeg, tDeg);

        // Feature 6: Junction + Corner node count ratio (r⁴)
        int jncD = (int) drawn.nodes().stream().filter(n -> n.type() == GeometryUtils.NodeType.JUNCTION
                        || n.type() == GeometryUtils.NodeType.CORNER).count();
        int jncT = (int) tpl.nodes().stream().filter(n -> n.type() == GeometryUtils.NodeType.JUNCTION
                        || n.type() == GeometryUtils.NodeType.CORNER).count();
        float fJnc = (jncD == 0 && jncT == 0) ? 1f : sharpRatio(jncD, jncT);

        return 0.18f * fEdgeCnt + 0.26f * fEps + 0.20f * fEdgeLen
             + 0.16f * fEdgeCurv + 0.12f * fDegree + 0.08f * fJnc;
    }

    // ═══════════════════ Structural Complexity Penalty ═══════════════════

    /**
     * Penalizes matches where the drawn graph lacks the structural complexity
     * required to plausibly be the template. A noise scribble with 0-1 edges
     * cannot be an arrow (3 edges, 3 endpoints, 1 junction).
     *
     * @return factor in [0,1] multiplying graphScore
     */
    private static float computeStructuralComplexityPenalty(
            GeometryUtils.SkeletonGraph drawn, GeometryUtils.SkeletonGraph tpl) {
        int epsD = drawn.endpointCount(), epsT = tpl.endpointCount();
        int eD = drawn.edgeCount(), eT = tpl.edgeCount();
        int jncD = (int) drawn.nodes().stream()
                .filter(n -> n.type() == GeometryUtils.NodeType.JUNCTION
                          || n.type() == GeometryUtils.NodeType.CORNER).count();
        int jncT = (int) tpl.nodes().stream()
                .filter(n -> n.type() == GeometryUtils.NodeType.JUNCTION
                          || n.type() == GeometryUtils.NodeType.CORNER).count();
        int nD = drawn.nodeCount(), nT = tpl.nodeCount();

        // If template is trivial, can't meaningfully penalize
        if (eT <= 1 && epsT <= 1 && jncT == 0) return 1.0f;

        // Endpoint adequacy
        float epsOk = (epsT == 0) ? 1.0f
                    : (epsD == 0) ? 0.10f
                    : (epsD >= epsT / 2.0) ? 1.0f
                    : Math.max(0.10f, sharpRatio(epsD, epsT));

        // Edge adequacy
        float edgesOk = (eT == 0) ? 1.0f
                      : (eD == 0) ? 0.06f
                      : softRatio(eD, eT);

        // Junction/corner adequacy
        float jncOk = (jncT == 0) ? 1.0f
                    : (jncD == 0) ? 0.12f
                    : softRatio(jncD, jncT);

        // Node adequacy
        float nodesOk = (nT == 0) ? 1.0f
                      : (nD == 0) ? 0.06f
                      : softRatio(nD, nT);

        return 0.35f * epsOk + 0.30f * edgesOk + 0.20f * jncOk + 0.15f * nodesOk;
    }

    // ═══════════════════ Degree Sequence Score ═══════════════════

    /**
     * Compares sorted degree sequences from highest degree downward.
     * Arrow [3,1,1,1] vs noise [1] → heavily penalized.
     * Arrow vs arrow [3,1,1,1] → 1.0.
     */
    private static float computeDegreeSequenceScore(
            GeometryUtils.SkeletonGraph drawn, GeometryUtils.SkeletonGraph tpl) {
        java.util.List<Integer> dDeg = drawn.degreeSequence();
        java.util.List<Integer> tDeg = tpl.degreeSequence();

        if (dDeg.isEmpty() && tDeg.isEmpty()) return 1.0f;
        if (dDeg.isEmpty() || tDeg.isEmpty()) return 0.15f;

        // Compare from highest degree downward
        int di = dDeg.size() - 1, ti = tDeg.size() - 1;
        int matches = 0;
        int total = Math.max(dDeg.size(), tDeg.size());

        while (di >= 0 && ti >= 0) {
            int dv = dDeg.get(di), tv = tDeg.get(ti);
            if (Math.abs(dv - tv) <= 1) { matches++; di--; ti--; }
            else if (dv > tv) di--;
            else ti--;
        }
        return (float) matches / total;
    }

    // ═══════════════════ Weisfeiler-Lehman Graph Kernel ═══════════════════

    private static final int WL_ITERATIONS = 3;

    /**
     * Compares two skeleton graphs using the Weisfeiler-Lehman (1-WL)
     * graph kernel. Unlike KS-on-distributions, WL refinement captures
     * the actual neighborhood structure: "which node types connect to
     * which other node types, via what kind of edge".
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Initialize node labels from (type, degree)</li>
     *   <li>For k iterations: refine each node's label by hashing
     *       (old_label || sorted_neighbor_labels || edge_attr_bins)</li>
     *   <li>Build histograms of refined labels for both graphs</li>
     *   <li>Score = histogram intersection / max histogram size</li>
     * </ol>
     */
    private static float computeWLGraphScore(
            GeometryUtils.SkeletonGraph drawn, GeometryUtils.SkeletonGraph tpl) {

        int nD = drawn.nodeCount(), nT = tpl.nodeCount();
        int eD = drawn.edgeCount(), eT = tpl.edgeCount();
        if (nD == 0 && nT == 0) return 1f;
        if (eD == 0 && eT == 0) return 0.7f;
        if (eD == 0 || eT == 0) return 0.2f;

        // Build adjacency
        var dAdj = buildAdjacency(drawn);
        var tAdj = buildAdjacency(tpl);

        // Initialize labels from node (type, degree)
        String[] dLabels = initLabels(drawn);
        String[] tLabels = initLabels(tpl);

        // WL iterations
        for (int iter = 0; iter < WL_ITERATIONS; iter++) {
            dLabels = refineLabels(drawn, dAdj, dLabels, iter);
            tLabels = refineLabels(tpl, tAdj, tLabels, iter);
        }

        // Histogram intersection on final labels
        var dHist = buildHistogram(dLabels);
        var tHist = buildHistogram(tLabels);

        int intersection = 0;
        for (var e : dHist.entrySet()) {
            int tCount = tHist.getOrDefault(e.getKey(), 0);
            intersection += Math.min(e.getValue(), tCount);
        }

        return (float) intersection / Math.max(dLabels.length, tLabels.length);
    }

    /** Builds adjacency list: nodeId → list of (neighborId, edgeIndex). */
    private static java.util.List<int[]>[] buildAdjacency(GeometryUtils.SkeletonGraph g) {
        int n = g.nodeCount();
        @SuppressWarnings("unchecked")
        java.util.List<int[]>[] adj = new java.util.ArrayList[n];
        for (int i = 0; i < n; i++) adj[i] = new java.util.ArrayList<>();
        var edges = g.edges();
        for (int ei = 0; ei < edges.size(); ei++) {
            var e = edges.get(ei);
            adj[e.fromId()].add(new int[]{e.toId(), ei});
            adj[e.toId()].add(new int[]{e.fromId(), ei});
        }
        return adj;
    }

    /** Initial label from node type and degree. */
    private static String[] initLabels(GeometryUtils.SkeletonGraph g) {
        var nodes = g.nodes();
        String[] labels = new String[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            var n = nodes.get(i);
            labels[i] = n.type().name().charAt(0) + Integer.toString(n.degree());
        }
        return labels;
    }

    /** Bin edge length: 4 levels. */
    private static int lenBin(int length) {
        return length < 5 ? 0 : length < 10 ? 1 : length < 20 ? 2 : 3;
    }

    /** Bin edge curvature: 4 levels. */
    private static int curvBin(double totalCurv) {
        return totalCurv < 0.2 ? 0 : totalCurv < 0.5 ? 1 : totalCurv < 1.2 ? 2 : 3;
    }

    /** Bin curvature std: 3 levels — distinguishes smooth from jagged edges. */
    private static int curvStdBin(double curvStd) {
        return curvStd < 0.1 ? 0 : curvStd < 0.3 ? 1 : 2;
    }

    /** Bin inflection count: 3 levels — noise edges have many sign changes. */
    private static int inflBin(int inflectionCount) {
        return inflectionCount == 0 ? 0 : inflectionCount == 1 ? 1 : 2;
    }

    /**
     * One WL refinement step.
     * New label = hash(old_label + compressed_multiset_of_neighbor_info).
     * Each neighbor contributes: "neighborLabel,lenBin,curvBin".
     */
    private static String[] refineLabels(
            GeometryUtils.SkeletonGraph g,
            java.util.List<int[]>[] adj,
            String[] oldLabels,
            int iteration) {

        var edges = g.edges();
        int n = g.nodeCount();
        String[] newLabels = new String[n];

        for (int i = 0; i < n; i++) {
            // Collect neighbor signatures, sort for canonical order
            java.util.List<String> neighbors = new java.util.ArrayList<>();
            for (int[] nb : adj[i]) {
                int nbId = nb[0], edgeIdx = nb[1];
                var edge = edges.get(edgeIdx);
                String sig = oldLabels[nbId] + ","
                           + lenBin(edge.pathLength()) + ","
                           + curvBin(edge.totalCurvature()) + ","
                           + curvStdBin(edge.curvatureStd()) + ","
                           + inflBin(edge.inflectionCount());
                neighbors.add(sig);
            }
            java.util.Collections.sort(neighbors);

            // Build new label
            StringBuilder sb = new StringBuilder(oldLabels[i]);
            for (String ns : neighbors)
                sb.append('|').append(ns);

            // Simple hash: use the string itself for small graphs
            // Prefix with iteration to avoid collisions across rounds
            newLabels[i] = "w" + iteration + "_" + Integer.toHexString(sb.toString().hashCode());
        }
        return newLabels;
    }

    /** Builds a histogram from label array. */
    private static java.util.Map<String, Integer> buildHistogram(String[] labels) {
        java.util.Map<String, Integer> hist = new java.util.HashMap<>();
        for (String label : labels)
            hist.merge(label, 1, Integer::sum);
        return hist;
    }

    /** Sharpened ratio: (min/max)^4 — tight tolerance. */
    private static float sharpRatio(double a, double b) {
        double max = Math.max(a, b);
        if (max < 1e-9) return 1f;
        double r = Math.min(a, b) / max;
        return (float) (r * r * r * r);
    }

    /** Degree sequence as sorted double[] for KS comparison. */
    private static double[] degreeDistribution(GeometryUtils.SkeletonGraph g) {
        return g.nodes().stream().mapToDouble(n -> n.degree()).sorted().toArray();
    }

    /** Build skeleton graph from drawn image at 32×32 native (no upscale) */
    private static GeometryUtils.SkeletonGraph buildDrawnGraph(int[][] image) {
        int[][] skel = GeometryUtils.thin(image);
        int[][] pruned = GeometryUtils.pruneSkeleton(skel, 0.04);
        return GeometryUtils.buildSkeletonGraph(pruned);
    }

    private static int countPixels(int[][] img) {int c=0;for(int[]r:img)for(int v:r)if(v!=0)c++;return c;}

    /**
     * Skeleton graph matching score — cycle + endpoint + edge length KS + corner nodes.
     */
    private static float computeSkeletonGraphScore(
            GeometryUtils.SkeletonGraph drawn, GeometryUtils.SkeletonGraph tpl, float[] tplWeights) {
        int nD = drawn.nodeCount(), nT = tpl.nodeCount();
        int eD = drawn.edgeCount(), eT = tpl.edgeCount();
        if (nD == 0 && nT == 0) return 1f;
        if (eD == 0 && eT == 0) return 0.8f;
        if (eD == 0 || eT == 0) return 0.3f;

        int cD = drawn.cycleCount(), cT = tpl.cycleCount();
        int deltaCycles = Math.abs(cD - cT);
        int epsD = drawn.endpointCount(), epsT = tpl.endpointCount();
        if (deltaCycles > 1) return 0.05f;
        if (Math.abs(epsD - epsT) > 2) return 0.10f;
        float cycleScore = (deltaCycles == 0) ? 1f : 0.5f;
        float epsScore = (epsD == 0 && epsT == 0) ? 1f :
            (epsD == 0 || epsT == 0) ? 0.3f : (float)Math.min(epsD,epsT)/Math.max(epsD,epsT);

        double[] dLens = new double[eD], tLens = new double[eT];
        double maxDL = 0, maxTL = 0;
        for (int i = 0; i < eD; i++) { dLens[i] = drawn.edges().get(i).pathLength(); if (dLens[i] > maxDL) maxDL = dLens[i]; }
        for (int i = 0; i < eT; i++) { tLens[i] = tpl.edges().get(i).pathLength(); if (tLens[i] > maxTL) maxTL = tLens[i]; }
        if (maxDL < 1e-9) maxDL = 1; if (maxTL < 1e-9) maxTL = 1;
        for (int i = 0; i < eD; i++) dLens[i] /= maxDL;
        for (int i = 0; i < eT; i++) tLens[i] /= maxTL;
        java.util.Arrays.sort(dLens); java.util.Arrays.sort(tLens);
        float edgeLenKS = (float)(1.0 - ksMax(dLens, tLens));

        return 0.25f*cycleScore + 0.20f*epsScore + 0.30f*edgeLenKS
            + 0.15f*(float)Math.min(nD,nT)/Math.max(1,Math.max(nD,nT))
            + 0.10f*(float)Math.min(eD,eT)/Math.max(1,Math.max(eD,eT));
    }

    /** CSS corner pairwise-distance KS. */
    private static float computeCornerGraphScore(List<Corner> drawn, List<Corner> tpl) {
        int nD = drawn.size(), nT = tpl.size();
        if (nD == 0 && nT == 0) return 1f;
        if (nD < 2 || nT < 2) {
            if (nD == 0 && nT == 0) return 1f;
            if (nD == 0 || nT == 0) return 0.3f;
            return 0.3f + 0.7f*(float)Math.min(nD,nT)/Math.max(nD,nT);
        }
        int kD = nD*(nD-1)/2; double[] dD = new double[kD]; int di=0;
        for (int i=0;i<nD;i++) { Corner ci=drawn.get(i);
            for (int j=i+1;j<nD;j++) { Corner cj=drawn.get(j);
                double dx=ci.x()-cj.x(), dy=ci.y()-cj.y(); dD[di++]=Math.sqrt(dx*dx+dy*dy); } }
        java.util.Arrays.sort(dD); double dM=dD[kD-1]; if(dM<1e-9)dM=1;
        int kT=nT*(nT-1)/2; double[] tD = new double[kT]; int ti=0;
        for (int i=0;i<nT;i++) { Corner ci=tpl.get(i);
            for (int j=i+1;j<nT;j++) { Corner cj=tpl.get(j);
                double dx=ci.x()-cj.x(), dy=ci.y()-cj.y(); tD[ti++]=Math.sqrt(dx*dx+dy*dy); } }
        java.util.Arrays.sort(tD); double tM=tD[kT-1]; if(tM<1e-9)tM=1;
        double ksM=0; int p=0,q=0;
        while(p<kD&&q<kT){double diff=Math.abs((double)p/kD-(double)q/kT);if(diff>ksM)ksM=diff;
            if(dD[p]/dM<=tD[q]/tM)p++;else q++;}
        while(p<kD){double diff=Math.abs((double)p/kD-1.0);if(diff>ksM)ksM=diff;p++;}
        while(q<kT){double diff=Math.abs(1.0-(double)q/kT);if(diff>ksM)ksM=diff;q++;}
        float ksS=(float)(1.0-ksM);
        return 0.6f*ksS + 0.4f*(float)Math.min(nD,nT)/Math.max(nD,nT);
    }

    /**
     * Full graph matching score — uses ALL graph features with soft similarity.
     * No hard topology filters; every feature contributes via KS or soft ratio.
     *
     * <p>Features (13 total):
     * <ol>
     *   <li>Edge length KS (skeleton edges)</li>
     *   <li>Edge total-curvature KS</li>
     *   <li>Edge curvature-std KS</li>
     *   <li>Edge inflection-count soft ratio</li>
     *   <li>Cycle-count soft ratio</li>
     *   <li>Endpoint-count soft ratio</li>
     *   <li>Node-count ratio</li>
     *   <li>Edge-count ratio</li>
     *   <li>Corner (CSS) pairwise-distance KS</li>
     *   <li>Corner (CSS) count ratio</li>
     *   <li>Skeleton CORNER node count ratio</li>
     *   <li>Skeleton JUNCTION node count ratio</li>
     *   <li>Skeleton ENDPOINT per edge ratio</li>
     * </ol>
     */
    private static float computeFullGraphScore(
            GeometryUtils.SkeletonGraph drawn, GeometryUtils.SkeletonGraph tpl,
            List<Corner> cssDrawn, List<Corner> cssTpl) {
        int nD = drawn.nodeCount(), nT = tpl.nodeCount();
        int eD = drawn.edgeCount(), eT = tpl.edgeCount();

        // Edge cases
        if (nD == 0 && nT == 0) return 1f;
        if (eD == 0 && eT == 0) return 0.7f;
        if (eD == 0 || eT == 0) return 0.2f;

        // ── Feature 1: Edge length KS ──
        float fEdgeLen = ksScore(edgeLengths(drawn), edgeLengths(tpl));

        // ── Feature 2: Edge total-curvature KS ──
        float fEdgeCurv = ksScore(edgeCurvTotals(drawn), edgeCurvTotals(tpl));

        // ── Feature 3: Edge curvature-std KS ──
        float fEdgeStd = ksScore(edgeCurvStds(drawn), edgeCurvStds(tpl));

        // ── Feature 5: Cycle count soft ratio ──
        float fCycle = softRatio(drawn.cycleCount(), tpl.cycleCount());

        // ── Feature 6: Endpoint count ──
        int epsD = drawn.endpointCount(), epsT = tpl.endpointCount();
        float fEps = (epsD == 0 && epsT == 0) ? 1f : (epsD == 0 || epsT == 0) ? 0.3f :
                     softRatio(epsD, epsT);

        // ── Feature 7-8: Node/edge count ──
        float fNode = softRatio(nD, nT);
        float fEdge = softRatio(eD, eT);

        // ── Feature 9: CSS corner pairwise-distance KS ──
        float cssDistKS = cssCornerKs(cssDrawn, cssTpl);
        int cssD = cssDrawn.size(), cssT = cssTpl.size();
        float cssCnt = (cssD == 0 && cssT == 0) ? 1f : (cssD == 0 || cssT == 0) ? 0.3f :
                       softRatio(cssD, cssT);

        // ── Feature 10: Skeleton total-length ratio ──
        float fTotalLen = softRatio(drawn.totalLength(), tpl.totalLength());

        // ── Feature 11: Skeleton CORNER nodes ──
        int skCornD = (int)drawn.nodes().stream().filter(n->n.type()==GeometryUtils.NodeType.CORNER).count();
        int skCornT = (int)tpl.nodes().stream().filter(n->n.type()==GeometryUtils.NodeType.CORNER).count();
        float fSkCorn = (skCornD == 0 && skCornT == 0) ? 1f : softRatio(Math.max(1,skCornD), Math.max(1,skCornT));

        // ── Best-fit edge pairing: each template edge to best drawn edge ──
        float fBestPair = bestPairEdgeScore(drawn, tpl);

        // ── Score ──
        float fEdgeLen2 = fEdgeLen * fEdgeLen;

        float score = 0.24f * fEdgeLen2    // edge length KS²
                    + 0.20f * fCycle       // Euler invariant
                    + 0.12f * fBestPair    // ★ best-pair edge matching
                    + 0.12f * fEps         // endpoint count
                    + 0.10f * fTotalLen    // total skeleton length
                    + 0.08f * fEdgeCurv    // total curvature KS
                    + 0.06f * fNode        // node count
                    + 0.04f * cssDistKS    // CSS distances
                    + 0.04f * cssCnt;      // CSS count
        return Math.max(0.01f, Math.min(1f, score));
    }

    /**
     * Best-fit edge pairing: for each template edge, find the drawn edge
     * with most similar (length + totalCurvature). Average over pairs.
     */
    private static float bestPairEdgeScore(GeometryUtils.SkeletonGraph drawn, GeometryUtils.SkeletonGraph tpl) {
        var dE = drawn.edges();
        var tE = tpl.edges();
        if (dE.isEmpty() || tE.isEmpty()) return 0.5f;
        float sum = 0;
        for (var te : tE) {
            double bestSim = 0;
            for (var de : dE) {
                double lenSim = softRatio(te.pathLength(), de.pathLength());
                double curvSim = softRatio(te.totalCurvature(), de.totalCurvature());
                double sim = lenSim * 0.6 + curvSim * 0.4;
                if (sim > bestSim) bestSim = sim;
            }
            sum += bestSim;
        }
        return sum / tE.size();
    }

    // ── Feature extractors ──

    private static double[] edgeLengths(GeometryUtils.SkeletonGraph g) {
        var es = g.edges(); double[] a = new double[es.size()]; int i = 0;
        for (var e : es) a[i++] = e.pathLength(); return a;
    }
    private static double[] edgeCurvTotals(GeometryUtils.SkeletonGraph g) {
        var es = g.edges(); double[] a = new double[es.size()]; int i = 0;
        for (var e : es) a[i++] = e.totalCurvature(); return a;
    }
    private static double[] edgeCurvStds(GeometryUtils.SkeletonGraph g) {
        var es = g.edges(); double[] a = new double[es.size()]; int i = 0;
        for (var e : es) a[i++] = e.curvatureStd(); return a;
    }
    private static double meanInflections(GeometryUtils.SkeletonGraph g) {
        var es = g.edges(); if (es.isEmpty()) return 0; double s = 0;
        for (var e : es) s += e.inflectionCount(); return s / es.size();
    }
    private static double meanCurvStd(GeometryUtils.SkeletonGraph g) {
        var es = g.edges(); if (es.isEmpty()) return 0; double s = 0;
        for (var e : es) s += e.curvatureStd(); return s / es.size();
    }
    private static double edgeSpan(GeometryUtils.SkeletonGraph g) {
        var es = g.edges(); if (es.isEmpty()) return 0;
        double min = Double.MAX_VALUE, max = 0;
        for (var e : es) { double v = e.pathLength(); if (v < min) min = v; if (v > max) max = v; }
        return max > 1e-9 ? min / max : 0;
    }
    /** Skeleton bounding-box aspect ratio min/max */
    private static double skelAspect(GeometryUtils.SkeletonGraph g) {
        if (g.nodes().isEmpty()) return 1;
        int minX = Integer.MAX_VALUE, maxX = 0, minY = Integer.MAX_VALUE, maxY = 0;
        for (var n : g.nodes()) {
            if (n.x() < minX) minX = n.x();
            if (n.x() > maxX) maxX = n.x();
            if (n.y() < minY) minY = n.y();
            if (n.y() > maxY) maxY = n.y();
        }
        double w = maxX - minX + 1, h = maxY - minY + 1;
        if (w < 1 || h < 1) return 1;
        return Math.min(w, h) / Math.max(w, h);
    }

    /** Max-normalize then KS: score = 1 - max|CDF diff| */
    private static float ksScore(double[] a, double[] b) {
        int na = a.length, nb = b.length;
        if (na == 0 && nb == 0) return 1f;
        if (na == 0 || nb == 0) return 0.3f;
        double mxA = 0, mxB = 0;
        for (double v : a) if (v > mxA) mxA = v;
        for (double v : b) if (v > mxB) mxB = v;
        if (mxA < 1e-9) mxA = 1; if (mxB < 1e-9) mxB = 1;
        double[] naA = new double[na], naB = new double[nb];
        for (int i = 0; i < na; i++) naA[i] = a[i] / mxA;
        for (int i = 0; i < nb; i++) naB[i] = b[i] / mxB;
        java.util.Arrays.sort(naA); java.util.Arrays.sort(naB);
        return (float)(1.0 - ksMax(naA, naB));
    }

    /** Soft ratio match: min/max, sharpened via x² */
    private static float softRatio(double a, double b) {
        double sum = a + b;
        if (sum < 1e-9) return 1f;
        double r = Math.min(a, b) / Math.max(a, b);
        return (float)(r * r);
    }

    /** Mean pairwise distance between CSS corners (normalized by max), for KS */
    private static float cssCornerKs(List<Corner> drawn, List<Corner> tpl) {
        int nD = drawn.size(), nT = tpl.size();
        if (nD < 2 || nT < 2) {
            if (nD == 0 && nT == 0) return 1f;
            if (nD == 0 || nT == 0) return 0.3f;
            return softRatio(nD, nT);
        }
        int kD = nD * (nD - 1) / 2;
        double[] dD = new double[kD]; int di = 0;
        for (int i = 0; i < nD; i++) {
            Corner ci = drawn.get(i);
            for (int j = i + 1; j < nD; j++) {
                Corner cj = drawn.get(j);
                double dx = ci.x() - cj.x(), dy = ci.y() - cj.y();
                dD[di++] = Math.sqrt(dx * dx + dy * dy);
            }
        }
        int kT = nT * (nT - 1) / 2;
        double[] tD = new double[kT]; int ti = 0;
        for (int i = 0; i < nT; i++) {
            Corner ci = tpl.get(i);
            for (int j = i + 1; j < nT; j++) {
                Corner cj = tpl.get(j);
                double dx = ci.x() - cj.x(), dy = ci.y() - cj.y();
                tD[ti++] = Math.sqrt(dx * dx + dy * dy);
            }
        }
        return ksScore(dD, tD);
    }

    // ── Keep old helpers ──
    private static double ksMax(double[] a, double[] b) {
        int na = a.length, nb = b.length;
        if (na == 0 || nb == 0) return 1.0;
        double maxDiff = 0; int p = 0, q = 0;
        while (p < na && q < nb) {
            double diff = Math.abs((double)p/na - (double)q/nb);
            if (diff > maxDiff) maxDiff = diff;
            if (a[p] <= b[q]) p++; else q++; }
        while (p < na) { double diff = Math.abs((double)p/na - 1.0); if (diff > maxDiff) maxDiff = diff; p++; }
        while (q < nb) { double diff = Math.abs(1.0 - (double)q/nb); if (diff > maxDiff) maxDiff = diff; q++; }
        return maxDiff;
    }

    private static double cdfRange(double[] arr) {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double v : arr) { if (v < min) min = v; if (v > max) max = v; }
        return max - min;
    }

    /** Computes skewness of a 1D array (measure of asymmetry). */
    private static double computeSkewness(double[] arr) {
        int n = arr.length;
        double mean = 0;
        for (double v : arr) mean += v;
        mean /= n;
        double m2 = 0, m3 = 0;
        for (double v : arr) {
            double d = v - mean;
            m2 += d * d;
            m3 += d * d * d;
        }
        m2 /= n; m3 /= n;
        double std = Math.sqrt(m2);
        if (std < 1e-9) return 0;
        return m3 / (std * std * std);
    }

    /**
     * Matches two corner sets by finding the optimal shift.
     * Returns the fraction of corners that match, capped at 1.0.
     */
    private static float matchCorners(List<Corner> drawn, List<Corner> template, int contourLen) {
        if (drawn.isEmpty() && template.isEmpty()) return 1f;
        if (drawn.isEmpty() || template.isEmpty()) return 0f;
        if (contourLen <= 0) return 0f;

        int nDrawn = drawn.size(), nTpl = template.size();
        float bestRatio = 0f;

        // Try all shifts (the TF shift is unknown to us here, but corners are sparse)
        for (int shift = 0; shift < contourLen; shift += Math.max(1, contourLen / 16)) {
            int matched = 0;
            for (Corner dc : drawn) {
                int si = (dc.idx() + shift) % contourLen;
                for (Corner tc : template) {
                    int diff = Math.abs(si - tc.idx());
                    diff = Math.min(diff, contourLen - diff);
                    if (diff <= CORNER_WINDOW) { matched++; break; }
                }
            }
            float ratio = (float) matched / Math.max(nDrawn, nTpl);
            if (ratio > bestRatio) bestRatio = ratio;
        }

        return Math.min(bestRatio, 1f);
    }
}
