package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.compile.operator.CompiledOp;
import com.astune.gyromancy.compile.operator.OpResolveContext;
import com.astune.gyromancy.compile.operator.DynamicStructure;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Static compiler which normalizes dynamic child structures before matching parents. */
public final class StaticCompiler {
    private final List<OpDefinition> definitions;

    public StaticCompiler(Collection<? extends OpDefinition> definitions) {
        this.definitions = List.copyOf(definitions);
    }

    public CompileResult<CompiledArray> compile(GroupNode ast, StaticResolveContext context) {
        GroupCompileOutcome outcome = compileGroup(ast, context);
        if (outcome instanceof GroupCompileOutcome.NoCandidate noCandidate) {
            return new CompileResult.Failure<>(noCandidate.diagnostics());
        }
        if (outcome instanceof GroupCompileOutcome.Failure failure) {
            return new CompileResult.Failure<>(failure.diagnostics());
        }

        GroupCompileOutcome.Success success = (GroupCompileOutcome.Success) outcome;
        return new CompileResult.Success<>(new CompiledArray(
                success.op(),
                ast.boundary(),
                GroupCompiler.boundGlyphs(success.op()),
                success.op().color(),
                success.dependencyKeys()));
    }

    public CompileResult<CompiledArray> compile(GroupNode ast) {
        StaticResolveContext context = new StaticResolveContext(
                null, null, definitions, ast.boundary(), List.of());
        return compile(ast, context);
    }

    private GroupCompileOutcome compileGroup(GroupNode ast, StaticResolveContext context) {
        OpResolveContext compileContext = OpResolveContext.forStatic(ast.boundary());
        GlobalCompiler compiler = new GlobalCompiler(definitions,
                (sourceGroup, outcome, parentContext) -> normalize(
                        sourceGroup, outcome, parentContext, context));
        GroupCompileOutcome outcome = compiler.compileDetailed(ast, compileContext);
        if (!(outcome instanceof GroupCompileOutcome.Success success)
                || !(success.op() instanceof DynamicStructure dynamic)) {
            return outcome;
        }

        StructureResolution resolution = dynamic.resolveStructure(
                context.withTargetBoundary(ast.boundary()));
        if (resolution instanceof StructureResolution.Preserved preserved) {
            return new GroupCompileOutcome.Success(success.op(), merge(
                    success.dependencyKeys(), preserved.dependencyKeys()), true);
        }
        if (resolution instanceof StructureResolution.RetryableDependency retryable) {
            return new GroupCompileOutcome.Failure(retryable.missing().stream()
                    .map(missing -> new CompileDiagnostic(
                            "missing_wireless_source", missing.key()))
                    .toList());
        }
        if (resolution instanceof StructureResolution.FatalFailure fatal) {
            return new GroupCompileOutcome.Failure(fatal.diagnostics());
        }

        StructureResolution.Found found = (StructureResolution.Found) resolution;
        String token = sourceToken(found);
        if (context.contains(token)) {
            return new GroupCompileOutcome.Failure(List.of(new CompileDiagnostic(
                    "wireless_cycle", "Dynamic structure resolution cycle at " + token)));
        }

        GroupCompileOutcome nested = new StaticCompiler(definitions).compileGroup(
                found.finalGroup(), context.push(token));
        if (nested instanceof GroupCompileOutcome.NoCandidate) {
            return new GroupCompileOutcome.NoCandidate(List.of(
                    new CompileDiagnostic("missing_primary_element",
                            "Resolved dynamic root has no executable operator")));
        }
        if (nested instanceof GroupCompileOutcome.Failure failure) return failure;
        GroupCompileOutcome.Success compiled = (GroupCompileOutcome.Success) nested;
        return new GroupCompileOutcome.Success(compiled.op(), merge(
                merge(success.dependencyKeys(), found.dependencyKeys()),
                compiled.dependencyKeys()));
    }

