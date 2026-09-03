package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.compile.operator.CompiledOp;
import com.astune.gyromancy.compile.operator.OpResolveContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Common group compiler used by global and local compiler front ends. */
public abstract class GroupCompiler {
    private final List<OpDefinition> definitions;
    private final int maxDepth;

    protected GroupCompiler(Collection<? extends OpDefinition> definitions, int maxDepth) {
        this.definitions = List.copyOf(definitions);
        this.maxDepth = maxDepth;
    }

    public final CompileResult<CompiledOp> compile(GroupNode ast) {
        return compile(ast, null);
    }

    /** Compiles a group while preserving an optional parent projection context. */
    public final CompileResult<CompiledOp> compile(GroupNode ast, OpResolveContext context) {
        return compileGroup(ast, 0, context == null
                ? OpResolveContext.forCompile(ast.boundary()) : context);
    }

    public final List<OpDefinition> definitions() {
        return definitions;
    }

    public static List<PositionedGlyph> boundGlyphs(CompiledOp root) {
        Set<PositionedGlyph> glyphs = new LinkedHashSet<>();
        collectBoundGlyphs(root, glyphs);
        return List.copyOf(glyphs);
    }

    private CompileResult<CompiledOp> compileGroup(GroupNode group, int depth,
                                                    OpResolveContext context) {
        if (!(group.body() instanceof SequenceNode sequence)) {
            return failure("missing_primary_element", "Group has no sequence body");
        }

        OpResolveContext groupContext = context.withTargetBoundary(group.boundary());
        List<OpInput> inputs = new ArrayList<>();
        for (ArrayNode child : sequence.children()) {
            if (child instanceof SymbolNode symbol) {
                inputs.add(new OpInput.Rune(symbol.glyph()));
            } else if (child instanceof GroupNode nested) {
                if (depth + 1 >= maxDepth) {
                    inputs.add(new OpInput.RawGroup(nested, List.of(
                            new CompileDiagnostic("nested_compile_deferred",
                                    "Nested group was preserved for a parent compiler"))));
                    continue;
                }

                CompileResult<CompiledOp> compiled = compileGroup(nested, depth + 1, groupContext);
                if (compiled instanceof CompileResult.Success<CompiledOp> success) {
                    inputs.add(new OpInput.Op(success.value(), nested));
                } else if (compiled instanceof CompileResult.Failure<CompiledOp> failure) {
                    inputs.add(new OpInput.RawGroup(nested, failure.diagnostics()));
                }
            }
        }

        // Group compilation only materializes the static tree.  A nested
        // resolvable Op remains its original OpInput until the parent that
        // consumes it explicitly asks for an OpResolution.
        return createOp(group.boundary(), List.copyOf(inputs), groupContext);
    }

    private CompileResult<CompiledOp> createOp(PositionedGlyph boundary, List<OpInput> inputs,
                                               OpResolveContext context) {
        Match best = bestMatch(inputs);
        if (best == null) {
            return failure("missing_primary_element", "Local direct inputs did not match an operator");
        }
        return best.definition().compile(context, boundary, List.copyOf(best.inputs()), inputs);
    }

    private Match bestMatch(List<OpInput> inputs) {
        Match best = null;
        for (OpDefinition definition : definitions) {
            InputMatch matched = matchedInputs(inputs, definition);
            if (matched.inputs().size() != definition.match().size()) continue;
            if (!acceptsAllUnmatchedInputs(inputs, matched.used(), definition.accepted())) continue;
            if (best == null || definition.match().size() > best.definition().match().size()) {
                best = new Match(definition, matched.inputs());
            }
        }
        return best;
    }

    private static InputMatch matchedInputs(List<OpInput> inputs, OpDefinition definition) {
        List<OpInput> matched = new ArrayList<>();
        Set<Integer> used = new LinkedHashSet<>();
        for (OpInputMatcher matcher : definition.match()) {
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

    private static void collectBoundGlyphs(CompiledOp op, Set<PositionedGlyph> glyphs) {
        if (op.boundary() != null) glyphs.add(op.boundary());
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

    private static <T> CompileResult<T> failure(String code, String message) {
        return new CompileResult.Failure<>(List.of(new CompileDiagnostic(code, message)));
    }

    private record InputMatch(List<OpInput> inputs, Set<Integer> used) {}

    private record Match(OpDefinition definition, List<OpInput> inputs) {}
}
