package com.astune.gyromancy.wand;

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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

import java.util.UUID;

/** A non-attached canvas used as the world-side source for a wand projection. */
public final class WandProjectionCanvasEntity extends CanvasEntity {
    private static final EntityDataAccessor<Float> DATA_VIEW_YAW =
            SynchedEntityData.defineId(WandProjectionCanvasEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_VIEW_PITCH =
            SynchedEntityData.defineId(WandProjectionCanvasEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_NORMAL_X =
            SynchedEntityData.defineId(WandProjectionCanvasEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_NORMAL_Y =
            SynchedEntityData.defineId(WandProjectionCanvasEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_NORMAL_Z =
            SynchedEntityData.defineId(WandProjectionCanvasEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PROJECTION_OFFSET =
            SynchedEntityData.defineId(WandProjectionCanvasEntity.class, EntityDataSerializers.FLOAT);
    private UUID owner;

    public WandProjectionCanvasEntity(EntityType<? extends WandProjectionCanvasEntity> type,
                                      Level level) {
        super(type, level);
    }

    public static WandProjectionCanvasEntity create(Level level, Vec3 center,
                                                     Direction facing,
                                                     CanvasDocument document,
                                                     UUID owner,
                                                     Vec3 viewDirection,
                                                     float viewYaw,
                                                     float viewPitch,
                                                     float projectionOffset) {
        WandProjectionCanvasEntity canvas = new WandProjectionCanvasEntity(
                ModEntities.WAND_PROJECTION.get(), level);
        canvas.owner = owner;
        canvas.setPos(center);
        canvas.setDocumentInternal(document, false);
        canvas.setDirection(facing);
        canvas.setProjectedNormal(viewDirection.normalize());
        canvas.setProjectionView(viewYaw, viewPitch);
        canvas.entityData.set(DATA_PROJECTION_OFFSET, projectionOffset);
        canvas.setPos(center);
        canvas.recalculateBoundingBox();
        return canvas;
    }

    public UUID owner() {
        return owner;
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
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        owner = compound.hasUUID("wand_owner") ? compound.getUUID("wand_owner") : null;
        if (compound.contains("surface_normal_x")) {
            setProjectedNormal(new Vec3(
                    compound.getDouble("surface_normal_x"),
                    compound.getDouble("surface_normal_y"),
                    compound.getDouble("surface_normal_z")));
        }
        if (compound.contains("projection_offset")) {
            entityData.set(DATA_PROJECTION_OFFSET, compound.getFloat("projection_offset"));
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
    }

    public void setProjectedNormal(Vec3 normal) {
        Vec3 normalized = normal.normalize();
        entityData.set(DATA_NORMAL_X, (float) normalized.x);
        entityData.set(DATA_NORMAL_Y, (float) normalized.y);
        entityData.set(DATA_NORMAL_Z, (float) normalized.z);
        applyProjectedNormal(normalized);
    }

    private void applyProjectedNormal(Vec3 normal) {
        SurfaceFrame frame = SurfaceFrame.facing(position(), normal, new Vec3(0.0, 1.0, 0.0));
        setSurfaceOrientation(frame.normal(), frame.axisU(), frame.axisV());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_NORMAL_X.equals(key) || DATA_NORMAL_Y.equals(key) || DATA_NORMAL_Z.equals(key)) {
            applyProjectedNormal(new Vec3(
                    entityData.get(DATA_NORMAL_X), entityData.get(DATA_NORMAL_Y),
                    entityData.get(DATA_NORMAL_Z)).normalize());
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
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel) || owner == null) return;
        net.minecraft.world.entity.player.Player player = serverLevel.getPlayerByUUID(owner);
        if (player == null || !player.isUsingItem()
                || !(player.getUseItem().getItem() instanceof WandItem)) {
            discard();
            return;
        }

        Vec3 view = player.getViewVector(1.0F).normalize();
        Vec3 center = player.getEyePosition().add(
                view.scale(entityData.get(DATA_PROJECTION_OFFSET)));
        boolean geometryChanged = position().distanceToSqr(center) > 1.0E-12
                || surfaceNormal().distanceToSqr(view) > 1.0E-12;
        setProjectionView(player.getYRot() + 180.0F, player.getXRot());
        if (!geometryChanged) return;

        setPos(center);
        setProjectedNormal(view);
        CanvasCompileService.refreshWorldGeometry(serverLevel, this);
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
