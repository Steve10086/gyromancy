package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.compile.operator.CompiledOp;
import com.astune.gyromancy.compile.operator.DynamicStructure;
import com.astune.gyromancy.compile.operator.ShapeOp;
import com.astune.gyromancy.compile.operator.WindFieldOp;
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

class StaticCompilerTest {
    @Test
    void replacesDynamicRootBeforeRuntimeCompilation() {
        PositionedGlyph outer = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph dynamic = glyph("dynamic", SymbolRole.PARAMETER_RUNE, 2);
        PositionedGlyph source = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 3);
        PositionedGlyph leaf = glyph("leaf", SymbolRole.PARAMETER_RUNE, 4);
        GroupNode sourceGroup = group(source, new SymbolNode(leaf));

        OpDefinition dynamicDefinition = definition("dynamic", DynamicStub::new);
        OpDefinition leafDefinition = definition("leaf", LeafStub::new);
        GroupNode ast = group(outer, new SymbolNode(dynamic));

        CompileResult<CompiledArray> result = new StaticCompiler(
                List.of(dynamicDefinition, leafDefinition)).compile(ast);

        CompileResult.Success<CompiledArray> success = assertInstanceOf(
                CompileResult.Success.class, result);
        CompiledArray compiled = success.value();
        assertInstanceOf(LeafStub.class, compiled.root());
        assertEquals(Set.of("test:dependency"), compiled.wirelessDependencyKeys());

        // Keep the source tree in the test's dynamic symbol so the resolver
        // proves that the replacement came from the final authored group.
        assertEquals(sourceGroup.boundary(), ((LeafStub) compiled.root()).boundary());
    }

    @Test
    void localCompilerMaterializesFieldShapeThroughTheParent() {
        PositionedGlyph outer = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 10);
        PositionedGlyph wind = glyph("wind", SymbolRole.CENTER_SYMBOL, 11);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 12);
        PositionedGlyph fix = glyph("fix", SymbolRole.PARAMETER_RUNE, 13);
        PositionedGlyph split = glyph("split", SymbolRole.PARAMETER_RUNE, 14);
        GroupNode ast = group(outer, new SymbolNode(wind), new SymbolNode(arrow),
                group(glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 15),
                        new SymbolNode(fix), new SymbolNode(split)));

        CompileResult<CompiledArray> staticResult = new StaticCompiler(
                OpDefinitionRegistry.definitions()).compile(ast);
        CompileResult.Success<CompiledArray> staticSuccess = assertInstanceOf(
                CompileResult.Success.class, staticResult);
        CompiledArray staticModel = staticSuccess.value();
        CompileResult.Success<RuntimeModel> runtimeSuccess = assertInstanceOf(
                CompileResult.Success.class, new LocalCompiler().compile(staticModel));
        RuntimeModel runtime = runtimeSuccess.value();

        WindFieldOp root = assertInstanceOf(WindFieldOp.class, runtime.root());
        ShapeOp shape = assertInstanceOf(ShapeOp.class,
                assertInstanceOf(OpInput.Op.class, root.inputs().get(2)).operator());
        assertEquals(true, shape.materializedShape().isPresent());
    }

    private static OpDefinition definition(
            String name, java.util.function.BiFunction<PositionedGlyph, List<OpInput>, CompiledOp> factory) {
        return new OpDefinition() {
            @Override
            public ResourceLocation id() {
                return ResourceLocation.fromNamespaceAndPath("gyromancy", name + "_test");
            }

            @Override
            public List<OpInputMatcher> match() {
                return List.of(OpInputMatcher.rune(name));
            }

            @Override
            public CompileResult<CompiledOp> compile(
                    PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs) {
                return new CompileResult.Success<>(factory.apply(boundary, inputs));
            }
        };
    }

    private static GroupNode group(PositionedGlyph boundary, ArrayNode... children) {
        return new GroupNode(boundary, new SequenceNode(List.of(children)));
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
                0.0,
                4.0,
                0.0,
                4.0,
                Set.of(new PixelPos(pos, Direction.NORTH, id, id, 0xFF00AA00)));
    }

    private static final class LeafStub implements CompiledOp {
        private final PositionedGlyph boundary;
        private final List<OpInput> inputs;

        private LeafStub(PositionedGlyph boundary, List<OpInput> inputs) {
            this.boundary = boundary;
            this.inputs = List.copyOf(inputs);
        }

        @Override public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath("gyromancy", "leaf");
        }
        @Override public PositionedGlyph boundary() { return boundary; }
        @Override public List<OpInput> inputs() { return inputs; }
        @Override public int color() { return 0; }
    }

    private static final class DynamicStub implements CompiledOp, DynamicStructure {
        private final PositionedGlyph boundary;
        private final List<OpInput> inputs;

        private DynamicStub(PositionedGlyph boundary, List<OpInput> inputs) {
            this.boundary = boundary;
            this.inputs = List.copyOf(inputs);
        }

        @Override public StructureResolution resolveStructure(StaticResolveContext context) {
            PositionedGlyph source = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 3);
            PositionedGlyph leaf = glyph("leaf", SymbolRole.PARAMETER_RUNE, 4);
            return new StructureResolution.Found(
                    group(source, new SymbolNode(leaf)),
                    SourceFrameRef.fromGroup(group(source, new SymbolNode(leaf))),
                    Set.of("test:dependency"));
        }

        @Override public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath("gyromancy", "dynamic");
        }
        @Override public PositionedGlyph boundary() { return boundary; }
        @Override public List<OpInput> inputs() { return inputs; }
        @Override public int color() { return 0; }
    }
}
