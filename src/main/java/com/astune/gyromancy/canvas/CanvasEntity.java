package com.astune.gyromancy.canvas;

import com.astune.gyromancy.api.geometry.SurfaceFrame;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.network.CanvasSnapshotPacket;
import com.astune.gyromancy.registry.ModDataComponents;
import com.astune.gyromancy.registry.ModEntities;
import com.astune.gyromancy.registry.ModItems;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import java.util.Objects;

/**
 * A portable, editable hanging canvas. Normal canvases use their document's
 * dimensions; collapsed canvases use the bounds of their scroll model.
 */
public class CanvasEntity extends BlockAttachedEntity {
    public static final float DEPTH = 1.0F / 16.0F;
    private static final String DOCUMENT_TAG = "CanvasDocument";
    private static final String COLLAPSED_TAG = "Collapsed";

    private static final EntityDataAccessor<Integer> DATA_WIDTH =
            SynchedEntityData.defineId(CanvasEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HEIGHT =
            SynchedEntityData.defineId(CanvasEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SCALE =
            SynchedEntityData.defineId(CanvasEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_REVISION =
            SynchedEntityData.defineId(CanvasEntity.class, EntityDataSerializers.INT);
    /**
     * A collapsed canvas remains the same entity and retains its document, but
     * deliberately has no world-side glyph or array registrations.
     */
    private static final EntityDataAccessor<Boolean> DATA_COLLAPSED =
            SynchedEntityData.defineId(CanvasEntity.class, EntityDataSerializers.BOOLEAN);

    private CanvasDocument document = CanvasDocument.blank(1, 1);
    private int revision;
    private Direction direction = Direction.SOUTH;
    private Vec3 surfaceNormalOverride;
    private Vec3 surfaceWidthAxisOverride;
    private Vec3 surfaceHeightAxisOverride;

    public CanvasEntity(EntityType<? extends CanvasEntity> type, Level level) {
        super(type, level);
    }

    protected CanvasEntity(EntityType<? extends CanvasEntity> type, Level level, BlockPos pos) {
        super(type, level, pos);
    }

    public static CanvasEntity create(Level level, BlockPos pos, Direction direction,
                                      CanvasDocument document) {
        return create(level, pos, direction, document, false);
    }

    public static CanvasEntity create(Level level, BlockPos pos, Direction direction,
                                      CanvasDocument document, boolean collapsed) {
        CanvasEntity canvas = new CanvasEntity(ModEntities.CANVAS.get(), level, pos);
        canvas.setDocumentInternal(document, false);
        canvas.entityData.set(DATA_COLLAPSED, collapsed);
        canvas.setDirection(direction);
        return canvas;
    }

    public void setDirection(Direction direction) {
        this.direction = Objects.requireNonNull(direction);
        surfaceNormalOverride = null;
        surfaceWidthAxisOverride = null;
        surfaceHeightAxisOverride = null;
        if (direction.getAxis().isHorizontal()) {
            setYRot(direction.get2DDataValue() * 90.0F);
        } else {
            setYRot(0.0F);
        }
        yRotO = getYRot();
        recalculateBoundingBox();
    }

    /** Sets an arbitrary orthonormal plane for projection canvases. */
    public void setSurfaceOrientation(Vec3 normal, Vec3 widthAxis, Vec3 heightAxis) {
        Vec3 normalizedNormal = normal.normalize();
        Vec3 normalizedWidth = widthAxis.normalize();
        Vec3 normalizedHeight = heightAxis.normalize();
        if (Math.abs(normalizedNormal.dot(normalizedWidth)) > 1.0E-4
                || Math.abs(normalizedNormal.dot(normalizedHeight)) > 1.0E-4
                || Math.abs(normalizedWidth.dot(normalizedHeight)) > 1.0E-4) {
            throw new IllegalArgumentException("Canvas surface axes must be orthogonal");
        }
        surfaceNormalOverride = normalizedNormal;
        surfaceWidthAxisOverride = normalizedWidth;
        surfaceHeightAxisOverride = normalizedHeight;
        recalculateBoundingBox();
    }

    public Vec3 surfaceNormal() {
        return surfaceNormalOverride != null
                ? surfaceNormalOverride : Vec3.atLowerCornerOf(direction.getNormal());
    }

    public Vec3 surfaceWidthAxis() {
        return surfaceWidthAxisOverride != null
                ? surfaceWidthAxisOverride : CanvasOrientation.widthAxis(direction);
    }

    public Vec3 surfaceHeightAxis() {
        return surfaceHeightAxisOverride != null
                ? surfaceHeightAxisOverride : CanvasOrientation.heightAxis(direction);
    }

    /** The compiler and renderer share this exact world-space plane definition. */
    public SurfaceFrame surfaceFrame() {
        return new SurfaceFrame(position(), surfaceWidthAxis(),
                surfaceHeightAxis(), surfaceNormal());
    }

    @Override
    public Direction getDirection() {
        return direction;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_WIDTH, 1);
        builder.define(DATA_HEIGHT, 1);
        builder.define(DATA_SCALE, 1);
        builder.define(DATA_REVISION, 0);
        builder.define(DATA_COLLAPSED, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_WIDTH.equals(key) || DATA_HEIGHT.equals(key) || DATA_COLLAPSED.equals(key)) {
            recalculateBoundingBox();
        }
        super.onSyncedDataUpdated(key);
    }

    @Override
    protected void recalculateBoundingBox() {
        if (direction == null) return;
        AABB bounds = calculateBoundingBox(pos, direction);
        // The Blockbench scroll is authored relative to its hanging origin.
        // Keep that origin for the collapsed renderer instead of implicitly
        // recentering its geometry at the entity position.
        Vec3 renderOrigin = isCollapsed()
                ? CanvasScrollGeometry.origin(pos, direction) : bounds.getCenter();
        setPosRaw(renderOrigin.x, renderOrigin.y, renderOrigin.z);
        setBoundingBox(bounds);
    }

    private AABB calculateBoundingBox(BlockPos pos, Direction facing) {
        if (isCollapsed()) {
            return CanvasScrollGeometry.bounds(pos, facing, syncedHeight());
        }
        return calculateUnfurledBoundingBox(pos, facing);
    }

    /** Calculates the active canvas bounds without changing the current scroll state. */
    private AABB calculateUnfurledBoundingBox(BlockPos pos, Direction facing) {
        double width = syncedWidth();
        double height = syncedHeight();
        Vec3 base = Vec3.atCenterOf(pos).relative(facing, -0.46875);
        double horizontalOffset = ((int) width & 1) == 0 ? 0.5 : 0.0;
        double verticalOffset = ((int) height & 1) == 0 ? 0.5 : 0.0;
        Vec3 widthAxis = surfaceWidthAxis();
        Vec3 heightAxis = surfaceHeightAxis();
        Vec3 normal = surfaceNormal();
        Vec3 center = base.add(widthAxis.scale(horizontalOffset))
                .add(heightAxis.scale(verticalOffset));
        double sizeX = Math.abs(widthAxis.x) * width
                + Math.abs(heightAxis.x) * height + Math.abs(normal.x) * DEPTH;
        double sizeY = Math.abs(widthAxis.y) * width
                + Math.abs(heightAxis.y) * height + Math.abs(normal.y) * DEPTH;
        double sizeZ = Math.abs(widthAxis.z) * width
                + Math.abs(heightAxis.z) * height + Math.abs(normal.z) * DEPTH;
        return AABB.ofSize(center, sizeX, sizeY, sizeZ);
    }

    @Override
    public boolean survives() {
        if (isCollapsed()) {
            // The scroll is attached by the clicked face alone. Its visual
            // length may span multiple blocks, but it must not require an
            // unfolded canvas' entire support plane before it can be placed.
            return isValidSupport(pos.relative(direction.getOpposite()));
        }

        return hasUnfurledSupport();
    }

    /**
     * Tests whether this entity's document can occupy the normal canvas state
     * at its current anchor. This deliberately does not toggle synced state,
     * so a failed unfold leaves the scroll untouched on both sides.
     */
    public boolean canUnfurl() {
        return hasUnfurledSupport();
    }

    private boolean hasUnfurledSupport() {
        // The unfolded canvas is centred near its support plane, while the
        // normal canvas uses a half-block shift to inspect its full support
        // plane rather than its own thin render plane.
        AABB supportBox = calculateUnfurledBoundingBox(pos, direction)
                .move(Vec3.atLowerCornerOf(direction.getNormal()).scale(-0.5))
                .deflate(1.0E-7);
        boolean supported = BlockPos.betweenClosedStream(supportBox).allMatch(this::isValidSupport);
        // Other attached entities may occupy the same plane (for example a
        // ProjectionOp result). They are visual surfaces, not a reason for a
        // supported canvas to fall.
        return supported;
    }

    private boolean isValidSupport(BlockPos supportPos) {
        var state = level().getBlockState(supportPos);
        return state.isSolid()
                || DiodeBlock.isDiode(state)
                || Block.canSupportCenter(level(), supportPos, direction);
    }

    public CanvasDocument document() {
        return document;
    }

    public int revision() {
        return revision;
    }

    public int syncedWidth() {
        return entityData.get(DATA_WIDTH);
    }

    public int syncedHeight() {
        return entityData.get(DATA_HEIGHT);
    }

    public int syncedScale() {
        return entityData.get(DATA_SCALE);
    }

    /** Whether this canvas is stored as a scroll rather than an active surface. */
    public boolean isCollapsed() {
        return entityData.get(DATA_COLLAPSED);
    }

    /** Restores an inactive scroll to a normal canvas after interaction has approved it. */
    public boolean unfurl() {
        if (level().isClientSide || !isCollapsed()) return false;
        entityData.set(DATA_COLLAPSED, false);
        if (level() instanceof ServerLevel serverLevel) {
            CanvasCompileService.onPlaced(serverLevel, this);
            broadcastSnapshot();
        }
        return true;
    }

    public static void notifyCannotUnfurl(Player player) {
        player.displayClientMessage(
                Component.translatable("message.gyromancy.canvas.cannot_unfurl"), true);
    }

    public void replaceDocument(CanvasDocument next, boolean incrementRevision) {
        setDocumentInternal(next, incrementRevision);
    }

    protected void setDocumentInternal(CanvasDocument next, boolean incrementRevision) {
        document = next;
        if (incrementRevision) revision++;
        entityData.set(DATA_WIDTH, next.physicalWidth());
        entityData.set(DATA_HEIGHT, next.physicalHeight());
        entityData.set(DATA_SCALE, next.resolutionScale());
        entityData.set(DATA_REVISION, revision);
        recalculateBoundingBox();
    }

    public boolean containsGlyph(UUID glyphUuid) {
        return document.glyphs().stream().anyMatch(glyph -> glyph.glyphUuid().equals(glyphUuid));
    }

    public Optional<CanvasGlyph> localGlyph(UUID glyphUuid) {
        return document.glyphs().stream()
                .filter(glyph -> glyph.glyphUuid().equals(glyphUuid))
                .findFirst();
    }

    public PositionedGlyph worldGlyph(CanvasGlyph local, int glyphId) {
        int rasterWidth = document.resolutionWidth();
        int rasterHeight = document.resolutionHeight();
        BlockPos supportPos = getPos().relative(getDirection().getOpposite());
        Set<PixelPos> pixels = new HashSet<>();
        for (int cell : local.rawCells()) {
            if (cell < 0 || cell >= document.rawColors().length) continue;
            int x = cell % rasterWidth;
            int y = cell / rasterWidth;
            pixels.add(new PixelPos(
                    supportPos, getDirection(), x, y, document.rawColors()[cell]));
        }

        Vec3[] corners = {
                localToWorld(local.minX(), local.minY()),
                localToWorld(local.maxX(), local.minY()),
                localToWorld(local.minX(), local.maxY()),
                localToWorld(local.maxX(), local.maxY())
        };
        SurfaceFrame frame = surfaceFrame();
        double minWorldX = Double.POSITIVE_INFINITY;
        double maxWorldX = Double.NEGATIVE_INFINITY;
        double minWorldY = Double.POSITIVE_INFINITY;
        double maxWorldY = Double.NEGATIVE_INFINITY;
        for (Vec3 corner : corners) {
            SurfaceFrame.Coordinates coordinates = frame.project(corner);
            minWorldX = Math.min(minWorldX, coordinates.u());
            maxWorldX = Math.max(maxWorldX, coordinates.u());
            minWorldY = Math.min(minWorldY, coordinates.v());
            maxWorldY = Math.max(maxWorldY, coordinates.v());
        }

        Vec3 widthAxis = surfaceWidthAxis();
        Vec3 heightAxis = surfaceHeightAxis();
        Vec3 physicalFront = widthAxis.scale(local.frontX() * document.physicalWidth())
                .add(heightAxis.scale(-local.frontY() * document.physicalHeight()));
        Vec3 front = physicalFront.lengthSqr() > 1.0E-12
                ? physicalFront.normalize() : Vec3.ZERO;
        Vec3 right = surfaceNormal().cross(front);
        double[] extents = projectedGlyphExtents(
                local, rasterWidth, rasterHeight, front, right, corners);

        return new PositionedGlyph(
                local.glyphUuid(), glyphId, local.symbolId(), local.confidence(), local.role(),
                front, extents[0], extents[1], getPos(),
                minWorldX, maxWorldX, minWorldY, maxWorldY,
                Set.copyOf(pixels), java.util.Optional.of(getUUID()), frame);
    }

    public Vec3 localToWorld(double normalizedX, double normalizedY) {
        return surfaceFrame().world(
                (normalizedX - 0.5) * document.physicalWidth(),
                (0.5 - normalizedY) * document.physicalHeight());
    }

    private static double[] projectedExtents(Vec3[] corners, Vec3 front, Vec3 right) {
        if (front.lengthSqr() < 1.0E-12 || right.lengthSqr() < 1.0E-12) {
            return new double[]{0.0, 0.0};
        }
        double minFront = Double.POSITIVE_INFINITY;
        double maxFront = Double.NEGATIVE_INFINITY;
        double minRight = Double.POSITIVE_INFINITY;
        double maxRight = Double.NEGATIVE_INFINITY;
        for (Vec3 corner : corners) {
            double along = corner.dot(front);
            double across = corner.dot(right);
            minFront = Math.min(minFront, along);
            maxFront = Math.max(maxFront, along);
            minRight = Math.min(minRight, across);
            maxRight = Math.max(maxRight, across);
        }
        return new double[]{maxFront - minFront, maxRight - minRight};
    }

    private double[] projectedGlyphExtents(CanvasGlyph glyph,
                                           int rasterWidth,
                                           int rasterHeight,
                                           Vec3 front,
                                           Vec3 right,
                                           Vec3[] fallbackCorners) {
        if (front.lengthSqr() < 1.0E-12 || right.lengthSqr() < 1.0E-12) {
            return new double[]{0.0, 0.0};
        }
        int[] cells = glyph.rawCells();
        if (cells.length == 0) return projectedExtents(fallbackCorners, front, right);

        double minFront = Double.POSITIVE_INFINITY;
        double maxFront = Double.NEGATIVE_INFINITY;
        double minRight = Double.POSITIVE_INFINITY;
        double maxRight = Double.NEGATIVE_INFINITY;
        for (int cell : cells) {
            if (cell < 0 || cell >= rasterWidth * rasterHeight) continue;
            int x = cell % rasterWidth;
            int y = cell / rasterWidth;
            Vec3 point = localToWorld(
                    (x + 0.5) / rasterWidth,
                    (y + 0.5) / rasterHeight);
            double along = point.dot(front);
            double across = point.dot(right);
            minFront = Math.min(minFront, along);
            maxFront = Math.max(maxFront, along);
            minRight = Math.min(minRight, across);
            maxRight = Math.max(maxRight, across);
        }
        if (!Double.isFinite(minFront)) {
            return projectedExtents(fallbackCorners, front, right);
        }
        return new double[]{maxFront - minFront, maxRight - minRight};
    }

    public void sendSnapshot(ServerPlayer player, boolean openEditor) {
        PacketDistributor.sendToPlayer(player,
                new CanvasSnapshotPacket(getId(), revision, document, openEditor));
    }

    public void broadcastSnapshot() {
        if (!level().isClientSide) {
            PacketDistributor.sendToPlayersTrackingEntity(
                    this, new CanvasSnapshotPacket(getId(), revision, document, false));
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level().isClientSide && player instanceof ServerPlayer serverPlayer
                && player.distanceToSqr(this) <= 64.0) {
            if (isCollapsed() && !canUnfurl()) {
                notifyCannotUnfurl(serverPlayer);
            } else if (isCollapsed() && player.isShiftKeyDown()) {
                unfurl();
            } else {
                sendSnapshot(serverPlayer, true);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!isCollapsed() && level() instanceof ServerLevel serverLevel) {
            CanvasCompileService.onPlaced(serverLevel, this);
        }
    }

    @Override
    public void onRemovedFromLevel() {
        RemovalReason reason = getRemovalReason();
        if (!isCollapsed() && reason != null && reason.shouldDestroy()
                && level() instanceof ServerLevel serverLevel) {
            CanvasCompileService.onRemoved(serverLevel, this);
        }
        super.onRemovedFromLevel();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putByte("facing_3d", (byte) direction.get3DDataValue());
        compound.putInt("revision", revision);
        compound.putBoolean(COLLAPSED_TAG, isCollapsed());
        DataResult<net.minecraft.nbt.Tag> encoded =
                CanvasDocument.CODEC.encodeStart(NbtOps.INSTANCE, document);
        encoded.resultOrPartial(message -> {
        }).ifPresent(tag -> compound.put(DOCUMENT_TAG, tag));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        revision = Math.max(0, compound.getInt("revision"));
        entityData.set(DATA_COLLAPSED, compound.getBoolean(COLLAPSED_TAG));
        if (compound.contains(DOCUMENT_TAG)) {
            CanvasDocument.CODEC.parse(NbtOps.INSTANCE, compound.get(DOCUMENT_TAG))
                    .result().ifPresent(value -> document = value);
        }
        direction = compound.contains("facing_3d")
                ? Direction.from3DDataValue(compound.getByte("facing_3d"))
                : Direction.from2DDataValue(compound.getByte("facing"));
        setDocumentInternal(document, false);
        setDirection(direction);
    }

    @Override
    public void dropItem(@Nullable Entity brokenEntity) {
        if (!level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) return;
        playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
        if (brokenEntity instanceof Player player && player.hasInfiniteMaterials()) return;
        ItemStack stack = new ItemStack(ModItems.CANVAS.get());
        stack.set(ModDataComponents.CANVAS_DOCUMENT.get(), document);
        spawnAtLocation(stack);
    }

    @Override
    public ItemEntity spawnAtLocation(ItemStack stack, float offsetY) {
        return super.spawnAtLocation(stack, offsetY);
    }

    public void playPlacementSound() {
        playSound(SoundEvents.PAINTING_PLACE, 1.0F, 1.0F);
    }

    @Override
    public void moveTo(double x, double y, double z, float yaw, float pitch) {
        setPos(x, y, z);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        setPos(x, y, z);
    }

    @Override
    public Vec3 trackingPosition() {
        return Vec3.atLowerCornerOf(pos);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, direction.get3DDataValue(), getPos());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        setDirection(Direction.from3DDataValue(packet.getData()));
    }

    @Override
    public ItemStack getPickResult() {
        ItemStack stack = new ItemStack(ModItems.CANVAS.get());
        stack.set(ModDataComponents.CANVAS_DOCUMENT.get(), document);
        return stack;
    }

    /** Hanging canvases are anchored surfaces and ignore external forces such as tornado attraction. */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
        // Canvas position is determined by its attachment, not entity collisions.
    }

    @Override
    public void push(Vec3 movement) {
        // Canvas position is determined by its attachment, not entity collisions.
    }

    @Override
    public void push(double x, double y, double z) {
        // Canvas position is determined by its attachment, not external forces.
    }
}
