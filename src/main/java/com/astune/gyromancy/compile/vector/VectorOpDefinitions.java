package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.array.compile.OpDefinition;

import java.util.List;

/** Aggregates the vector definitions supplied to a VectorCompiler. */
public final class VectorOpDefinitions {
    private VectorOpDefinitions() {}

    public static List<OpDefinition> definitions() {
        return List.of(
                StaticVectorDefinition.DIRECTION,
                StaticVectorDefinition.VELOCITY,
                RotationVectorDefinition.DEFINITION,
                StaticRotationVectorDefinition.DEFINITION,
                ArrayNormalVectorDefinition.DEFINITION,
                GravityVectorDefinition.DEFINITION,
                RevertVectorDefinition.DEFINITION);
    }

}
