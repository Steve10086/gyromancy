package com.astune.gyromancy.api.array;

import com.astune.gyromancy.api.symbol.PositionedGlyph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A validated magic array bound to its constituent glyphs.
 *
 * <p>When any bound glyph (circle, center, or rune) is invalidated, the array is
 * destroyed and {@code EndEffect} fires with the scratch data from {@code CenterEffect}.
 */
public record ArrayObject(
        UUID arrayId,
        PositionedGlyph circleGlyph,
        PositionedGlyph centerGlyph,
        List<PositionedGlyph> runeGlyphs,
        Map<String, Object> scratchData
) {
    /** All glyphs bound to this array (circle + center + runes). */
    public List<PositionedGlyph> allBoundGlyphs() {
        List<PositionedGlyph> all = new ArrayList<>(1 + 1 + runeGlyphs.size());
        all.add(circleGlyph);
        all.add(centerGlyph);
        all.addAll(runeGlyphs);
        return all;
    }
}
