package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.array.runtime.OpRuntimeContext;

/** Runtime emission modifier capability used by EntityEffectOp. */
public interface EntityEmissionModifier {
    EmitOp.Emission modifyEntityEmission(
            EmitOp.Emission emission, OpRuntimeContext context);
}
