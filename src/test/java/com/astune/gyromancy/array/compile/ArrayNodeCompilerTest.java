package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
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

class ArrayNodeCompilerTest {
    @Test
    void compilesProjectileNodeFromPrimaryElementAndAttributes() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 2);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 3);
        PositionedGlyph revert = glyph("revert", SymbolRole.PARAMETER_RUNE, 4);

        GroupNode ast = new GroupNode(circle, new SequenceNode(List.of(
                new SymbolNode(fire), new SymbolNode(arrow), new SymbolNode(revert))));

        var result = ArrayNodeCompiler.compile(ast);

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class, result);
        CompiledArray compiled = success.value();
        EffectNode root = (EffectNode) compiled.script().root();
        assertInstanceOf(ProjectileRuntimeNode.class, root);
        assertEquals(ElementType.FIRE, root.primaryElement());
        assertEquals(true, root.attributes().inverted());
        assertEquals(List.of(new MotionAttribute(arrow.front(), arrow.length())), root.attributes().motion());
        assertEquals(List.of(circle, fire, arrow, revert), compiled.boundGlyphs());
    }

    private static PositionedGlyph glyph(String name, SymbolRole role, int id) {
        BlockPos pos = new BlockPos(0, 64, 0);
        return new PositionedGlyph(
                UUID.fromString("00000000-0000-0000-0000-00000000000" + id),
                id,
                ResourceLocation.fromNamespaceAndPath("gyromancy", name),
                0.9f,
                role,
                new Vec3(1.0, 0.0, 0.0),
                2.0,
                1.0,
                pos,
                0.0, 4.0, 0.0, 4.0,
                Set.of(new PixelPos(pos, Direction.NORTH, id, id, 0xFF00AA00))
        );
    }
}
