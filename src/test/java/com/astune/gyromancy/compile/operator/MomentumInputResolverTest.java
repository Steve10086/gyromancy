package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.ArrayNode;
import com.astune.gyromancy.array.compile.ArrayNodeCompiler;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.CompiledArray;
import com.astune.gyromancy.array.compile.GroupNode;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.SequenceNode;
import com.astune.gyromancy.array.compile.SymbolNode;
import com.astune.gyromancy.array.compile.VectorCompiler;
import com.astune.gyromancy.compile.vector.VectorOp;
import com.astune.gyromancy.compile.vector.VectorOpDefinitions;
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
import static org.junit.jupiter.api.Assertions.assertNotSame;

class MomentumInputResolverTest {
    @Test
    void recompiledVectorReplacesItsSourceOpInTheTree() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 2);
        GroupNode ast = group(circle, new SymbolNode(arrow));

        @SuppressWarnings("unchecked")
        CompiledArray compiled = ((CompileResult.Success<CompiledArray>)
                assertInstanceOf(CompileResult.Success.class,
                        ArrayNodeCompiler.compile(ast, VectorOpDefinitions.definitions()))).value();
        VectorOp source = assertInstanceOf(VectorOp.class, compiled.root());

        MomentumInputResolver resolver = new MomentumInputResolver(
                new VectorCompiler(VectorOpDefinitions.definitions()));
        @SuppressWarnings("unchecked")
        MomentumInputResolver.ResolvedInputs resolved =
                ((CompileResult.Success<MomentumInputResolver.ResolvedInputs>)
                        assertInstanceOf(CompileResult.Success.class,
                                resolver.resolve(circle, List.of(new OpInput.Op(source, ast))))).value();

        assertEquals(1, resolved.velocityInputs().size());
        assertEquals(1, resolved.treeInputs().size());
        OpInput.Op edge = assertInstanceOf(OpInput.Op.class, resolved.treeInputs().getFirst());
        VectorOp replacement = assertInstanceOf(VectorOp.class, edge.operator());
        assertNotSame(source, replacement);
    }

    private static GroupNode group(PositionedGlyph circle, ArrayNode... children) {
        return new GroupNode(circle, new SequenceNode(List.of(children)));
    }

    private static PositionedGlyph glyph(String name, SymbolRole role, int id) {
        BlockPos pos = new BlockPos(0, 64, 0);
        return new PositionedGlyph(
                UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", id)),
                id,
                ResourceLocation.fromNamespaceAndPath("gyromancy", name),
                0.9F,
                role,
                new Vec3(1.0, 0.0, 0.0),
                2.0,
                1.0,
                pos,
                0.0, 4.0, 0.0, 4.0,
                Set.of(new PixelPos(pos, Direction.NORTH, id, id, 0xFF00AA00)));
    }
}
