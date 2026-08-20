package com.astune.gyromancy.array.compile;

import java.util.Collection;

/** Global compiler front end using the complete group depth. */
public final class GlobalCompiler extends GroupCompiler {
    public GlobalCompiler(Collection<? extends OpDefinition> definitions) {
        super(definitions, Integer.MAX_VALUE);
    }
}
