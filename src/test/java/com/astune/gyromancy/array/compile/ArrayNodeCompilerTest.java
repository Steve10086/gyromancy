package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.compile.operator.FireballOp;
import com.astune.gyromancy.compile.operator.Operator;
import com.astune.gyromancy.compile.operator.ProjectileOp;
import com.astune.gyromancy.array.runtime.RuntimeHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
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
        ProjectileOp root = assertInstanceOf(FireballOp.class, success.value().root());

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

        assertInstanceOf(FireballOp.class, success.value().root());
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

        assertInstanceOf(FireballOp.class, success.value().root());
    }

    @Test
    void longestMatchingDefinitionReceivesMatchedInputsAndFullInputs() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 2);
        PositionedGlyph fix = glyph("fix", SymbolRole.PARAMETER_RUNE, 3);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 4);
        ArrayEffectDefinition shortOp = runeOp("short", "fire");
        ArrayEffectDefinition longOp = runeOp("long", "fire", "fix");
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
        ArrayEffectDefinition shortOp = runeOp("short", "fire");
        ArrayEffectDefinition partialLongOp = runeOp("partial_long", "fire", "fix");
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
        ArrayEffectDefinition innerOp = runeOp("inner", "water");
        ArrayEffectDefinition outerOp = runeAndChildOp("outer", "fire", DummyOperator.class);
        GroupNode ast = group(outer, new SymbolNode(fire), group(inner, new SymbolNode(water)));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast, List.of(innerOp, outerOp)));
        DummyOperator root = assertInstanceOf(DummyOperator.class, success.value().root());

        assertEquals("outer", root.id().getPath());
        assertEquals(2, root.matchedInputs().size());
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

    private static ArrayEffectDefinition runeOp(String id, String... symbols) {
        List<OpInputMatcher> matchers = List.of(symbols).stream()
                .map(OpInputMatcher::rune)
                .toList();
        return op(id, matchers);
    }

    @SafeVarargs
    private static ArrayEffectDefinition runeAndChildOp(String id, String firstSymbol,
                                                        Class<? extends Operator>... opTypes) {
        List<OpInputMatcher> matchers = new java.util.ArrayList<>();
        matchers.add(OpInputMatcher.rune(firstSymbol));
        for (Class<? extends Operator> opType : opTypes) matchers.add(OpInputMatcher.op(opType));
        return op(id, List.copyOf(matchers));
    }

    private static ArrayEffectDefinition op(String id, List<OpInputMatcher> matchers) {
        return new ArrayEffectDefinition() {
            @Override
            public ResourceLocation id() {
                return ResourceLocation.fromNamespaceAndPath("gyromancy", id);
            }

            @Override
            public List<OpInputMatcher> match() {
                return matchers;
            }

            @Override
            public CompileResult<Operator> compile(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                                   List<OpInput> inputs) {
                return new CompileResult.Success<>(new DummyOperator(id, boundary, matchedInputs, inputs));
            }
        };
    }

    private record DummyOperator(String name, PositionedGlyph boundary, List<OpInput> matchedInputs,
                                 List<OpInput> inputs) implements Operator {
        @Override
        public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath("gyromancy", name);
        }

        @Override
        public RuntimeHandle activate(ServerLevel level) {
            return new RuntimeHandle(Map.of());
        }

        @Override
        public void deactivate(ServerLevel level, Map<String, Object> scratchData) {
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
