package com.astune.gyromancy.api.symbol;

import com.astune.gyromancy.util.GeometryUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines the canonical pattern for a symbol used in magic arrays.
 * Symbols are drawn by players and recognized via geometric pattern matching.
 *
 * <p>Lazily caches ART descriptor and edge histogram for fast matching.
 */
public record SymbolTemplate(
        ResourceLocation id,
        int[][] pattern,
        int featurePoints,
        boolean allowRotation,
        boolean allowMirror,
        SymbolRole defaultRole
) {
    // ── Convenience ──

    public int getWidth() { return pattern.length > 0 ? pattern[0].length : 0; }
    public int getHeight() { return pattern.length; }

    // ── Cached descriptors (lazy, computed once per template) ──

    /** Combined cache: all precomputed descriptors + self-reference distances */
    private record TemplateDescriptors(GeometryUtils.Contour contour, double[] tf,
                                       double[] cdf,
                                       double[] curv,
                                       List<GeometryUtils.Corner> corners,
                                       GeometryUtils.SkeletonGraph graph,
                                       float[] edgeWeights,
                                       int trueCycleCount,
                                       double curvSelfDist,
                                       double cdfSelfDist) {}

    private static final java.util.concurrent.ConcurrentHashMap<ResourceLocation, TemplateDescriptors> descCache =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<ResourceLocation, GeometryUtils.PCAResult> pcaCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    private TemplateDescriptors descriptors() {
        return descCache.computeIfAbsent(id, k -> {
            // Topology from raw pattern (pre-normalize — preserves true structure)
            int trueCycles = GeometryUtils.detectTrueCycles(pattern);

            // Prune #1: clean raw skeleton (removes drawing noise before normalize)
            int[][] rawSkel = GeometryUtils.thin(pattern);
            int[][] rawPruned = GeometryUtils.pruneSkeleton(rawSkel, 0.04);

            // Metrics & graph from normalized pattern
            int[][] norm = GeometryUtils.normalize(pattern, 32, 32);
            int[][] hires = GeometryUtils.upscaleConnectivityPreserving(norm, 128);
            int upK = (128 + Math.min(norm.length, norm[0].length) - 1)
                    / Math.min(norm.length, norm[0].length);
            int[][] smoothHires = GeometryUtils.gaussianSmoothBinary(hires, upK / 3.0);

            // Prune #2: build graph from normalized + upscaled + smoothed + cleaned skeleton
            int[][] normSkel = GeometryUtils.thin(smoothHires);
            int[][] normPruned = GeometryUtils.pruneSkeleton(normSkel, 0.04);
            GeometryUtils.SkeletonGraph graph = GeometryUtils.buildSkeletonGraph(normPruned);
            float[] weights = computeEdgeWeights(graph);

            GeometryUtils.Contour contour = GeometryUtils.traceContour(norm);
            double[] tf = GeometryUtils.turningFunction(contour, 72);
            double[] cdf = GeometryUtils.centroidDistanceFunction(contour, 72);
            double[] curv = GeometryUtils.curvatureFromTurningFunction(tf, 1.5);
            GeometryUtils.Contour thinned = GeometryUtils.traceThinnedContour(norm);
            List<GeometryUtils.Corner> corners = GeometryUtils.detectCorners(thinned, 4);

            // Precomputed curv for use in match() (avoids re-deriving from TF)
            // and curvature energy as intrinsic complexity measure
            double curvEnergy = 0;
            for (double c : curv) curvEnergy += c * c;
            curvEnergy = Math.sqrt(curvEnergy / curv.length); // RMS curvature

            double cdfEnergy = 0;
            for (double c : cdf) cdfEnergy += c * c;
            cdfEnergy = Math.sqrt(cdfEnergy / cdf.length); // RMS CDF

            return new TemplateDescriptors(contour, tf, cdf, curv, corners, graph,
                    weights, trueCycles, curvEnergy, cdfEnergy);
        });
    }

    /** Precompute template edge importance weights */
    private static float[] computeEdgeWeights(GeometryUtils.SkeletonGraph graph) {
        var edges = graph.edges();
        int n = edges.size();
        float[] w = new float[n];
        if (n == 0) return w;
        int totalLen = graph.totalLength();
        for (int i = 0; i < n; i++) {
            double ratio = (double) edges.get(i).pathLength() / Math.max(1, totalLen);
            if (ratio > 0.25) w[i] = 1.0f;
            else if (ratio > 0.10) w[i] = 0.6f;
            else w[i] = 0.2f;
        }
        return w;
    }

    public GeometryUtils.PCAResult pca() {
        return pcaCache.computeIfAbsent(id, k -> GeometryUtils.computePCA(pattern));
    }

    public double[] turningFunction() { return descriptors().tf(); }
    public double[] centroidDistanceFunction() { return descriptors().cdf(); }
    public List<GeometryUtils.Corner> corners() { return descriptors().corners(); }
    public GeometryUtils.SkeletonGraph skeletonGraph() { return descriptors().graph(); }
    public float[] edgeWeights() { return descriptors().edgeWeights(); }
    public int trueCycleCount() { return descriptors().trueCycleCount(); }
    public double[] curvature() { return descriptors().curv(); }
    public double curvEnergy() { return descriptors().curvSelfDist(); }
    public double cdfEnergy() { return descriptors().cdfSelfDist(); }

    public static void clearCaches() { pcaCache.clear(); descCache.clear(); }

    // ── Codec ──

    public static final Codec<int[][]> PATTERN_CODEC = Codec.list(Codec.list(Codec.INT))
            .xmap(SymbolTemplate::listToPattern, SymbolTemplate::patternToList);

    public static final Codec<SymbolTemplate> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("id").forGetter(SymbolTemplate::id),
                    PATTERN_CODEC.fieldOf("pattern").forGetter(SymbolTemplate::pattern),
                    Codec.INT.fieldOf("featurePoints").forGetter(SymbolTemplate::featurePoints),
                    Codec.BOOL.fieldOf("allowRotation").forGetter(SymbolTemplate::allowRotation),
                    Codec.BOOL.fieldOf("allowMirror").forGetter(SymbolTemplate::allowMirror),
                    SymbolRole.CODEC.fieldOf("defaultRole").forGetter(SymbolTemplate::defaultRole)
            ).apply(instance, SymbolTemplate::new)
    );

    private static int[][] listToPattern(List<List<Integer>> list) {
        if (list.isEmpty()) return new int[0][0];
        int h = list.size(), w = list.getFirst().size();
        int[][] p = new int[h][w];
        for (int y = 0; y < h; y++) { List<Integer> row = list.get(y); for (int x = 0; x < w; x++) p[y][x] = row.get(x); }
        return p;
    }

    private static List<List<Integer>> patternToList(int[][] p) {
        List<List<Integer>> list = new ArrayList<>(p.length);
        for (int[] row : p) { List<Integer> r = new ArrayList<>(row.length); for (int v : row) r.add(v); list.add(r); }
        return list;
    }
}
