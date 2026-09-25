package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.element.ManaElements;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.compile.operator.CompiledOp;

import java.util.List;
import java.util.Set;

public record CompiledArray(
        CompiledOp root,
        PositionedGlyph rootCircleGlyph,
        List<PositionedGlyph> boundGlyphs,
        int color,
        Set<String> wirelessDependencyKeys,
        ManaElements manaElements
) {
    public CompiledArray(CompiledOp root,
                         PositionedGlyph rootCircleGlyph,
                         List<PositionedGlyph> boundGlyphs,
                         int color) {
        this(root, rootCircleGlyph, boundGlyphs, color, Set.of());
    }

    public CompiledArray(CompiledOp root,
                         PositionedGlyph rootCircleGlyph,
                         List<PositionedGlyph> boundGlyphs,
                         int color,
                         Set<String> wirelessDependencyKeys) {
        this(root, rootCircleGlyph, boundGlyphs, color, wirelessDependencyKeys,
                ManaElements.EMPTY);
    }

    public CompiledArray {
        boundGlyphs = List.copyOf(boundGlyphs);
        wirelessDependencyKeys = Set.copyOf(wirelessDependencyKeys);
        manaElements = manaElements == null ? ManaElements.EMPTY : manaElements;
    }
}
