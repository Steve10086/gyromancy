package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.element.ManaElements;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.compile.operator.CompiledOp;
import com.astune.gyromancy.compile.operator.MomentumOp;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArrayManaCostTest {

    @Test
    void collectSumsEveryOpInTheTree() {
        StubOp child = new StubOp(List.of());
        StubOp root = new StubOp(List.of(new OpInput.Op(child)));

        ManaElements cost = ArrayManaCost.collect(root);

        assertEquals(20.0, cost.at(ElementType.MANA));
        assertEquals(0.0, cost.at(ElementType.FIRE));
    }

    @Test
    void checkReportsEveryShortElementWithRequirementAndCurrent() {
        StubOp root = new StubOp(List.of());

        List<CompileDiagnostic> diagnostics = ArrayManaCost.check(root, mana(4.0));

        assertEquals(1, diagnostics.size());
        assertEquals("insufficient_element", diagnostics.getFirst().code());
        assertTrue(diagnostics.getFirst().message().contains("mana requires 10"));
        assertTrue(diagnostics.getFirst().message().contains("current 4"));
    }

    @Test
    void checkAcceptsSufficientElements() {
        StubOp root = new StubOp(List.of());

        assertTrue(ArrayManaCost.check(root, mana(10.0)).isEmpty());
        assertTrue(ArrayManaCost.check(root, mana(11.0)).isEmpty());
    }

    @Test
    void customCostIsCollectedPerElement() {
        StubOp root = new StubOp(List.of()) {
            @Override
            public ManaElements getCost() {
                double[] values = new double[ElementType.COUNT];
                values[ElementType.FIRE.ordinal()] = 5.0;
                return new ManaElements(values);
            }
        };

        List<CompileDiagnostic> diagnostics = ArrayManaCost.check(root, ManaElements.EMPTY);

        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.getFirst().message().contains("fire requires 5"));
    }

    @Test
    void stageTwoRejectsAnArrayThatCannotAffordItsCost() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1)
                .withManaElements(mana(4.0));
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 2)
                .withManaElements(mana(4.0));
        GroupNode ast = group(circle, new SymbolNode(fire));

        CompileResult<RuntimeModel> result = new ArrayCompilePipeline(
                OpDefinitionRegistry.definitions()).compile(ast);

        CompileResult.Failure<RuntimeModel> failure =
                assertInstanceOf(CompileResult.Failure.class, result);
        assertEquals(CompileDiagnostic.RUNTIME_ERROR, failure.diagnostics().getFirst().code());
        assertTrue(failure.diagnostics().stream()
                .anyMatch(diagnostic -> "insufficient_element".equals(diagnostic.code())));
    }

    @Test
    void stageTwoAcceptsAnArrayThatCanAffordItsCost() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1)
                .withManaElements(mana(20.0));
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 2)
                .withManaElements(mana(20.0));
        GroupNode ast = group(circle, new SymbolNode(fire));

        CompileResult<RuntimeModel> result = new ArrayCompilePipeline(
                OpDefinitionRegistry.definitions()).compile(ast);

        assertInstanceOf(CompileResult.Success.class, result);
    }

    @Test
    void hiddenVectorOperatorsAreCounted() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph motion = glyph("motion", SymbolRole.PARAMETER_RUNE, 2);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 3);
        GroupNode ast = group(circle, new SymbolNode(motion), new SymbolNode(arrow));

        CompileResult<RuntimeModel> result = new ArrayCompilePipeline(
                OpDefinitionRegistry.definitions()).compile(ast);

        CompileResult.Failure<RuntimeModel> failure =
                assertInstanceOf(CompileResult.Failure.class, result);
        assertTrue(failure.diagnostics().stream()
                        .anyMatch(diagnostic -> "insufficient_element".equals(diagnostic.code())
                                && diagnostic.message().contains("requires 20")),
                "the resolved arrow vector operator must add its own cost");
    }

    @Test
    void nestedVectorCompositionsAreCounted() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph motion = glyph("motion", SymbolRole.PARAMETER_RUNE, 2);
        PositionedGlyph vectorCircle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 3);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 4);
        PositionedGlyph gravityCircle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 5);
        PositionedGlyph space = glyph("space", SymbolRole.PARAMETER_RUNE, 6);
        GroupNode ast = group(circle, new SymbolNode(motion),
                group(vectorCircle, new SymbolNode(arrow),
                        group(gravityCircle, new SymbolNode(space))));

        CompileResult<RuntimeModel> result = new ArrayCompilePipeline(
                OpDefinitionRegistry.definitions()).compile(ast);

        CompileResult.Failure<RuntimeModel> failure =
                assertInstanceOf(CompileResult.Failure.class, result);
        assertTrue(failure.diagnostics().stream()
                        .anyMatch(diagnostic -> "insufficient_element".equals(diagnostic.code())
                                && diagnostic.message().contains("requires 40")),
                "nested vector composition operators must be counted: " + failure.diagnostics());
    }

    @Test
    void momentumVelocityVectorAppearsExactlyOnceInTheFinalTree() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1)
                .withManaElements(mana(100.0));
        PositionedGlyph motion = glyph("motion", SymbolRole.PARAMETER_RUNE, 2);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 3);
        GroupNode ast = group(circle, new SymbolNode(motion), new SymbolNode(arrow));

        CompileResult<RuntimeModel> result = new ArrayCompilePipeline(
                OpDefinitionRegistry.definitions()).compile(ast);
        @SuppressWarnings("unchecked")
        RuntimeModel model = ((CompileResult.Success<RuntimeModel>)
                assertInstanceOf(CompileResult.Success.class, result)).value();
        MomentumOp root = assertInstanceOf(MomentumOp.class, model.root());

        assertEquals(1, root.velocityInputs().size());
        assertEquals(List.of(root.velocityInputs().getFirst().vector()), root.childOps());
    }

    @Test
    void nestedMomentumVelocityOperatorsAreCountedOnce() {
        PositionedGlyph outer = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph inner = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 2);
        PositionedGlyph outerMotion = glyph("motion", SymbolRole.PARAMETER_RUNE, 3);
        PositionedGlyph innerMotion = glyph("motion", SymbolRole.PARAMETER_RUNE, 4);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 5);
        GroupNode ast = group(outer, new SymbolNode(outerMotion),
                group(inner, new SymbolNode(innerMotion), new SymbolNode(arrow)));

        CompileResult<RuntimeModel> result = new ArrayCompilePipeline(
                OpDefinitionRegistry.definitions()).compile(ast);

        CompileResult.Failure<RuntimeModel> failure =
                assertInstanceOf(CompileResult.Failure.class, result);
        assertTrue(failure.diagnostics().stream()
                        .anyMatch(diagnostic -> "insufficient_element".equals(diagnostic.code())
                                && diagnostic.message().contains("requires 30")),
                "nested momentum and its velocity vector must be counted once: "
                        + failure.diagnostics());
    }

    private static ManaElements mana(double mana) {
        double[] values = new double[ElementType.COUNT];
        values[ElementType.MANA.ordinal()] = mana;
        return new ManaElements(values);
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

    private static class StubOp implements CompiledOp {
        private final List<OpInput> inputs;

        private StubOp(List<OpInput> inputs) {
            this.inputs = List.copyOf(inputs);
        }

        @Override public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath("gyromancy", "stub");
        }
        @Override public PositionedGlyph boundary() { return null; }
        @Override public List<OpInput> inputs() { return inputs; }
        @Override public int color() { return 0; }
    }
}
