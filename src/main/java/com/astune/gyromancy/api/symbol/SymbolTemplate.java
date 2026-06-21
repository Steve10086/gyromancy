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

    /** Combined cache: contour + TF + CDF + corners computed once */
    private record TemplateDescriptors(GeometryUtils.Contour contour, double[] tf,
                                       double[] cdf,
                                       List<GeometryUtils.Corner> corners) {}

    private static final java.util.concurrent.ConcurrentHashMap<ResourceLocation, TemplateDescriptors> descCache =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<ResourceLocation, GeometryUtils.PCAResult> pcaCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    private TemplateDescriptors descriptors() {
        return descCache.computeIfAbsent(id, k -> {
            GeometryUtils.Contour contour = GeometryUtils.traceContour(pattern);
            double[] tf = GeometryUtils.turningFunction(contour, 72);
            double[] cdf = GeometryUtils.centroidDistanceFunction(contour, 72);
            GeometryUtils.Contour thinned = GeometryUtils.traceThinnedContour(pattern);
            List<GeometryUtils.Corner> corners = GeometryUtils.detectCorners(thinned, 4);
            return new TemplateDescriptors(contour, tf, cdf, corners);
        });
    }

    public GeometryUtils.PCAResult pca() {
        return pcaCache.computeIfAbsent(id, k -> GeometryUtils.computePCA(pattern));
    }

    public double[] turningFunction() { return descriptors().tf(); }
    public double[] centroidDistanceFunction() { return descriptors().cdf(); }
    public List<GeometryUtils.Corner> corners() { return descriptors().corners(); }

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
