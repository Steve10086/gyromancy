package com.astune.gyromancy.array.runtime;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.array.compile.CompiledArray;
import com.astune.gyromancy.compile.operator.Operator;
import com.astune.gyromancy.compile.operator.FireProjectileOp;
import com.astune.gyromancy.compile.operator.ManaProjectileOp;
import com.astune.gyromancy.symbol.CenterSymbol;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;

public final class ArrayRuntimes {
    private static final String RUNTIME_KEY = "__runtime";
    private static final String OPERATOR_KEY = "__operator";
    private static final Map<String, RuntimeDeactivator> DEACTIVATORS = Map.of(
            FireProjectileOp.ID.toString(), (level, data) -> discard(level, data, CenterSymbol.FIREBALL_KEY),
            ManaProjectileOp.ID.toString(), (level, data) -> discard(level, data, CenterSymbol.MANABALL_KEY)
    );

    private ArrayRuntimes() {}

    public static RuntimeHandle activate(CompiledArray compiled, ServerLevel level) {
        //TODO: cleanup after acceptance: remove this migration plan comment.
        /*
         * Cross-TODO runtime plan:
         *
         *
         * 1. Activate the Operator produced by ArrayNodeCompiler, not an EffectNode.
         *    - ArrayRuntimes should receive the compiled Operator/envelope from the
         *      detector.
         *    - The Operator exposes its runtime behavior or activation method.
         *
         * 2. Remove the FireballOp special case.
         *    - Dispatch through a small runtime interface on Operator, or through a
         *      simple runtime kind returned by Operator.
         *    - Fireball, water, and mana projectiles should all use the same dispatch
         *      shape; only their Operator implementation differs.
         *
         * 3. Keep scratch data owned by runtime activation.
         *    - The runtime returns entity refs, runtime id, color/debug metadata, and
         *      any persistent state needed for deactivate.
         *    - MagicArrayDetector copies that scratch data into ArrayObject but does
         *      not interpret op-specific keys.
         *
         * 4. Delete LegacyRuntimeAdapter after migrated Operators cover current
         *    gameplay behavior.
         */
        RuntimeHandle handle = compiled.root().activate(level);
        Map<String, Object> data = new HashMap<>(handle.scratchData());
        data.put(RUNTIME_KEY, compiled.root().id().toString());
        data.put(OPERATOR_KEY, compiled.root());
        return new RuntimeHandle(Map.copyOf(data));
    }

    public static void deactivate(ServerLevel level, ArrayObject array) {
        if (array.scratchData().get(OPERATOR_KEY) instanceof Operator operator) {
            operator.deactivate(level, array.scratchData());
            return;
        }
        Object runtime = array.scratchData().get(RUNTIME_KEY);
        RuntimeDeactivator deactivator = runtime instanceof String id ? DEACTIVATORS.get(id) : null;
        if (deactivator != null) deactivator.deactivate(level, array.scratchData());
    }

    private static void discard(ServerLevel level, Map<String, Object> scratchData, String... keys) {
        for (String key : keys) {
            CenterSymbol.boundEntity(level, scratchData, key).ifPresent(entity -> entity.discard());
        }
    }

    @FunctionalInterface
    private interface RuntimeDeactivator {
        void deactivate(ServerLevel level, Map<String, Object> scratchData);
    }
}
