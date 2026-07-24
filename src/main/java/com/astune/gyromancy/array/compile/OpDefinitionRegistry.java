package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.compile.operator.ElementOp;
import com.astune.gyromancy.compile.operator.FireProjectileOp;
import com.astune.gyromancy.compile.operator.ManaProjectileOp;
import com.astune.gyromancy.compile.operator.SplitEmitOp;
import com.astune.gyromancy.compile.operator.WaterProjectileOp;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OpDefinitionRegistry {
    private static final Map<ResourceLocation, OpDefinition> DEFINITIONS = new LinkedHashMap<>();

    static {
        register(FireProjectileOp.DEFINITION);
        register(WaterProjectileOp.DEFINITION);
        register(ManaProjectileOp.DEFINITION);
        register(ElementOp.DEFINITION);
        register(SplitEmitOp.DEFINITION);
    }

    private OpDefinitionRegistry() {}

    public static synchronized List<OpDefinition> definitions() {
        return List.copyOf(DEFINITIONS.values());
    }

    public static synchronized void register(OpDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("definition cannot be null");
        ResourceLocation id = definition.id();
        if (id == null) throw new IllegalArgumentException("definition id cannot be null");
        OpDefinition existing = DEFINITIONS.putIfAbsent(id, definition);
        if (existing != null && existing != definition) {
            throw new IllegalArgumentException("Duplicate op definition: " + id);
        }
    }
}
