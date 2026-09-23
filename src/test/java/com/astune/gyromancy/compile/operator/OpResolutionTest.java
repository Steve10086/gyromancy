package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.CompiledArray;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.GroupNode;
import com.astune.gyromancy.array.compile.LocalCompileContext;
import com.astune.gyromancy.array.compile.LocalCompileResult;
import com.astune.gyromancy.array.compile.LocalCompiler;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.SourceFrameRef;
import com.astune.gyromancy.array.compile.SequenceNode;
import com.astune.gyromancy.array.compile.StaticResolveContext;
import com.astune.gyromancy.array.compile.StructureResolution;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class OpResolutionTest {
    @Test
    void ordinaryOperatorWithoutLocalRuleIsKept() {
        StubOp operator = new StubOp("ordinary", glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1));

        LocalCompileResult result = OpResolver.localCompile(operator,
                new LocalCompileContext(new LocalCompiler()));

        LocalCompileResult.Success success = assertInstanceOf(LocalCompileResult.Success.class, result);
        assertSame(operator, success.operator());
    }

    @Test
    void localRuleReturnsAReplacementInstance() {
        LocalStub symbolic = new LocalStub(glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 2));
        CompiledArray model = new CompiledArray(symbolic, symbolic.boundary(), List.of(), 0);

        CompileResult<com.astune.gyromancy.array.compile.RuntimeModel> result =
                new LocalCompiler().compile(model);

        CompileResult.Success<com.astune.gyromancy.array.compile.RuntimeModel> success =
                assertInstanceOf(CompileResult.Success.class, result);
        CompiledOp materialized = success.value().root();
        assertNotSame(symbolic, materialized);
    }

    @Test
    void dynamicStructureIsReplacedBeforeTheParentReceivesIt() {
        PositionedGlyph outer = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 3);
        DynamicStub dynamic = new DynamicStub(outer);

        // The helper is intentionally exercised through a direct symbolic root
        // definition in StaticCompilerTest; this test verifies the resolution
        // carrier itself remains structure-only.
        StructureResolution.Found found = (StructureResolution.Found)
                dynamic.resolveStructure(new StaticResolveContext(
                        null, null, List.of(), outer, List.of()));
        assertSame(found.finalGroup().boundary(), found.frame().rootGlyph());
    }

    private static PositionedGlyph glyph(String name, SymbolRole role, int id) {
        BlockPos pos = new BlockPos(0, 64, 0);
        return new PositionedGlyph(
                UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", id)),
                id, ResourceLocation.fromNamespaceAndPath("gyromancy", name), 0.9F, role,
                new Vec3(1.0, 0.0, 0.0), 2.0, 1.0, pos,
                0.0, 4.0, 0.0, 4.0,
                Set.of(new PixelPos(pos, Direction.NORTH, id, id, 0xFF00AA00)));
    }

    private static class StubOp implements CompiledOp {
        private final ResourceLocation id;
        private final PositionedGlyph boundary;

        private StubOp(String id, PositionedGlyph boundary) {
            this.id = ResourceLocation.fromNamespaceAndPath("gyromancy", id);
            this.boundary = boundary;
        }

        @Override public ResourceLocation id() { return id; }
        @Override public PositionedGlyph boundary() { return boundary; }
        @Override public List<OpInput> inputs() { return List.of(); }
        @Override public int color() { return 0; }
    }

    private static final class LocalStub extends StubOp implements LocalCompilable {
        private LocalStub(PositionedGlyph boundary) {
            super("local_stub", boundary);
        }

        @Override
        public LocalCompileResult localCompile(LocalCompileContext context) {
            return LocalCompileResult.success(new StubOp("materialized_stub", boundary()));
        }
    }

    private static final class DynamicStub extends StubOp implements DynamicStructure {
        private DynamicStub(PositionedGlyph boundary) {
            super("dynamic_stub", boundary);
        }

        @Override
        public StructureResolution resolveStructure(StaticResolveContext context) {
            GroupNode group = new GroupNode(boundary(), new SequenceNode(List.of()));
            return new StructureResolution.Found(group, SourceFrameRef.fromGroup(group), Set.of());
        }
    }

}
