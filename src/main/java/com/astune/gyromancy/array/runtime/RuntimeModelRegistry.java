package com.astune.gyromancy.array.runtime;

import com.astune.gyromancy.array.compile.RuntimeModel;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Live-only owner for non-serializable phase-two models. */
public final class RuntimeModelRegistry {
    private static final Map<ServerLevel, Map<UUID, RuntimeModel>> MODELS = new WeakHashMap<>();

    private RuntimeModelRegistry() {}

    public static void put(ServerLevel level, UUID arrayId, RuntimeModel model) {
        MODELS.computeIfAbsent(level, ignored -> new HashMap<>()).put(arrayId, model);
    }

    public static RuntimeModel get(ServerLevel level, UUID arrayId) {
        Map<UUID, RuntimeModel> models = MODELS.get(level);
        return models == null ? null : models.get(arrayId);
    }

    public static void remove(ServerLevel level, UUID arrayId) {
        Map<UUID, RuntimeModel> models = MODELS.get(level);
        if (models == null) return;
        models.remove(arrayId);
        if (models.isEmpty()) MODELS.remove(level);
    }

    public static void clear(ServerLevel level) {
        MODELS.remove(level);
    }
}
