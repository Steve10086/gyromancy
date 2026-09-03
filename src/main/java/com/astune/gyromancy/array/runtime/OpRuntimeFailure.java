package com.astune.gyromancy.array.runtime;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.compile.operator.CompiledOp;
import com.astune.gyromancy.registry.ModAttachments;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Reports a parent-owned dynamic match failure and stops its live array. */
public final class OpRuntimeFailure {
    private static final Set<ArrayObject> PENDING_TERMINATIONS =
            Collections.newSetFromMap(new IdentityHashMap<>());

    public enum Kind {
        RUNTIME_ERROR("runtime_error"),
        AMBIGUOUS_MATCH("ambiguous_match");

        private final String code;

        Kind(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private OpRuntimeFailure() {}

    public static void terminate(OpRuntimeContext context, CompiledOp parent,
                                 Kind kind, String message) {
        String parentId = parent == null ? "unknown" : parent.id().toString();
        Gyromancy.LOGGER.warn("[MagicArrayRuntime] {} in {}: {}",
                kind.code(), parentId, message);

        if (context == null || context.level() == null || context.array() == null) return;
        ArrayObject array = context.array();
        synchronized (PENDING_TERMINATIONS) {
            PENDING_TERMINATIONS.add(array);
        }

        ArrayObject liveArray = context.level().getData(ModAttachments.ARRAY_MANAGER)
                .getArrayObj(array.arrayId());
        if (liveArray != null) ArrayEffectLifecycle.deactivate(context.level(), liveArray);
    }

    /**
     * Consumed by activation after its root has finished running.  At that
     * point the activation array has not been registered yet, so normal
     * lifecycle deactivation cannot prevent registration by itself.
     */
    static boolean consumePendingTermination(ArrayObject array) {
        synchronized (PENDING_TERMINATIONS) {
            return PENDING_TERMINATIONS.remove(array);
        }
    }
}
