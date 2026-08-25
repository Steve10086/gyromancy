package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.entity.ArrayRelativePosition;
import com.astune.gyromancy.entity.MagicEntity;
import com.astune.gyromancy.registry.ModAttachments;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

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
    private Vec3 parentRelativePosition;
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
        if (ctx.isClientSide() || !following) return;

        // A launch can update the entity's current movement before
        // velocityThisTick is published, so check both representations.
        if (ctx.velocity().lengthSqr() > 0.0
                || ctx.owner().getDeltaMovement().lengthSqr() > 0.0) {
            stopFollowing();
            return;
        }

        Object parent = ctx.parent();
        if (parent instanceof ArrayObject array) {
            updateArrayRelativePosition(ctx, array);
        } else if (parent instanceof Entity entity) {
            updateEntityRelativePosition(ctx, entity);
        } else if (ctx.owner() instanceof MagicEntity effect && effect.parentPending()) {
            return;
        } else {
            stopFollowing();
        }
    }

    @Override
    public void bindToArray(UUID arrayId) {
        this.boundArrayId = arrayId;
    }

    /** Updates the effect's world position from the array's current frame. */
    private void updateArrayRelativePosition(EntityTickContext ctx, ArrayObject parentArray) {
        if (!(ctx.level() instanceof ServerLevel serverLevel)) return;
        ArrayObject array = boundArrayId == null
                ? parentArray
                : serverLevel.getData(ModAttachments.ARRAY_MANAGER).getArrayObj(boundArrayId);
        if (array == null) return;
        if (arrayRelativePosition == null) {
            arrayRelativePosition = ArrayRelativePosition.capture(
                    ctx.position(), array.rootCircleGlyph().center(),
                    array.rootCircleGlyph().surface());
        }
        ctx.owner().setPos(arrayRelativePosition.resolve(
                array.rootCircleGlyph().center(), array.rootCircleGlyph().surface()));
    }

    private void updateEntityRelativePosition(EntityTickContext ctx, Entity parent) {
        if (!parent.isAlive() || parent.level() != ctx.level()) {
            stopFollowing();
            return;
        }
        if (parentRelativePosition == null) {
            parentRelativePosition = ctx.position().subtract(parent.position());
        }
        ctx.owner().setPos(parent.position().add(parentRelativePosition));
    }

    private void stopFollowing() {
        following = false;
        arrayRelativePosition = null;
        parentRelativePosition = null;
    }
}