    private CompileResult<NormalizedChild> normalize(
            GroupNode sourceGroup,
            GroupCompileOutcome outcome,
            OpResolveContext parentContext,
            StaticResolveContext staticContext) {
        // A nested group which has no executable operator (or whose operator
        // rejected its extra inputs) is authored data. Keep it raw so the
        // parent definition decides whether it can consume it; a parent that
        // does not accept raw data will fail its own match.
        if (outcome instanceof GroupCompileOutcome.NoCandidate noCandidate) {
            return new CompileResult.Success<>(new NormalizedChild(
                    new OpInput.RawGroup(sourceGroup, noCandidate.diagnostics()), Set.of()));
        }
        if (outcome instanceof GroupCompileOutcome.Failure failure) {
            return new CompileResult.Success<>(new NormalizedChild(
                    new OpInput.RawGroup(sourceGroup, failure.diagnostics()), Set.of()));
        }

        GroupCompileOutcome.Success success = (GroupCompileOutcome.Success) outcome;
        CompiledOp child = success.op();
        Set<String> dependencies = new LinkedHashSet<>(success.dependencyKeys());
        if (!(child instanceof DynamicStructure dynamic)) {
            return new CompileResult.Success<>(new NormalizedChild(
                    new OpInput.Op(child, sourceGroup), dependencies));
        }

        // Legacy/unit-test compilation has no level lookup. Preserve the symbolic
        // child there; live lifecycle compilation supplies the full static context.
        if (staticContext.level() == null || staticContext.manager() == null) {
            return new CompileResult.Success<>(new NormalizedChild(
                    new OpInput.Op(child, sourceGroup), dependencies));
        }

        StructureResolution resolution = dynamic.resolveStructure(
                staticContext.withTargetBoundary(sourceGroup.boundary()));
        if (resolution instanceof StructureResolution.Preserved preserved) {
            dependencies.addAll(preserved.dependencyKeys());
            return new CompileResult.Success<>(new NormalizedChild(
                    new OpInput.Op(child, sourceGroup).asDeferred(), dependencies));
        }
        if (resolution instanceof StructureResolution.RetryableDependency retryable) {
            return new CompileResult.Failure<>(retryable.missing().stream()
                    .map(missing -> new CompileDiagnostic(
                            "missing_wireless_source", missing.key()))
                    .toList());
        }
        if (resolution instanceof StructureResolution.FatalFailure fatal) {
            return new CompileResult.Failure<>(fatal.diagnostics());
        }

        StructureResolution.Found found = (StructureResolution.Found) resolution;
        String token = sourceToken(found);
        if (staticContext.contains(token)) {
            return new CompileResult.Failure<>(List.of(new CompileDiagnostic(
                    "wireless_cycle", "Dynamic structure resolution cycle at " + token)));
        }

        StaticResolveContext nestedContext = staticContext.push(token);
        GroupCompileOutcome nested = new StaticCompiler(definitions).compileGroup(
                found.finalGroup(), nestedContext);
        if (nested instanceof GroupCompileOutcome.NoCandidate) {
            return new CompileResult.Success<>(new NormalizedChild(
                    new OpInput.RawGroup(found.finalGroup(), List.of()),
                    merge(dependencies, found.dependencyKeys())));
        }
        if (nested instanceof GroupCompileOutcome.Failure failure) {
            return new CompileResult.Failure<>(failure.diagnostics());
        }

        GroupCompileOutcome.Success compiledSource = (GroupCompileOutcome.Success) nested;
        dependencies.addAll(found.dependencyKeys());
        dependencies.addAll(compiledSource.dependencyKeys());
        return new CompileResult.Success<>(new NormalizedChild(
                new OpInput.Op(compiledSource.op(), found.finalGroup(),
                        compiledSource.deferred()), dependencies));
    }

    private static String sourceToken(StructureResolution.Found found) {
        return found.dependencyKeys().stream()
                .findFirst()
                .orElseGet(() -> found.frame().rootGlyph().glyphUuid().toString());
    }

    private static Set<String> merge(Set<String> left, Set<String> right) {
        Set<String> merged = new LinkedHashSet<>(left);
        merged.addAll(right);
        return Set.copyOf(merged);
    }
}
