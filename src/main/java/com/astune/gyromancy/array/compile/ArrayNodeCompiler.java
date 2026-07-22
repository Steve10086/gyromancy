package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.compile.operator.Operator;

import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class ArrayNodeCompiler {
    private ArrayNodeCompiler() {}

    //TODO: cleanup after acceptance: remove old CompiledArrayNode/EffectNode/ArrayScript classes and this migration note.
    public static CompileResult<CompiledArray> compile(GroupNode ast) {
        return compile(ast, ArrayEffectRegistry.effects());
    }

    public static CompileResult<CompiledArray> compile(GroupNode ast, Collection<ArrayEffectDefinition> effects) {
        CompileResult<Operator> root = compileGroup(ast, effects);
        if (root instanceof CompileResult.Failure<Operator> failure) {
            return new CompileResult.Failure<>(failure.diagnostics());
        }

        Operator op = ((CompileResult.Success<Operator>) root).value();
        return new CompileResult.Success<>(new CompiledArray(
                op,
                ast.boundary(),
                boundGlyphs(op),
                op.color()));
    }

    private static CompileResult<Operator> compileGroup(GroupNode group, Collection<ArrayEffectDefinition> effects) {
        if (!(group.body() instanceof SequenceNode sequence)) {
            return fail("missing_primary_element", "Group has no sequence body");
        }

        List<OpInput> inputs = new ArrayList<>();
        List<CompileDiagnostic> diagnostics = new ArrayList<>();

        for (ArrayNode child : sequence.children()) {
            if (child instanceof SymbolNode symbol) {
                inputs.add(new OpInput.Rune(symbol.glyph()));
            } else if (child instanceof GroupNode nested) {
                CompileResult<Operator> compiled = compileGroup(nested, effects);
                if (compiled instanceof CompileResult.Success<Operator> success) {
                    inputs.add(new OpInput.Op(success.value()));
                } else if (compiled instanceof CompileResult.Failure<Operator> failure) {
                    diagnostics.addAll(failure.diagnostics());
                }
            }
        }
        if (!diagnostics.isEmpty()) return new CompileResult.Failure<>(diagnostics);

        return createOp(group.boundary(), List.copyOf(inputs), effects);
    }

    private static CompileResult<Operator> createOp(PositionedGlyph boundary, List<OpInput> inputs,
                                                    Collection<ArrayEffectDefinition> effects) {
        Match best = bestMatch(inputs, effects);
        if (best == null) {
            return fail("missing_primary_element", "Local direct inputs did not match an operator");
        }
        return best.effect().compile(boundary, List.copyOf(best.inputs()), inputs);
    }

    private static Match bestMatch(List<OpInput> inputs, Collection<ArrayEffectDefinition> effects) {
        Match best = null;
        for (ArrayEffectDefinition effect : effects) {
            List<OpInput> matched = matchedInputs(inputs, effect);
            if (matched.size() != effect.match().size()) continue;
            if (best == null || effect.match().size() > best.effect().match().size()) {
                best = new Match(effect, matched);
            }
        }
        return best;
    }

    private static List<OpInput> matchedInputs(List<OpInput> inputs, ArrayEffectDefinition effect) {
        List<OpInput> matched = new ArrayList<>();
        Set<Integer> used = new LinkedHashSet<>();
        for (OpInputMatcher matcher : effect.match()) {
            int index = firstMatch(inputs, matcher, used);
            if (index < 0) return matched;
            used.add(index);
            matched.add(inputs.get(index));
        }
        return matched;
    }

    private static int firstMatch(List<OpInput> inputs, OpInputMatcher matcher, Set<Integer> used) {
        for (int i = 0; i < inputs.size(); i++) {
            if (!used.contains(i) && matcher.matches(inputs.get(i))) return i;
        }
        return -1;
    }

    private static List<PositionedGlyph> boundGlyphs(Operator root) {
        Set<PositionedGlyph> glyphs = new LinkedHashSet<>();
        collectBoundGlyphs(root, glyphs);
        return List.copyOf(glyphs);
    }

    private static void collectBoundGlyphs(Operator op, Set<PositionedGlyph> glyphs) {
        glyphs.add(op.boundary());
        for (OpInput input : op.inputs()) {
            if (input instanceof OpInput.Rune rune) {
                glyphs.add(rune.glyph());
            } else if (input instanceof OpInput.Op child) {
                collectBoundGlyphs(child.operator(), glyphs);
            }
        }
    }

    private static <T> CompileResult<T> fail(String code, String message) {
        return new CompileResult.Failure<>(List.of(new CompileDiagnostic(code, message)));
    }

    private record Match(ArrayEffectDefinition effect, List<OpInput> inputs) {}
}
