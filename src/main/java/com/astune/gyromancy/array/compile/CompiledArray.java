package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.symbol.PositionedGlyph;

import java.util.List;

public record CompiledArray(
        ArrayScript script,
        PositionedGlyph rootCircleGlyph,
        List<PositionedGlyph> boundGlyphs,
        int color
) {}
