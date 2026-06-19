package com.astune.gyromancy.api.symbol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines the canonical pattern for a symbol used in magic arrays.
 * Symbols are drawn by players and recognized via geometric pattern matching.
 */
public record SymbolTemplate(
        /** Unique identifier for this symbol */
        ResourceLocation id,
        /** Normalized binary pixel grid (e.g., 32×32). 1 = drawn pixel, 0 = empty */
        int[][] pattern,
        /** Number of characteristic feature points */
        int featurePoints,
        /** Whether rotation produces a valid variant of this symbol */
        boolean allowRotation,
        /** Whether mirroring produces a valid (different) variant */
        boolean allowMirror,
        /** The role this symbol typically plays in a magic array */
        SymbolRole defaultRole
) {
    // ── Convenience ──

    public int getWidth() {
        return pattern.length > 0 ? pattern[0].length : 0;
    }

    public int getHeight() {
        return pattern.length;
    }

    // ── Codec ──

    /** Codec for int[][] via nested list-of-int */
    public static final Codec<int[][]> PATTERN_CODEC = Codec.list(Codec.list(Codec.INT))
            .xmap(
                    SymbolTemplate::listToPattern,
                    SymbolTemplate::patternToList
            );

    /** Full Codec for SymbolTemplate */
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
        int h = list.size();
        int w = list.getFirst().size();
        int[][] pattern = new int[h][w];
        for (int y = 0; y < h; y++) {
            List<Integer> row = list.get(y);
            for (int x = 0; x < w; x++) {
                pattern[y][x] = row.get(x);
            }
        }
        return pattern;
    }

    private static List<List<Integer>> patternToList(int[][] pattern) {
        List<List<Integer>> list = new ArrayList<>(pattern.length);
        for (int[] row : pattern) {
            List<Integer> rowList = new ArrayList<>(row.length);
            for (int val : row) rowList.add(val);
            list.add(rowList);
        }
        return list;
    }
}
