package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.compile.operator.FireballOp;
import com.astune.gyromancy.compile.operator.ManaProjectileOp;
import com.astune.gyromancy.compile.operator.WaterProjectileOp;

import java.util.ArrayList;
import java.util.List;

public final class ArrayEffectRegistry {
    private static final List<ArrayEffectDefinition> EFFECTS = new ArrayList<>();

    static {
        register(FireballOp.DEFINITION);
        register(WaterProjectileOp.DEFINITION);
        register(ManaProjectileOp.DEFINITION);
    }

    private ArrayEffectRegistry() {}

    public static List<ArrayEffectDefinition> effects() {
        return List.copyOf(EFFECTS);
    }

    private static void register(ArrayEffectDefinition effect) {
        EFFECTS.add(effect);
    }
}
