package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.array.compile.StaticResolveContext;
import com.astune.gyromancy.array.compile.StructureResolution;

/** Static structure lookup capability exposed by an operator symbol. */
public interface DynamicStructure {
    StructureResolution resolveStructure(StaticResolveContext context);
}
