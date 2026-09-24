package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.compile.operator.CompiledOp;

import java.util.List;
import java.util.Set;

public sealed interface GroupCompileOutcome
        permits GroupCompileOutcome.Success, GroupCompileOutcome.NoCandidate,
        GroupCompileOutcome.Failure {
    record Success(CompiledOp op, Set<String> dependencyKeys)
            implements GroupCompileOutcome {
        public Success {
            dependencyKeys = Set.copyOf(dependencyKeys);
        }
    }

    record NoCandidate(List<CompileDiagnostic> diagnostics) implements GroupCompileOutcome {
        public NoCandidate {
            diagnostics = List.copyOf(diagnostics);
        }
    }

    record Failure(List<CompileDiagnostic> diagnostics) implements GroupCompileOutcome {
        public Failure {
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
