package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.compile.operator.FireProjectileOp;
import com.astune.gyromancy.compile.operator.ElementOp;
import com.astune.gyromancy.compile.operator.CompiledOp;
import com.astune.gyromancy.compile.operator.EntityEffectOp;
import com.astune.gyromancy.compile.operator.PersistentOp;
import com.astune.gyromancy.compile.operator.SplitEmitOp;
import com.astune.gyromancy.compile.operator.WaterProjectileOp;
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
    void compilesProjectileOperatorFromRawRuneInputs() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 2);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 3);
        PositionedGlyph revert = glyph("revert", SymbolRole.PARAMETER_RUNE, 4);
        GroupNode ast = group(circle, new SymbolNode(fire), new SymbolNode(arrow), new SymbolNode(revert));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        EntityEffectOp root = assertInstanceOf(FireProjectileOp.class, success.value().root());

        assertEquals(ElementType.FIRE, root.primaryElement());
        assertEquals(true, root.attributes().inverted());
        assertEquals(List.of(new MotionAttribute(arrow.front(), arrow.length())), root.attributes().motion());
        assertEquals(List.of("fire", "arrow", "revert"), runeNames(root.inputs()));
        assertEquals(List.of("fire"), runeNames(root.matchedInputs()));
        assertEquals(List.of(circle, fire, arrow, revert), success.value().boundGlyphs());
    }

    @Test
    void primaryRuneDoesNotNeedCenterRole() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph fire = glyph("fire", SymbolRole.PARAMETER_RUNE, 2);
        GroupNode ast = group(circle, new SymbolNode(fire));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));

        assertInstanceOf(FireProjectileOp.class, success.value().root());
        assertEquals(List.of("fire"), runeNames(success.value().root().inputs()));
    }

    @Test
    void emptyCircleFailsWithoutOperator() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        GroupNode ast = group(circle);

        @SuppressWarnings("unchecked")
        var failure = (CompileResult.Failure<CompiledArray>) assertInstanceOf(CompileResult.Failure.class,
                ArrayNodeCompiler.compile(ast));

        assertEquals("missing_primary_element", failure.diagnostics().getFirst().code());
    }

    @Test
    void modifierOnlyCircleFailsWithoutOperator() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 2);
        GroupNode ast = group(circle, new SymbolNode(arrow));

        @SuppressWarnings("unchecked")
        var failure = (CompileResult.Failure<CompiledArray>) assertInstanceOf(CompileResult.Failure.class,
                ArrayNodeCompiler.compile(ast));

        assertEquals("missing_primary_element", failure.diagnostics().getFirst().code());
    }

    @Test
    void extraPrimaryRuneDoesNotCreateCompilerAmbiguity() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 2);
        PositionedGlyph water = glyph("water", SymbolRole.CENTER_SYMBOL, 3);
        GroupNode ast = group(circle, new SymbolNode(fire), new SymbolNode(water));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));

        assertInstanceOf(FireProjectileOp.class, success.value().root());
    }

    @Test
    void longestMatchingDefinitionReceivesMatchedInputsAndFullInputs() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 2);
        PositionedGlyph fix = glyph("fix", SymbolRole.PARAMETER_RUNE, 3);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 4);
        OpDefinition shortOp = runeOp("short", "fire");
        OpDefinition longOp = runeOp("long", "fire", "fix");
        GroupNode ast = group(circle, new SymbolNode(fire), new SymbolNode(fix), new SymbolNode(arrow));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast, List.of(shortOp, longOp)));
        DummyOperator root = assertInstanceOf(DummyOperator.class, success.value().root());

        assertEquals("long", root.id().getPath());
        assertEquals(List.of("fire", "fix"), runeNames(root.matchedInputs()));
        assertEquals(List.of("fire", "fix", "arrow"), runeNames(root.inputs()));
    }

    @Test
    void partialLongerDefinitionDoesNotBeatFullShortDefinition() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 2);
        OpDefinition shortOp = runeOp("short", "fire");
        OpDefinition partialLongOp = runeOp("partial_long", "fire", "fix");
        GroupNode ast = group(circle, new SymbolNode(fire));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast, List.of(partialLongOp, shortOp)));
        DummyOperator root = assertInstanceOf(DummyOperator.class, success.value().root());

        assertEquals("short", root.id().getPath());
        assertEquals(List.of("fire"), runeNames(root.matchedInputs()));
    }

    @Test
    void nestedGroupCompilesToChildOpAndParentCanMatchIt() {
        PositionedGlyph outer = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph inner = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 2);
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 3);
        PositionedGlyph water = glyph("water", SymbolRole.CENTER_SYMBOL, 4);
        OpDefinition innerOp = runeOp("inner", "water");
        OpDefinition outerOp = runeAndChildOp("outer", "fire", DummyOperator.class);
        GroupNode ast = group(outer, new SymbolNode(fire), group(inner, new SymbolNode(water)));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast, List.of(innerOp, outerOp)));
        DummyOperator root = assertInstanceOf(DummyOperator.class, success.value().root());

        assertEquals("outer", root.id().getPath());
        assertEquals(2, root.matchedInputs().size());
    }

    @Test
    void engagingRuneCompilesStandaloneElementOp() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph engaging = glyph("engaging", SymbolRole.PARAMETER_RUNE, 2);
        GroupNode ast = group(circle, new SymbolNode(engaging));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));

        assertInstanceOf(ElementOp.class, success.value().root());
    }

    @Test
    void defaultCompilerUsesDistributedRegistryDefinitions() {
        OpDefinition registered = runeOp("registered_star", "star");
        OpDefinitionRegistry.register(registered);
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph star = glyph("star", SymbolRole.PARAMETER_RUNE, 2);
        GroupNode ast = group(circle, new SymbolNode(star));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        DummyOperator root = assertInstanceOf(DummyOperator.class, success.value().root());

        assertEquals("registered_star", root.id().getPath());
    }

    @Test
    void projectileCanOwnNestedElementPayloadOp() {
        PositionedGlyph outer = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph inner = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 2);
        PositionedGlyph water = glyph("water", SymbolRole.CENTER_SYMBOL, 3);
        PositionedGlyph engaging = glyph("engaging", SymbolRole.PARAMETER_RUNE, 4);
        GroupNode ast = group(outer, new SymbolNode(water), group(inner, new SymbolNode(engaging)));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        WaterProjectileOp root = assertInstanceOf(WaterProjectileOp.class, success.value().root());
        CompiledOp child = assertInstanceOf(OpInput.Op.class, root.inputs().get(1)).operator();

        assertInstanceOf(ElementOp.class, child);
    }

    @Test
    void splitEmitCompilesButIsNotPersistentRoot() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph split = glyph("split", SymbolRole.PARAMETER_RUNE, 2);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 3);
        GroupNode ast = group(circle, new SymbolNode(split), new SymbolNode(arrow));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));

        assertInstanceOf(SplitEmitOp.class, success.value().root());
        assertEquals(false, success.value().root() instanceof PersistentOp);
    }

    @Test
    void splitEmitPlansOneEmissionPerDirectArrow() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph split = glyph("split", SymbolRole.PARAMETER_RUNE, 2);
        PositionedGlyph arrowA = glyph("arrow", SymbolRole.PARAMETER_RUNE, 3);
        PositionedGlyph arrowB = glyph("arrow", SymbolRole.PARAMETER_RUNE, 4);
        GroupNode ast = group(circle, new SymbolNode(split), new SymbolNode(arrowA), new SymbolNode(arrowB));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        SplitEmitOp splitOp = assertInstanceOf(SplitEmitOp.class, success.value().root());

        assertEquals(2, splitOp.emissions().size());
        assertEquals(0.5F, splitOp.emissions().getFirst().sizeScale());
        assertEquals(new Vec3(2.0, 0.0, 0.0), splitOp.emissions().getFirst().velocity());
    }

    @Test
    void splitEmitTreatsArrowUpAsNormalLockedArrow() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph split = glyph("split", SymbolRole.PARAMETER_RUNE, 2);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 3);
        PositionedGlyph arrowUp = glyph("arrow_up", SymbolRole.PARAMETER_RUNE, 4);
        GroupNode ast = group(circle, new SymbolNode(split), new SymbolNode(arrow), new SymbolNode(arrowUp));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        SplitEmitOp splitOp = assertInstanceOf(SplitEmitOp.class, success.value().root());

        assertEquals(2, splitOp.emissions().size());
        assertEquals(new Vec3(2.0, 0.0, 0.0), splitOp.emissions().get(0).velocity());
        assertEquals(new Vec3(0.0, 0.0, -2.0), splitOp.emissions().get(1).velocity());
        assertEquals(0.5F, splitOp.emissions().get(1).sizeScale());
    }

    @Test
    void projectileCanOwnNestedSplitEmitOpWithoutChangingRootType() {
        PositionedGlyph outer = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph inner = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 2);
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 3);
        PositionedGlyph split = glyph("split", SymbolRole.PARAMETER_RUNE, 4);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 5);
        GroupNode ast = group(outer, new SymbolNode(fire), group(inner, new SymbolNode(split), new SymbolNode(arrow)));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        FireProjectileOp root = assertInstanceOf(FireProjectileOp.class, success.value().root());
        CompiledOp child = assertInstanceOf(OpInput.Op.class, root.inputs().get(1)).operator();

        assertInstanceOf(SplitEmitOp.class, child);
    }

    private static GroupNode group(PositionedGlyph circle, ArrayNode... children) {
        return new GroupNode(circle, new SequenceNode(List.of(children)));
    }

    private static List<String> runeNames(List<OpInput> inputs) {
        return inputs.stream()
                .filter(OpInput.Rune.class::isInstance)
                .map(OpInput.Rune.class::cast)
                .map(OpInput.Rune::symbolName)
                .toList();
    }

    private static OpDefinition runeOp(String id, String... symbols) {
        List<OpInputMatcher> matchers = List.of(symbols).stream()
                .map(OpInputMatcher::rune)
                .toList();
        return op(id, matchers);
    }

    @SafeVarargs
    private static OpDefinition runeAndChildOp(String id, String firstSymbol,
                                                        Class<? extends CompiledOp>... opTypes) {
        List<OpInputMatcher> matchers = new java.util.ArrayList<>();
        matchers.add(OpInputMatcher.rune(firstSymbol));
        for (Class<? extends CompiledOp> opType : opTypes) matchers.add(OpInputMatcher.op(opType));
        return op(id, List.copyOf(matchers));
    }

    private static OpDefinition op(String id, List<OpInputMatcher> matchers) {
        return new OpDefinition() {
            @Override
            public ResourceLocation id() {
                return ResourceLocation.fromNamespaceAndPath("gyromancy", id);
            }

            @Override
            public List<OpInputMatcher> match() {
                return matchers;
            }

            @Override
            public CompileResult<CompiledOp> compile(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                                   List<OpInput> inputs) {
                return new CompileResult.Success<>(new DummyOperator(id, boundary, matchedInputs, inputs));
            }
        };
    }

    private record DummyOperator(String name, PositionedGlyph boundary, List<OpInput> matchedInputs,
                                 List<OpInput> inputs) implements CompiledOp {
        @Override
        public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath("gyromancy", name);
        }

        @Override
        public int color() {
            return 0xFFFFFFFF;
        }
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
