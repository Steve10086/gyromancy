package com.astune.gyromancy.compile.operator;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

public final class ElementPayloadOp extends OnEntityTickOp {
    public static final Codec<ElementPayloadOp> CODEC = Codec.unit(ElementPayloadOp::new);

    @Override
    public ResourceLocation typeId() {
        return ElementOp.ID;
    }

    @Override
    protected Codec<ElementPayloadOp> codec() {
        return CODEC;
    }

    @Override
    public void onEntityTick(EntityTickContext ctx) {
    }
}
