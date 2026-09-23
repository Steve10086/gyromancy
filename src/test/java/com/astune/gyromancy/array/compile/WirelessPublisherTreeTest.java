package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.compile.operator.WirelessOp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class WirelessPublisherTreeTest {
    @Test
    void publisherSubtreeWithUnmatchedNestedGroupStillCompiles() {
        // Mirrors a live publisher AST: the source circle contains a loop
        // group whose extra arrow and nested circle have no executable
        // operator. Those groups must stay raw so the publisher can register.
        GroupNode ast = group(circle(467),
                rune("space", 426),
                rune("secret_text_8", 459),
                rune("secret_text_2", 460),
                rune("secret_text_1", 461),
                group(circle(463),
                        rune("revert", 428),
                        rune("arrow", 462),
                        group(circle(464),
                                rune("loop", 427),
                                rune("arrow", 458),
                                group(circle(466),
                                        rune("arrow", 429),
                                        rune("arrow", 430))),
                        group(circle(465), rune("space", 425))));

        CompileResult<CompiledArray> result = ArrayNodeCompiler.compile(ast);
        CompileResult.Success<CompiledArray> success =
                assertInstanceOf(CompileResult.Success.class, result);
        WirelessOp root = assertInstanceOf(WirelessOp.class, success.value().root());
        assertEquals(true, root.publishesSource());
        assertEquals("wireless:1:2:8", root.key());
    }

    private static GroupNode group(PositionedGlyph circle, ArrayNode... children) {
        return new GroupNode(circle, new SequenceNode(List.of(children)));
    }

    private static SymbolNode rune(String name, int id) {
        return new SymbolNode(glyph(name, SymbolRole.PARAMETER_RUNE, id));
    }

    private static PositionedGlyph circle(int id) {
        return glyph("circle_outer", SymbolRole.OUTER_CIRCLE, id);
    }

    private static PositionedGlyph glyph(String name, SymbolRole role, int id) {
        BlockPos pos = new BlockPos(0, 64, 0);
        return new PositionedGlyph(
                UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", id)),
                id,
                ResourceLocation.fromNamespaceAndPath("gyromancy", name),
                0.9f,
                role,
                new Vec3(1.0, 0.0, 0.0),
                2.0,
                1.0,
                pos,
                0.0, 4.0, 0.0, 4.0,
                Set.of(new PixelPos(pos, Direction.NORTH, id, id, 0xFF00AA00)));
    }
}
