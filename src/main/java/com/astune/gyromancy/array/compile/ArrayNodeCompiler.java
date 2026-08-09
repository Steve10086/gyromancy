package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.compile.operator.CompiledOp;

import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class ArrayNodeCompiler {
    private ArrayNodeCompiler() {}

    //TODO: cleanup after acceptance: remove old CompiledArrayNode/EffectNode/ArrayScript classes and this migration note.
    public static CompileResult<CompiledArray> compile(GroupNode ast) {
        return compile(ast, OpDefinitionRegistry.definitions());
    }

    public static CompileResult<CompiledArray> compile(GroupNode ast, Collection<? extends OpDefinition> effects) {
        CompileResult<CompiledOp> root = compileGroup(ast, effects);
        if (root instanceof CompileResult.Failure<CompiledOp> failure) {
            return new CompileResult.Failure<>(failure.diagnostics());
        }

        CompiledOp op = ((CompileResult.Success<CompiledOp>) root).value();
        return new CompileResult.Success<>(new CompiledArray(
                op,
                ast.boundary(),
                boundGlyphs(op),
                op.color()));
    }

    private static CompileResult<CompiledOp> compileGroup(GroupNode group, Collection<? extends OpDefinition> effects) {
        if (!(group.body() instanceof SequenceNode sequence)) {
            return fail("missing_primary_element", "Group has no sequence body");
        }

        List<OpInput> inputs = new ArrayList<>();

        for (ArrayNode child : sequence.children()) {
            if (child instanceof SymbolNode symbol) {
                inputs.add(new OpInput.Rune(symbol.glyph()));
            } else if (child instanceof GroupNode nested) {
                CompileResult<CompiledOp> compiled = compileGroup(nested, effects);
                if (compiled instanceof CompileResult.Success<CompiledOp> success) {
                    inputs.add(new OpInput.Op(success.value()));
                } else if (compiled instanceof CompileResult.Failure<CompiledOp> failure) {
                    // Preserve the complete failed subtree as a passive
                    // input. Operators must explicitly accept RawGroup in
                    // their accepted list before it can reach compile().
                    inputs.add(new OpInput.RawGroup(nested, failure.diagnostics()));
                }
            }
        }

        return createOp(group.boundary(), List.copyOf(inputs), effects);
    }

    private static CompileResult<CompiledOp> createOp(PositionedGlyph boundary, List<OpInput> inputs,
                                                    Collection<? extends OpDefinition> effects) {
        Match best = bestMatch(inputs, effects);
        if (best == null) {
            return fail("missing_primary_element", "Local direct inputs did not match an operator");
        }
        return best.effect().compile(boundary, List.copyOf(best.inputs()), inputs);
    }

    private static Match bestMatch(List<OpInput> inputs, Collection<? extends OpDefinition> effects) {
        Match best = null;
        for (OpDefinition effect : effects) {
            InputMatch matched = matchedInputs(inputs, effect);
            if (matched.inputs().size() != effect.match().size()) continue;
            if (!acceptsAllUnmatchedInputs(inputs, matched.used(), effect.accepted())) continue;
            if (best == null || effect.match().size() > best.effect().match().size()) {
                best = new Match(effect, matched.inputs());
            }
        }
        return best;
    }

    private static InputMatch matchedInputs(List<OpInput> inputs, OpDefinition effect) {
        List<OpInput> matched = new ArrayList<>();
        Set<Integer> used = new LinkedHashSet<>();
        for (OpInputMatcher matcher : effect.match()) {
            int index = firstMatch(inputs, matcher, used);
            if (index < 0) return new InputMatch(List.copyOf(matched), Set.copyOf(used));
            used.add(index);
            matched.add(inputs.get(index));
        }
        return new InputMatch(List.copyOf(matched), Set.copyOf(used));
    }

    private static boolean acceptsAllUnmatchedInputs(List<OpInput> inputs, Set<Integer> used,
                                                     List<OpInputMatcher> accepted) {
        for (int i = 0; i < inputs.size(); i++) {
            if (used.contains(i)) continue;
            OpInput input = inputs.get(i);
            if (accepted.stream().noneMatch(matcher -> matcher.matches(input))) return false;
        }
        return true;
    }

    private static int firstMatch(List<OpInput> inputs, OpInputMatcher matcher, Set<Integer> used) {
        for (int i = 0; i < inputs.size(); i++) {
            if (!used.contains(i) && matcher.matches(inputs.get(i))) return i;
        }
        return -1;
    }

    private static List<PositionedGlyph> boundGlyphs(CompiledOp root) {
        Set<PositionedGlyph> glyphs = new LinkedHashSet<>();
        collectBoundGlyphs(root, glyphs);
        return List.copyOf(glyphs);
    }

    private static void collectBoundGlyphs(CompiledOp op, Set<PositionedGlyph> glyphs) {
        glyphs.add(op.boundary());
        for (OpInput input : op.inputs()) {
            if (input instanceof OpInput.Rune rune) {
                glyphs.add(rune.glyph());
            } else if (input instanceof OpInput.Op child) {
                collectBoundGlyphs(child.operator(), glyphs);
            } else if (input instanceof OpInput.RawGroup raw) {
                glyphs.addAll(ArrayAstBuilder.boundGlyphs(raw.group()));
            }
        }
    }

    private static <T> CompileResult<T> fail(String code, String message) {
        return new CompileResult.Failure<>(List.of(new CompileDiagnostic(code, message)));
    }

    private record InputMatch(List<OpInput> inputs, Set<Integer> used) {}

    private record Match(OpDefinition effect, List<OpInput> inputs) {}
}
