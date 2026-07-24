package com.astune.gyromancy.array.runtime.emit;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.array.runtime.RuntimeHandle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EmitResult {
    public static final String EMISSIONS_KEY = "__emissions";

    private final List<EmittedObject> emitted = new ArrayList<>();
    private final Map<String, Object> data = new HashMap<>();

    public void add(EmittedObject object) {
        emitted.add(object);
    }

    public void put(String key, Object value) {
        data.put(key, value);
    }

    public RuntimeHandle toRuntimeHandle() {
        Map<String, Object> scratch = new HashMap<>(data);
        if (!emitted.isEmpty()) scratch.put(EMISSIONS_KEY, List.copyOf(emitted));
        return new RuntimeHandle(Map.copyOf(scratch));
    }

    public static List<EmittedObject> emissions(Map<String, Object> scratchData) {
        Object value = scratchData.get(EMISSIONS_KEY);
        if (!(value instanceof List<?> list)) return List.of();

        List<EmittedObject> emissions = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof EmittedObject emittedObject) emissions.add(emittedObject);
        }
        return List.copyOf(emissions);
    }

    public static void discardEmittedEntities(ServerLevel level, Map<String, Object> scratchData) {
        for (EmittedObject emittedObject : emissions(scratchData)) {
            if (emittedObject.ref() instanceof ArrayObject.EntityRef ref) {
                Entity entity = ref.resolve(level);
                if (entity != null) entity.discard();
            }
        }
    }
}
