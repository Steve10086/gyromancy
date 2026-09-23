package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.array.compile.LocalCompileContext;
import com.astune.gyromancy.array.compile.LocalCompileResult;

/** Optional second-stage compilation capability. */
public interface LocalCompilable {
    LocalCompileResult localCompile(LocalCompileContext context);
}
