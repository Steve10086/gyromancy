package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.ArrayNodeCompiler;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.CompiledArray;
import com.astune.gyromancy.array.compile.GroupNode;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.SequenceNode;
import com.astune.gyromancy.array.compile.SymbolNode;
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

class OnDiscardOpTest {
    @Test
    void crossAcceptsEntityEffectsAsDiscardContent() {
        PositionedGlyph rootCircle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph rootFire = glyph("fire", SymbolRole.CENTER_SYMBOL, 2);
        PositionedGlyph discardCircle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 3);
        PositionedGlyph cross = glyph("cross", SymbolRole.PARAMETER_RUNE, 4);
        PositionedGlyph childCircle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 5);
        PositionedGlyph childFire = glyph("fire", SymbolRole.CENTER_SYMBOL, 6);

        GroupNode childEffect = group(childCircle, new SymbolNode(childFire));
        GroupNode onDiscard = group(discardCircle, new SymbolNode(cross), childEffect);
        GroupNode ast = group(rootCircle, new SymbolNode(rootFire), onDiscard);

        CompileResult<CompiledArray> result = ArrayNodeCompiler.compile(ast);
        @SuppressWarnings("unchecked")
        CompileResult.Success<CompiledArray> success =
                (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class, result);
        CompiledArray compiled = success.value();
        FireProjectileOp root = assertInstanceOf(FireProjectileOp.class, compiled.root());
        OnDiscardOp op = root.inputs().stream()
                .filter(OpInput.Op.class::isInstance)
                .map(OpInput.Op.class::cast)
                .map(OpInput.Op::operator)
                .filter(OnDiscardOp.class::isInstance)
                .map(OnDiscardOp.class::cast)
                .findFirst()
                .orElseThrow();

        assertEquals(1, op.effects().size());
        assertInstanceOf(FireProjectileOp.class, op.effects().getFirst());
        OnDiscardPayload payload = assertInstanceOf(OnDiscardPayload.class, root.payload(List.of()).stream()
                .filter(OnDiscardPayload.class::isInstance)
                .findFirst()
                .orElseThrow());
        assertEquals(op.effects(), payload.content().effects());
    }

    private static GroupNode group(PositionedGlyph boundary, com.astune.gyromancy.array.compile.ArrayNode... children) {
        return new GroupNode(boundary, new SequenceNode(List.of(children)));
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
                0.0,
                2.0,
                0.0,
                1.0,
                Set.of(new PixelPos(pos, Direction.NORTH, id, id, 0xFF00AA00))
        );
    }
}
