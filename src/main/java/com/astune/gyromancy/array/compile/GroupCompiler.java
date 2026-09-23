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
    private final ChildNormalizer childNormalizer;

    protected GroupCompiler(Collection<? extends OpDefinition> definitions, int maxDepth) {
        this(definitions, maxDepth, ChildNormalizer.preserving());
    }

    protected GroupCompiler(Collection<? extends OpDefinition> definitions, int maxDepth,
                            ChildNormalizer childNormalizer) {
        this.definitions = List.copyOf(definitions);
        this.maxDepth = maxDepth;
        this.childNormalizer = childNormalizer;
    }

    public final CompileResult<CompiledOp> compile(GroupNode ast) {
        return compile(ast, null);
    }

    /** Compiles a group while preserving an optional parent projection context. */
    public final CompileResult<CompiledOp> compile(GroupNode ast, OpResolveContext context) {
        GroupCompileOutcome outcome = compileDetailed(ast, context);
        return switch (outcome) {
            case GroupCompileOutcome.Success success ->
                    new CompileResult.Success<>(success.op());
            case GroupCompileOutcome.NoCandidate noCandidate ->
                    new CompileResult.Failure<>(noCandidate.diagnostics());
            case GroupCompileOutcome.Failure failure ->
                    new CompileResult.Failure<>(failure.diagnostics());
        };
    }

    public final GroupCompileOutcome compileDetailed(GroupNode ast, OpResolveContext context) {
        OpResolveContext effective = context == null
                ? OpResolveContext.forCompile(ast.boundary()) : context;
        return compileGroup(ast, 0, effective);
    }

    public final List<OpDefinition> definitions() {
        return definitions;
    }

    public static List<PositionedGlyph> boundGlyphs(CompiledOp root) {
        Set<PositionedGlyph> glyphs = new LinkedHashSet<>();
        collectBoundGlyphs(root, glyphs);
        return List.copyOf(glyphs);
    }

    private GroupCompileOutcome compileGroup(GroupNode group, int depth,
                                             OpResolveContext context) {
        if (!(group.body() instanceof SequenceNode sequence)) {
            return failure("missing_primary_element", "Group has no sequence body");
        }

        OpResolveContext groupContext = context.withTargetBoundary(group.boundary());
        List<OpInput> inputs = new ArrayList<>();
        Set<String> dependencyKeys = new LinkedHashSet<>();
        boolean deferred = false;
        for (ArrayNode child : sequence.children()) {
            if (child instanceof SymbolNode symbol) {
                inputs.add(new OpInput.Rune(symbol.glyph()));
                continue;
            }
            if (!(child instanceof GroupNode nested)) continue;

            if (depth + 1 >= maxDepth) {
                inputs.add(new OpInput.RawGroup(nested, List.of(
                        new CompileDiagnostic("nested_compile_deferred",
                                "Nested group was preserved for a parent compiler"))));
                continue;
            }

            GroupCompileOutcome nestedOutcome = compileGroup(nested, depth + 1, groupContext);
            CompileResult<NormalizedChild> normalized = childNormalizer.normalize(
                    nested, nestedOutcome, groupContext);
            if (normalized instanceof CompileResult.Failure<NormalizedChild> failure) {
                return new GroupCompileOutcome.Failure(failure.diagnostics());
            }

            NormalizedChild childInput = ((CompileResult.Success<NormalizedChild>) normalized).value();
            inputs.add(childInput.input());
            dependencyKeys.addAll(childInput.dependencyKeys());
            if (nestedOutcome instanceof GroupCompileOutcome.Success success) {
                dependencyKeys.addAll(success.dependencyKeys());
                deferred |= success.deferred();
            }
        }

        GroupCompileOutcome created = createOp(group.boundary(), List.copyOf(inputs), groupContext);
        if (created instanceof GroupCompileOutcome.Success success) {
            Set<String> combined = new LinkedHashSet<>(dependencyKeys);
            combined.addAll(success.dependencyKeys());
            return new GroupCompileOutcome.Success(success.op(), combined,
                    deferred || success.deferred());
        }
        return created;
    }

    private GroupCompileOutcome createOp(PositionedGlyph boundary, List<OpInput> inputs,
                                         OpResolveContext context) {
        MatchSelection selection = bestMatch(inputs);
        if (selection.match() == null) {
            if (selection.definitionRejected()) {
                return failure("definition_rejected",
                        "A matched definition rejected one or more direct inputs");
            }
            return new GroupCompileOutcome.NoCandidate(List.of(
                    new CompileDiagnostic("missing_primary_element",
                            "Local direct inputs did not match an operator")));
        }
        Match best = selection.match();
        CompileResult<CompiledOp> result = best.definition().compile(
                context, boundary, List.copyOf(best.inputs()), inputs);
        if (result instanceof CompileResult.Success<CompiledOp> success) {
            return new GroupCompileOutcome.Success(success.value(), Set.of());
        }
        return new GroupCompileOutcome.Failure(
                ((CompileResult.Failure<CompiledOp>) result).diagnostics());
    }

    private MatchSelection bestMatch(List<OpInput> inputs) {
        Match best = null;
        boolean definitionRejected = false;
        for (OpDefinition definition : definitions) {
            InputMatch matched = matchedInputs(inputs, definition);
            if (matched.inputs().size() != definition.match().size()) continue;
            if (!acceptsAllUnmatchedInputs(inputs, matched.used(), definition.accepted())) {
                definitionRejected = true;
                continue;
            }
            if (best == null || definition.match().size() > best.definition().match().size()) {
                best = new Match(definition, matched.inputs());
            }
        }
        return new MatchSelection(best, definitionRejected);
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
            if (OpInputMatcher.isDeferred(input)) continue;
            if (accepted.stream().noneMatch(matcher -> matcher.matches(input))) return false;
        }
        return true;
    }

    private static int firstMatch(List<OpInput> inputs, OpInputMatcher matcher, Set<Integer> used) {
        for (int i = 0; i < inputs.size(); i++) {
            if (used.contains(i)) continue;
            if (OpInputMatcher.isDeferred(inputs.get(i))) return i;
            if (matcher.matches(inputs.get(i))) return i;
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

    private static GroupCompileOutcome failure(String code, String message) {
        return new GroupCompileOutcome.Failure(
                List.of(new CompileDiagnostic(code, message)));
    }

    private record InputMatch(List<OpInput> inputs, Set<Integer> used) {}

    private record Match(OpDefinition definition, List<OpInput> inputs) {}

    private record MatchSelection(Match match, boolean definitionRejected) {}
}
