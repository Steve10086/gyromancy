package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.effect.MagicEffect;
import com.astune.gyromancy.array.runtime.ArrayEffectLifecycle;
import com.astune.gyromancy.array.runtime.OpRuntimeFailure;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.array.runtime.RuntimeHandle;
import com.astune.gyromancy.array.runtime.emit.EmitResult;
import com.astune.gyromancy.array.runtime.emit.EmittedObject;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.registry.ModAttachments;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.List;

/** Entity payload which runs compiled entity effects when its owner is removed. */
public final class OnDiscardPayload extends EntityPayload {
    public static final ResourceLocation ID =
            OnDiscardOp.ID;
    public static final Codec<OnDiscardPayload> CODEC =
            Codec.unit(() -> new OnDiscardPayload(new OnDiscardContent(List.of(), null), null));
    private static final List<OpInputMatcher> EFFECT_MATCHERS =
            List.of(OpInputMatcher.op(EntityEffectOp.class));

    private OnDiscardContent content;
    private final OnDiscardOp parentOp;
    private boolean triggered;

    public OnDiscardPayload(OnDiscardContent content) {
        this(content, null);
    }

    public OnDiscardPayload(OnDiscardContent content, OnDiscardOp parentOp) {
        this.content = content;
        this.parentOp = parentOp;
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
    public void bindToArray(ServerLevel level, UUID arrayId) {
        bindToArray(arrayId);
        // The persisted content is rebuilt once the owning array is available.
        // The actual pipeline lookup is kept at the lifecycle boundary.
    }

    @Override
    public void onOwnerRemoved(Level level, MagicEffect owner) {
        if (triggered || !(level instanceof ServerLevel server)) return;
        triggered = true;

        EmitResult result = new EmitResult();
        OpRuntimeContext runtime = runtimeContext(server, owner);
        for (OpInput input : content.inputs()) {
            if (!(input instanceof OpInput.Op child)) {
                OpRuntimeFailure.terminate(runtime, parentOp, OpRuntimeFailure.Kind.RUNTIME_ERROR,
                        "OnDiscard requires a compiled EntityEffectOp child");
                return;
            }
            if (!(child.operator() instanceof EntityEffectOp effect)) {
                OpRuntimeFailure.terminate(runtime, parentOp, OpRuntimeFailure.Kind.RUNTIME_ERROR,
                        "OnDiscard requires an EntityEffectOp after static compilation");
                return;
            }
            RuntimeHandle handle = effect.activateAt(
                    runtime.forOp(effect).withoutParent(), owner.position());
            for (EmittedObject emitted : EmitResult.emissions(handle.scratchData())) {
                result.add(emitted);
            }
        }
        if (content.arrayId() != null) {
            ArrayEffectLifecycle.bindEmittedEntities(server, content.arrayId(), result.toRuntimeHandle().scratchData());
        }
    }

    private OpRuntimeContext runtimeContext(ServerLevel level, MagicEffect owner) {
        OpRuntimeContext runtime = new OpRuntimeContext(level, parentOp, owner.position(), null)
                .withoutParent();
        if (content.arrayId() == null) return runtime;

        ArrayObject array = level.getData(ModAttachments.ARRAY_MANAGER).getArrayObj(content.arrayId());
        return array == null ? runtime : runtime.withArray(array, array.rootCircleGlyph());
    }
}
