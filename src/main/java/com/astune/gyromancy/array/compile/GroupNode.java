package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.symbol.PositionedGlyph;

public record GroupNode(PositionedGlyph boundary, ArrayNode body) implements ArrayNode {}
