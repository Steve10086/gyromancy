package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.ParameterRune;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.registry.ModSymbols;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;

/** Catalog of built-in symbols and their runtime behavior. */
public final class SymbolCatalog {

    public static final int DEFAULT_GLYPH_COLOR = 0xFFFFFFFF;

    /** Behavior executed when this center symbol forms a valid array with runes.
     *  @return scratch data passed to {@link EndEffect} when the array is destroyed. */
    @FunctionalInterface
    public interface CenterEffect {
        Map<String, Object> execute(ServerLevel level, BlockPos arrayPos,
                                    PositionedGlyph circleGlyph, PositionedGlyph centerGlyph,
                                    List<PositionedGlyph> runes);
    }

    /** Behavior executed when an array bound to this center symbol is destroyed. */
    @FunctionalInterface
    public interface EndEffect {
        void execute(ServerLevel level, BlockPos arrayPos, List<ParameterRune> runes,
                     Map<String, Object> scratchData);
    }

    static final CenterEffect NOOP_CENTER = (level, pos, circle, center, runes) -> Map.of();
    static final EndEffect NOOP_END = (level, pos, runes, data) -> {};

    private SymbolCatalog() {}

    static SkeletonMatcher.SoftThresholds thresholdsFor(String name) {
        for (Symbol sym : ModSymbols.symbols())
            if (sym.name().equals(name)) return sym.thresholds();
        return SkeletonMatcher.DEFAULT_THRESHOLDS;
    }

    public static int glyphColorFor(ResourceLocation id) {
        for (Symbol sym : ModSymbols.symbols())
            if (sym.name().equals(id.getPath())) return sym.glyphColor();
        return DEFAULT_GLYPH_COLOR;
    }

    public static CenterEffect getCenterEffect(ResourceLocation symbolId) {
        for (Symbol sym : ModSymbols.symbols()) {
            if (sym.name().equals(symbolId.getPath()) && sym instanceof CenterSymbol cs) {
                CenterEffect e = cs.centerEffect();
                if (e != null) return e;
            }
        }
        return NOOP_CENTER;
    }

    public static EndEffect getEndEffect(ResourceLocation symbolId) {
        for (Symbol sym : ModSymbols.symbols()) {
            if (sym.name().equals(symbolId.getPath()) && sym instanceof CenterSymbol cs) {
                EndEffect e = cs.endEffect();
                if (e != null) return e;
            }
        }
        return NOOP_END;
    }
}
