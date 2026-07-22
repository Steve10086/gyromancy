package com.astune.gyromancy.array.runtime;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.ParameterRune;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.ArrayNodeCompiler;
import com.astune.gyromancy.array.compile.CompiledArray;
import com.astune.gyromancy.array.compile.EffectNode;
import com.astune.gyromancy.compile.operator.FireballOp;
import com.astune.gyromancy.symbol.SymbolCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class LegacyRuntimeAdapter {
    static final String CENTER_KEY = "__legacy_center";

    private LegacyRuntimeAdapter() {}

    static RuntimeHandle activate(CompiledArray compiled, ServerLevel level) {
        if (!(compiled.script().root() instanceof EffectNode root)) return new RuntimeHandle(Map.of());
        PositionedGlyph center = primaryGlyph(compiled, root);
        if (center == null) return new RuntimeHandle(Map.of());
        if (root.primaryElement() == ElementType.FIRE) {
            Map<String, Object> data = new HashMap<>(FireballOp.activate(level, compiled.rootCircleGlyph(), center, root));
            data.put(CENTER_KEY, center.symbolId().toString());
            return new RuntimeHandle(Map.copyOf(data));
        }
        List<PositionedGlyph> runes = directLegacyRunes(compiled, center);
        Map<String, Object> scratch = SymbolCatalog.getCenterEffect(center.symbolId())
                .execute(level, compiled.rootCircleGlyph().worldPos(), compiled.rootCircleGlyph(),
                        center, runes);
        Map<String, Object> data = new HashMap<>(scratch == null ? Map.of() : scratch);
        data.put(CENTER_KEY, center.symbolId().toString());
        return new RuntimeHandle(Map.copyOf(data));
    }

    static void deactivate(ServerLevel level, ArrayObject array) {
        Object center = array.scratchData().get(CENTER_KEY);
        if (!(center instanceof String centerId)) return;
        if (ResourceLocation.parse(centerId).getPath().equals("fire")) {
            FireballOp.deactivate(level, array.scratchData());
            return;
        }
        SymbolCatalog.getEndEffect(ResourceLocation.parse(centerId))
                .execute(level, array.rootCircleGlyph().worldPos(), toRuneParams(array.boundGlyphs()), array.scratchData());
    }

    private static List<ParameterRune> toRuneParams(List<PositionedGlyph> glyphs) {
        List<ParameterRune> params = new ArrayList<>();
        for (PositionedGlyph glyph : glyphs) {
            params.add(new ParameterRune(glyph.symbolId(), glyph.confidence(), ""));
        }
        return params;
    }

    private static PositionedGlyph primaryGlyph(CompiledArray compiled, EffectNode root) {
        String name = ArrayNodeCompiler.symbolName(root.primaryElement());
        for (PositionedGlyph glyph : compiled.boundGlyphs()) {
            if (name.equals(glyph.symbolId().getPath())) return glyph;
        }
        return null;
    }

    private static List<PositionedGlyph> directLegacyRunes(CompiledArray compiled, PositionedGlyph center) {
        List<PositionedGlyph> runes = new ArrayList<>();
        for (PositionedGlyph glyph : compiled.boundGlyphs()) {
            if (glyph.glyphUuid().equals(compiled.rootCircleGlyph().glyphUuid())) continue;
            if (glyph.glyphUuid().equals(center.glyphUuid())) continue;
            if ("circle_outer".equals(glyph.symbolId().getPath())) continue;
            runes.add(glyph);
        }
        return runes;
    }
}
