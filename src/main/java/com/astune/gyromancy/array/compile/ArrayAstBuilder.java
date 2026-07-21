package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.array.MagicArrayManager;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;

import java.util.ArrayList;
import java.util.List;

public final class ArrayAstBuilder {
    private ArrayAstBuilder() {}

    public static GroupNode build(PositionedGlyph circleGlyph, MagicArrayManager manager) {
        return new GroupNode(circleGlyph, buildBody(circleGlyph, manager));
    }

    public static List<PositionedGlyph> boundGlyphs(ArrayNode node) {
        List<PositionedGlyph> glyphs = new ArrayList<>();
        collect(node, glyphs);
        return List.copyOf(glyphs);
    }

    private static ArrayNode buildBody(PositionedGlyph circleGlyph, MagicArrayManager manager) {
        List<ArrayNode> children = new ArrayList<>();
        for (PositionedGlyph child : manager.directChildren(circleGlyph)) {
            children.add(child.role() == SymbolRole.OUTER_CIRCLE
                    ? new GroupNode(child, buildBody(child, manager))
                    : new SymbolNode(child));
        }
        return new SequenceNode(List.copyOf(children));
    }

    private static void collect(ArrayNode node, List<PositionedGlyph> glyphs) {
        switch (node) {
            case SymbolNode symbol -> glyphs.add(symbol.glyph());
            case GroupNode group -> {
                glyphs.add(group.boundary());
                collect(group.body(), glyphs);
            }
            case SequenceNode sequence -> sequence.children().forEach(child -> collect(child, glyphs));
            case ApplyNode apply -> {
                collect(apply.operator(), glyphs);
                collect(apply.target(), glyphs);
            }
        }
    }
}
