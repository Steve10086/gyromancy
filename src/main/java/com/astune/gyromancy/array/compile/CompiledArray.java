package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.compile.operator.CompiledOp;

import java.util.List;

public record CompiledArray(
        CompiledOp root,
        PositionedGlyph rootCircleGlyph,
        List<PositionedGlyph> boundGlyphs,
        int color
) {}
