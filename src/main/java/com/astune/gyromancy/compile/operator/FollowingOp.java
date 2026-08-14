package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.entity.ArrayRelativePosition;
import com.astune.gyromancy.registry.ModAttachments;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/** Keeps a spawned effect at its position relative to the array until it moves. */
public final class FollowingOp extends OnEntityTickOp {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "following");

    public static final Codec<FollowingOp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("following", true).forGetter(op -> op.following)
    ).apply(instance, FollowingOp::new));

    private UUID boundArrayId;
    private ArrayRelativePosition arrayRelativePosition;
    private boolean following;

    public FollowingOp() {
        this(true);
    }

    private FollowingOp(boolean following) {
        this.following = following;
    }

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<FollowingOp> codec() {
        return CODEC;
    }

    @Override
    public void onEntityTick(EntityTickContext ctx) {
        if (ctx.isClientSide() || !following || boundArrayId == null) return;

        // A launch can update the entity's current movement before
        // velocityThisTick is published, so check both representations.
        if (ctx.velocity().lengthSqr() > 0.0
                || ctx.owner().getDeltaMovement().lengthSqr() > 0.0) {
            following = false;
            arrayRelativePosition = null;
            return;
        }

        updateArrayRelativePosition(ctx);
    }

    @Override
    public void bindToArray(UUID arrayId) {
        this.boundArrayId = arrayId;
    }

    /** Updates the effect's world position from the array's current frame. */
    private void updateArrayRelativePosition(EntityTickContext ctx) {
        if (!(ctx.level() instanceof ServerLevel serverLevel) || boundArrayId == null) return;
        ArrayObject array = serverLevel.getData(ModAttachments.ARRAY_MANAGER)
                .getArrayObj(boundArrayId);
        if (array == null) {
            arrayRelativePosition = null;
            return;
        }
        if (arrayRelativePosition == null) {
            arrayRelativePosition = ArrayRelativePosition.capture(
                    ctx.position(), array.rootCircleGlyph().center(),
                    array.rootCircleGlyph().surface());
        }
        ctx.owner().setPos(arrayRelativePosition.resolve(
                array.rootCircleGlyph().center(), array.rootCircleGlyph().surface()));
    }
}
