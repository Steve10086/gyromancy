package com.astune.gyromancy.entity.projection;

import com.astune.gyromancy.canvas.CanvasCompileService;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.registry.ModEntities;
import com.astune.gyromancy.item.WandItem;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/** A wand-owned projection canvas that follows its caster while the wand is used. */
public class WandProjectionEntity extends ProjectionCanvasEntity {
    private static final EntityDataAccessor<Float> DATA_PROJECTION_OFFSET =
            SynchedEntityData.defineId(WandProjectionEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(WandProjectionEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_MIRROR_OFFSET =
            SynchedEntityData.defineId(WandProjectionEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID owner;
    private int clientLerpSteps;
    private double clientLerpX;
    private double clientLerpY;
    private double clientLerpZ;
    private float projectionRollTarget;
    private boolean projectionRollTargetInitialized;

    public WandProjectionEntity(EntityType<? extends WandProjectionEntity> type, Level level) {
        super(type, level);
    }

    public static WandProjectionEntity create(Level level,
                                              Vec3 center,
                                              Direction facing,
                                              CanvasDocument document,
                                              UUID owner,
                                              Vec3 viewDirection,
                                              float viewYaw,
                                              float viewPitch,
                                              float projectionOffset,
                                              boolean mirrorOffset) {
        WandProjectionEntity projection = new WandProjectionEntity(
                ModEntities.WAND_PROJECTION.get(), level);
        projection.owner = owner;
        projection.entityData.set(DATA_OWNER, Optional.of(owner));
        projection.entityData.set(DATA_PROJECTION_OFFSET, projectionOffset);
        projection.entityData.set(DATA_MIRROR_OFFSET, mirrorOffset);
        projection.setPos(center);
        projection.setDocumentInternal(document, false);
        projection.setDirection(facing);
        projection.setProjectedOrientation(viewDirection.normalize(), 0.0F);
        projection.setRollTarget(0.0F);
        projection.setProjectionView(viewYaw, viewPitch);
        projection.setSpawnGameTick(level.getGameTime());
        projection.setPos(center);
        projection.recalculateBoundingBox();
        return projection;
    }

    public UUID owner() {
        return owner != null ? owner : entityData.get(DATA_OWNER).orElse(null);
    }

    public float projectionOffset() {
        return entityData.get(DATA_PROJECTION_OFFSET);
    }

    public boolean mirrorsWandOffset() {
        return entityData.get(DATA_MIRROR_OFFSET);
    }

    /** Keeps remote wand projections visually smooth between server updates. */
    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        if (!level().isClientSide) {
            setPos(x, y, z);
            return;
        }
        Vec3 target = new Vec3(x, y, z);
        if (position().distanceToSqr(target)
                >= CanvasProjectionMotion.TELEPORT_SNAP_DISTANCE
                * CanvasProjectionMotion.TELEPORT_SNAP_DISTANCE) {
            clientLerpSteps = 0;
            setPos(target);
            return;
        }
        clientLerpX = x;
        clientLerpY = y;
        clientLerpZ = z;
        clientLerpSteps = Math.max(1, steps);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            tickClientPositionInterpolation();
            return;
        }
        if (!(level() instanceof ServerLevel serverLevel) || owner == null) return;
        Player player = serverLevel.getPlayerByUUID(owner);
        if (player == null || !player.isUsingItem()
                || !(player.getUseItem().getItem() instanceof WandItem)) {
            discard();
            return;
        }

        Vec3 view = player.getViewVector(1.0F).normalize();
        Vec3 targetCenter = WandProjectionPose.targetCenter(
                player.getEyePosition(), view,
                projectionOffset(), player.getYRot(), mirrorsWandOffset());
        // The client predicts the pose every render frame. Keep the server
        // state authoritative and current as well, without adding a second
        // multi-tick smoothing delay here.
        if (!projectionRollTargetInitialized) {
            projectionRollTarget = projectionRoll();
            projectionRollTargetInitialized = true;
        }
        projectionRollTarget = CanvasProjectionMotion.advanceRollTarget(
                projectionRollTarget, CanvasProjectionMotion.ROLL_DEGREES_PER_TICK);
        setRollTarget(projectionRollTarget);
        float nextRoll = CanvasProjectionMotion.smoothRoll(
                projectionRoll(), projectionRollTarget);
        setProjectionView(player.getYRot() + 180.0F, player.getXRot());
        setPos(targetCenter);
        setProjectedOrientation(view, nextRoll);
        CanvasCompileService.refreshWorldGeometry(serverLevel, this);
    }

    private void tickClientPositionInterpolation() {
        if (clientLerpSteps <= 0) return;
        double divisor = clientLerpSteps;
        setPos(
                getX() + (clientLerpX - getX()) / divisor,
                getY() + (clientLerpY - getY()) / divisor,
                getZ() + (clientLerpZ - getZ()) / divisor);
        clientLerpSteps--;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PROJECTION_OFFSET, 1.0F);
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_MIRROR_OFFSET, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (owner != null) compound.putUUID("wand_owner", owner);
        compound.putFloat("projection_offset", projectionOffset());
        compound.putBoolean("mirror_offset", mirrorsWandOffset());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        owner = compound.hasUUID("wand_owner") ? compound.getUUID("wand_owner") : null;
        entityData.set(DATA_OWNER, owner == null
                ? Optional.empty() : Optional.of(owner));
        if (compound.contains("projection_offset")) {
            entityData.set(DATA_PROJECTION_OFFSET, compound.getFloat("projection_offset"));
        }
        if (compound.contains("mirror_offset")) {
            entityData.set(DATA_MIRROR_OFFSET, compound.getBoolean("mirror_offset"));
        }
    }
}
