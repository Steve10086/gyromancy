package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.array.MagicArrayManager;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class ArrayAstBuilder {
    private ArrayAstBuilder() {}

    public static GroupNode build(PositionedGlyph circleGlyph, MagicArrayManager manager) {
        return build(circleGlyph, manager, ignored -> true);
    }

    public static GroupNode build(PositionedGlyph circleGlyph, MagicArrayManager manager,
                                  Predicate<PositionedGlyph> isValidStroke) {
        return new GroupNode(circleGlyph, buildBody(circleGlyph, manager, isValidStroke));
    }

    public static List<PositionedGlyph> boundGlyphs(ArrayNode node) {
        List<PositionedGlyph> glyphs = new ArrayList<>();
        collect(node, glyphs);
        return List.copyOf(glyphs);
    }

    private static ArrayNode buildBody(PositionedGlyph circleGlyph, MagicArrayManager manager,
                                       Predicate<PositionedGlyph> isValidStroke) {
        List<ArrayNode> children = new ArrayList<>();
        for (PositionedGlyph child : manager.directChildren(circleGlyph)) {
            if (!isValidStroke.test(child)) continue;
            children.add(child.role() == SymbolRole.OUTER_CIRCLE
                    ? new GroupNode(child, buildBody(child, manager, isValidStroke))
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
