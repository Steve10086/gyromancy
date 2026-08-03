package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.array.runtime.ArrayEffectLifecycle;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.array.runtime.RuntimeHandle;
import com.astune.gyromancy.array.runtime.emit.EmitResult;
import com.astune.gyromancy.array.runtime.emit.EmittedObject;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** Entity payload which runs compiled entity effects when its owner is removed. */
public final class OnDiscardPayload extends EntityPayload {
    public static final ResourceLocation ID =
            OnDiscardOp.ID;
    public static final Codec<OnDiscardPayload> CODEC =
            Codec.unit(() -> new OnDiscardPayload(new OnDiscardContent(java.util.List.of(), null)));

    private OnDiscardContent content;
    private boolean triggered;

    public OnDiscardPayload(OnDiscardContent content) {
        this.content = content;
    }

    public OnDiscardContent content() {
        return content;
    }

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<OnDiscardPayload> codec() {
        return CODEC;
    }

    @Override
    public void bindToArray(UUID arrayId) {
        content = content.withArrayId(arrayId);
    }

    @Override
    public void onOwnerRemoved(Level level, Entity owner) {
        if (triggered || !(level instanceof ServerLevel server)) return;
        triggered = true;

        EmitResult result = new EmitResult();
        for (EntityEffectOp effect : content.effects()) {
            RuntimeHandle handle = effect.activateAt(new OpRuntimeContext(server, effect), owner.position());
            for (EmittedObject emitted : EmitResult.emissions(handle.scratchData())) {
                result.add(emitted);
            }
        }
        if (content.arrayId() != null) {
            ArrayEffectLifecycle.bindEmittedEntities(server, content.arrayId(), result.toRuntimeHandle().scratchData());
        }
    }
}
