package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class ManaProjectileOp extends ProjectileOp {
    public static final ManaProjectileOp DEFINITION = new ManaProjectileOp();

    private ManaProjectileOp() {
        super(ElementType.MANA);
    }

    @Override
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "mana_projectile");
    }

    @Override
    public List<OpInputMatcher> match() {
        return List.of(OpInputMatcher.rune("mana"));
    }
}
