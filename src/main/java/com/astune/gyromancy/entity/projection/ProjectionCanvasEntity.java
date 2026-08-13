package com.astune.gyromancy.entity.projection;

import com.astune.gyromancy.api.geometry.SurfaceFrame;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.canvas.CanvasEntity;
import com.astune.gyromancy.canvas.CanvasCompileService;
import com.astune.gyromancy.item.WandItem;
import com.astune.gyromancy.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;

import java.util.UUID;

/** A non-attached canvas used as the world-side source for a wand projection. */
public final class ProjectionCanvasEntity extends CanvasEntity {
    private static final EntityDataAccessor<Float> DATA_VIEW_YAW =
            SynchedEntityData.defineId(ProjectionCanvasEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_VIEW_PITCH =
            SynchedEntityData.defineId(ProjectionCanvasEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_NORMAL_X =
            SynchedEntityData.defineId(ProjectionCanvasEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_NORMAL_Y =
            SynchedEntityData.defineId(ProjectionCanvasEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_NORMAL_Z =
            SynchedEntityData.defineId(ProjectionCanvasEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PROJECTION_OFFSET =
            SynchedEntityData.defineId(ProjectionCanvasEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROLL_DEGREES =
            SynchedEntityData.defineId(ProjectionCanvasEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> DATA_SPAWN_GAME_TICK =
            SynchedEntityData.defineId(ProjectionCanvasEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<java.util.Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(ProjectionCanvasEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_MIRROR_OFFSET =
            SynchedEntityData.defineId(ProjectionCanvasEntity.class, EntityDataSerializers.BOOLEAN);
    private UUID owner;
    private int clientLerpSteps;
    private double clientLerpX;
    private double clientLerpY;
    private double clientLerpZ;
    private boolean clientNormalSyncPending;
    private Vec3 previousRenderNormal;
    private Float previousRenderRoll;
    private float appliedProjectionRoll;
    private float projectionRollTarget;
    private boolean projectionRollTargetInitialized;

    public ProjectionCanvasEntity(EntityType<? extends ProjectionCanvasEntity> type,
                                  Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    public static ProjectionCanvasEntity create(Level level, Vec3 center,
                                                Direction facing,
                                                CanvasDocument document,
                                                UUID owner,
                                                Vec3 viewDirection,
                                                float viewYaw,
                                                float viewPitch,
                                                float projectionOffset,
                                                boolean mirrorOffset) {
        ProjectionCanvasEntity canvas = new ProjectionCanvasEntity(
                ModEntities.CANVAS_PROJECTION.get(), level);
        canvas.owner = owner;
        canvas.entityData.set(DATA_OWNER, java.util.Optional.of(owner));
        canvas.setPos(center);
        canvas.setDocumentInternal(document, false);
        canvas.setDirection(facing);
        canvas.setProjectedOrientation(viewDirection.normalize(), 0.0F);
        canvas.setRollTarget(0.0F);
        canvas.setProjectionView(viewYaw, viewPitch);
        canvas.entityData.set(DATA_PROJECTION_OFFSET, projectionOffset);
        canvas.entityData.set(DATA_MIRROR_OFFSET, mirrorOffset);
        canvas.entityData.set(DATA_SPAWN_GAME_TICK, level.getGameTime());
        canvas.setPos(center);
        canvas.recalculateBoundingBox();
        return canvas;
    }

    /** Creates a projection plane that keeps the supplied frame and never follows a player. */
    public static ProjectionCanvasEntity createFixed(Level level,
                                                     SurfaceFrame frame,
                                                     CanvasDocument document) {
        ProjectionCanvasEntity canvas = new ProjectionCanvasEntity(
                ModEntities.CANVAS_PROJECTION.get(), level);
        canvas.setPos(frame.origin());
        canvas.setDocumentInternal(document, false);
        canvas.setDirection(Direction.getNearest(frame.normal()));
        canvas.setSurfaceOrientation(frame.normal(), frame.axisU(), frame.axisV());
        canvas.setProjectionView(0.0F, 0.0F);
        canvas.entityData.set(DATA_PROJECTION_OFFSET, 0.0F);
        canvas.entityData.set(DATA_OWNER, java.util.Optional.empty());
        canvas.entityData.set(DATA_MIRROR_OFFSET, false);
        canvas.entityData.set(DATA_SPAWN_GAME_TICK, level.getGameTime());
        canvas.setPos(frame.origin());
        canvas.recalculateBoundingBox();
        return canvas;
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

    /** BlockAttachedEntity only updates its integer anchor; projections need exact coordinates. */
    @Override
    public void setPos(double x, double y, double z) {
        pos = BlockPos.containing(x, y, z);
        setPosRaw(x, y, z);
        recalculateBoundingBox();
        hasImpulse = true;
    }

    @Override
    public Vec3 trackingPosition() {
        return position();
    }

    /** Projection planes are display surfaces, never physical breakable entities. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean skipAttackInteraction(Entity entity) {
        return true;
    }

    @Override
    public void push(Entity entity) {
        // Projection planes never participate in entity pushing.
    }

    @Override
    public void push(Vec3 movement) {
        // Projection planes never participate in entity pushing.
    }

    @Override
    public void push(double x, double y, double z) {
        // Projection planes never participate in entity pushing.
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        // Wand motion is applied explicitly by tick(); external movement is ignored.
    }

    /** CanvasEntity disables interpolation because attached canvases never move. */
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
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(
                this, entity, getDirection().get3DDataValue());
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (owner != null) compound.putUUID("wand_owner", owner);
        compound.putDouble("surface_normal_x", surfaceNormal().x);
        compound.putDouble("surface_normal_y", surfaceNormal().y);
        compound.putDouble("surface_normal_z", surfaceNormal().z);
        compound.putFloat("projection_offset", entityData.get(DATA_PROJECTION_OFFSET));
        compound.putBoolean("mirror_offset", entityData.get(DATA_MIRROR_OFFSET));
        compound.putFloat("surface_roll", projectionRoll());
        compound.putLong("spawn_game_tick", entityData.get(DATA_SPAWN_GAME_TICK));
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        owner = compound.hasUUID("wand_owner") ? compound.getUUID("wand_owner") : null;
        entityData.set(DATA_OWNER, owner == null
                ? java.util.Optional.empty() : java.util.Optional.of(owner));
        if (compound.contains("surface_normal_x")) {
            float roll = compound.getFloat("surface_roll");
            setProjectedOrientation(new Vec3(
                    compound.getDouble("surface_normal_x"),
                    compound.getDouble("surface_normal_y"),
                    compound.getDouble("surface_normal_z")),
                    roll);
            setRollTarget(roll);
        }
        if (compound.contains("projection_offset")) {
            entityData.set(DATA_PROJECTION_OFFSET, compound.getFloat("projection_offset"));
        }
        if (compound.contains("mirror_offset")) {
            entityData.set(DATA_MIRROR_OFFSET, compound.getBoolean("mirror_offset"));
        }
        if (compound.contains("spawn_game_tick")) {
            entityData.set(DATA_SPAWN_GAME_TICK, compound.getLong("spawn_game_tick"));
        }
    }

    public float projectionYaw() {
        return entityData.get(DATA_VIEW_YAW);
    }

    public float projectionPitch() {
        return entityData.get(DATA_VIEW_PITCH);
    }

    public void setProjectionView(float viewYaw, float viewPitch) {
        entityData.set(DATA_VIEW_YAW, viewYaw);
        entityData.set(DATA_VIEW_PITCH, viewPitch);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VIEW_YAW, 0.0F);
        builder.define(DATA_VIEW_PITCH, 0.0F);
        builder.define(DATA_NORMAL_X, 0.0F);
        builder.define(DATA_NORMAL_Y, 0.0F);
        builder.define(DATA_NORMAL_Z, -1.0F);
        builder.define(DATA_PROJECTION_OFFSET, 1.0F);
        builder.define(DATA_ROLL_DEGREES, 0.0F);
        builder.define(DATA_SPAWN_GAME_TICK, Long.MIN_VALUE);
        builder.define(DATA_OWNER, java.util.Optional.empty());
        builder.define(DATA_MIRROR_OFFSET, false);
    }

    public void setProjectedNormal(Vec3 normal) {
        setProjectedOrientation(normal, projectionRoll());
    }

    public float projectionRoll() {
        return appliedProjectionRoll;
    }

    private float projectionRollTarget() {
        return entityData.get(DATA_ROLL_DEGREES);
    }

    private void setRollTarget(float rollDegrees) {
        entityData.set(DATA_ROLL_DEGREES, Mth.wrapDegrees(rollDegrees));
    }

    private void setProjectedOrientation(Vec3 normal, float rollDegrees) {
        Vec3 normalized = normal.normalize();
        entityData.set(DATA_NORMAL_X, (float) normalized.x);
        entityData.set(DATA_NORMAL_Y, (float) normalized.y);
        entityData.set(DATA_NORMAL_Z, (float) normalized.z);
        applyProjectedOrientation(normalized, rollDegrees);
    }

    private void applyProjectedOrientation(Vec3 normal, float rollDegrees) {
        SurfaceFrame frame = WandProjectionVisuals.rolledFrame(
                SurfaceFrame.facing(position(), normal, new Vec3(0.0, 1.0, 0.0)),
                rollDegrees);
        appliedProjectionRoll = Mth.wrapDegrees(rollDegrees);
        setSurfaceOrientation(frame.normal(), frame.axisU(), frame.axisV());
    }

    /** Frame used only for rendering between client ticks. */
    public SurfaceFrame renderSurfaceFrame(float partialTick) {
        Vec3 current = surfaceNormal();
        Vec3 previous = previousRenderNormal == null ? current : previousRenderNormal;
        Vec3 rendered = CanvasProjectionMotion.interpolateDirection(
                previous, current, partialTick);
        return renderSurfaceFrameAt(partialTick, renderCenter(partialTick), rendered);
    }

    /**
     * Builds a frame from a render-time predicted pose. The server-synchronized
     * roll is still interpolated, while position and normal may come from the
     * local player's current render frame.
     */
    public SurfaceFrame renderSurfaceFrameAt(float partialTick, Vec3 center, Vec3 normal) {
        float currentRoll = projectionRoll();
        float previousRoll = previousRenderRoll == null ? currentRoll : previousRenderRoll;
        float renderedRoll = CanvasProjectionMotion.interpolateRoll(
                previousRoll, currentRoll, partialTick);
        return WandProjectionVisuals.rolledFrame(
                SurfaceFrame.facing(
                        center, normal, new Vec3(0.0, 1.0, 0.0)),
                renderedRoll);
    }

    /** Render-only scale; compiler geometry always remains at full size. */
    public float renderEntranceScale(float partialTick) {
        long spawnTick = entityData.get(DATA_SPAWN_GAME_TICK);
        double age = spawnTick == Long.MIN_VALUE
                ? tickCount + partialTick
                : level().getGameTime() + partialTick - spawnTick;
        return WandProjectionVisuals.entranceScale(age);
    }

    /** Exact interpolated center shared by the canvas and its client effects. */
    public Vec3 renderCenter(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, xo, getX()),
                Mth.lerp(partialTick, yo, getY()),
                Mth.lerp(partialTick, zo, getZ()));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_NORMAL_X.equals(key) || DATA_NORMAL_Y.equals(key)
                || DATA_NORMAL_Z.equals(key) || DATA_ROLL_DEGREES.equals(key)) {
            if (level().isClientSide) {
                // All normal components and the roll target are sent as entity
                // metadata. Defer
                // reading them until tick so no mixed intermediate normal can
                // reach the renderer.
                clientNormalSyncPending = true;
            }
        }
    }

    @Override
    protected void recalculateBoundingBox() {
        // Entity's base constructor calls setPos(), which dispatches here before
        // CanvasEntity's direction field has been initialized. Do not inspect
        // the projection plane until construction has reached CanvasEntity.
        if (getDirection() == null) return;
        double width = syncedWidth();
        double height = syncedHeight();
        Vec3 widthAxis = surfaceWidthAxis();
        Vec3 heightAxis = surfaceHeightAxis();
        Vec3 normal = surfaceNormal();
        double sizeX = Math.abs(widthAxis.x) * width
                + Math.abs(heightAxis.x) * height + Math.abs(normal.x) * DEPTH;
        double sizeY = Math.abs(widthAxis.y) * width
                + Math.abs(heightAxis.y) * height + Math.abs(normal.y) * DEPTH;
        double sizeZ = Math.abs(widthAxis.z) * width
                + Math.abs(heightAxis.z) * height + Math.abs(normal.z) * DEPTH;
        setBoundingBox(AABB.ofSize(position(), sizeX, sizeY, sizeZ));
    }

    @Override
    public boolean survives() {
        // Projection planes intentionally occupy the same space as the caster's
        // view volume and are not physical hanging entities.
        return true;
    }

    @Override
    public void tick() {
        if (level().isClientSide) {
            previousRenderNormal = surfaceNormal();
            previousRenderRoll = appliedProjectionRoll;
        }
        super.tick();
        if (level().isClientSide) {
            tickClientInterpolation();
            return;
        }
        if (!(level() instanceof ServerLevel serverLevel) || owner == null) return;
        net.minecraft.world.entity.player.Player player = serverLevel.getPlayerByUUID(owner);
        if (player == null || !player.isUsingItem()
                || !(player.getUseItem().getItem() instanceof WandItem)) {
            discard();
            return;
        }

        Vec3 view = player.getViewVector(1.0F).normalize();
        Vec3 targetCenter = WandProjectionPose.targetCenter(
                player.getEyePosition(), view,
                entityData.get(DATA_PROJECTION_OFFSET), player.getYRot(),
                entityData.get(DATA_MIRROR_OFFSET));
        // The client predicts the pose every render frame. Keep the server
        // state authoritative and current as well, instead of introducing a
        // second multi-tick smoothing delay here.
        Vec3 nextCenter = targetCenter;
        Vec3 nextNormal = view;
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
        setPos(nextCenter);
        setProjectedOrientation(nextNormal, nextRoll);
        CanvasCompileService.refreshWorldGeometry(serverLevel, this);
    }

    private void tickClientInterpolation() {
        if (clientLerpSteps > 0) {
            double divisor = clientLerpSteps;
            setPos(
                    getX() + (clientLerpX - getX()) / divisor,
                    getY() + (clientLerpY - getY()) / divisor,
                    getZ() + (clientLerpZ - getZ()) / divisor);
            clientLerpSteps--;
        }
        Vec3 nextNormal = surfaceNormal();
        if (clientNormalSyncPending) {
            clientNormalSyncPending = false;
            Vec3 synced = new Vec3(
                    entityData.get(DATA_NORMAL_X), entityData.get(DATA_NORMAL_Y),
                    entityData.get(DATA_NORMAL_Z));
            if (synced.lengthSqr() > 1.0E-12) nextNormal = synced.normalize();
        }

        float nextRoll = CanvasProjectionMotion.smoothRoll(
                projectionRoll(), projectionRollTarget());
        if (nextNormal.distanceToSqr(surfaceNormal()) > 1.0E-12
                || Math.abs(Mth.wrapDegrees(nextRoll - projectionRoll())) > 1.0E-5F) {
            applyProjectedOrientation(nextNormal, nextRoll);
        }
    }

    @Override
    public void dropItem(net.minecraft.world.entity.Entity brokenEntity) {
        // A projection is a spell surface, not a breakable hanging canvas.
    }

    @Override
    public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player,
                                                          net.minecraft.world.InteractionHand hand) {
        return net.minecraft.world.InteractionResult.PASS;
    }
}
