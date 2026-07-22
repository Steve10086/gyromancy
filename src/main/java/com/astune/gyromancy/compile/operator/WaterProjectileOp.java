package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class WaterProjectileOp extends ProjectileOp {
    public static final WaterProjectileOp DEFINITION = new WaterProjectileOp();

    private WaterProjectileOp() {
        super(ElementType.WATER);
    }

    @Override
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "water_projectile");
    }

    @Override
    public List<OpInputMatcher> match() {
        return List.of(OpInputMatcher.rune("water"));
    }
}
