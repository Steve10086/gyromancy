package com.astune.gyromancy.array.compile;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Result of querying a dynamic structure before parent matching. */
public sealed interface StructureResolution
        permits StructureResolution.Found, StructureResolution.Preserved,
        StructureResolution.RetryableDependency, StructureResolution.FatalFailure {
    record Found(GroupNode finalGroup, SourceFrameRef frame, Set<String> dependencyKeys)
            implements StructureResolution {
        public Found {
            Objects.requireNonNull(finalGroup, "finalGroup");
            Objects.requireNonNull(frame, "frame");
            dependencyKeys = Set.copyOf(dependencyKeys);
        }
    }

    /** Keeps the symbolic input, for example a publishing Wireless root. */
    record Preserved(Set<String> dependencyKeys) implements StructureResolution {
        public Preserved {
            dependencyKeys = Set.copyOf(dependencyKeys);
        }
    }

    record RetryableDependency(List<MissingDependency> missing)
            implements StructureResolution {
        public RetryableDependency {
            missing = List.copyOf(missing);
        }
    }

    record FatalFailure(List<CompileDiagnostic> diagnostics)
            implements StructureResolution {
        public FatalFailure {
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
