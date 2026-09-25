package com.astune.gyromancy.block;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.compile.operator.CrystalGenOp;
import com.astune.gyromancy.element.ElementStorageManager;
import com.astune.gyromancy.network.CrystalGrowthPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A decorative element crystal block.
 *
 * <p>Fire, water and wind crystals are grown by their matching crystal
 * generation payloads; earth, light and dark crystals have no payload yet and
 * only exist as placed blocks.
 *
 * <p>Each crystal schedules its own stability check every
 * {@link CrystalGenOp#SCAN_INTERVAL} ticks. Once the element concentration at
 * the block's own position falls below {@link CrystalGenOp#ELEMENT_THRESHOLD}
 * the crystal shatters and drops its crystal loot.</p>
 */
public final class CrystalBlock extends Block implements EntityBlock {
    /** Shatter progress gained per scheduled tick while the element is too low. */
    private static final int SHATTER_PROGRESS_PER_TICK = 25;
    /** Client-side block crack overlay stages, 0 through 9. */
    private static final int BREAK_OVERLAY_STAGES = 9;

    /**
     * Collision and outline shape assembled from the floating crystal model's
     * element bounds (in model units, {@link Block#box} converts them). Every
     * rotated element contributes its enclosing axis-aligned box, which keeps
     * the shape close to the visible silhouette without loading models on the
     * server.
     */
    private static final VoxelShape SHAPE = Shapes.or(
            // core, rotated 45 degrees around Y
            Block.box(4.818, 5.75, 4.818, 11.182, 12.25, 11.182),
            // lower facet, rotated 45 degrees around Z
            Block.box(6.056, 3.181, 6.5, 9.944, 7.069, 9.5),
            // bottom point, rotated 45 degrees around Z
            Block.box(6.763, 2.013, 7.25, 9.237, 4.487, 8.75),
            // upper facet, rotated 45 degrees around Z
            Block.box(6.144, 11.144, 6.5, 9.856, 14.856, 9.5),
            // top point, rotated 45 degrees around Z
            Block.box(6.763, 13.513, 7.25, 9.237, 15.987, 8.75),
            // left shard, rotated 22.5 degrees around Z
            Block.box(1.263, 6.904, 6.25, 4.737, 11.596, 8.25),
            // right shard, rotated -22.5 degrees around Z
            Block.box(11.215, 5.539, 7.875, 14.785, 10.461, 9.875),
            // back shard, rotated -22.5 degrees around X
            Block.box(6.0, 5.654, 1.013, 8.0, 10.346, 4.487),
            // front shard, rotated 22.5 degrees around X
            Block.box(8.25, 6.904, 11.513, 10.25, 11.596, 14.987),
            // left spark, rotated 45 degrees around Z
            Block.box(2.851, 3.476, 10.625, 5.149, 5.774, 11.875),
            // right spark, rotated -45 degrees around Z
            Block.box(11.064, 2.564, 4.125, 13.186, 4.686, 5.375));

    private final ElementType element;

    private CrystalBlock(ElementType element, MapColor color, int lightLevel) {
        super(BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(2.0F)
                .sound(SoundType.AMETHYST)
                // The floating crystal model is smaller than a full cube, so the
                // block must not occlude its neighbours or hide their faces.
                .noOcclusion()
                .lightLevel(state -> lightLevel)
                .requiresCorrectToolForDrops());
        this.element = element;
    }

    // Fire, water and wind crystals glow at light level 10; light crystals
    // shine at 15, while earth and dark crystals give off no light.
    public static CrystalBlock fire() {
        return new CrystalBlock(ElementType.FIRE, MapColor.FIRE, 10);
    }

    public static CrystalBlock water() {
        return new CrystalBlock(ElementType.WATER, MapColor.WATER, 10);
    }

    public static CrystalBlock wind() {
        return new CrystalBlock(ElementType.WIND, MapColor.COLOR_LIGHT_GREEN, 10);
    }

    public static CrystalBlock earth() {
        return new CrystalBlock(ElementType.EARTH, MapColor.COLOR_BROWN, 0);
    }

    public static CrystalBlock light() {
        return new CrystalBlock(ElementType.LIGHT, MapColor.QUARTZ, 15);
    }

    public static CrystalBlock dark() {
        return new CrystalBlock(ElementType.DARK, MapColor.COLOR_BLACK, 0);
    }

    /** The element whose local concentration keeps this crystal stable. */
    public ElementType element() {
        return element;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrystalBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // The crystal is drawn by its block entity renderer so it can grow in.
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !oldState.is(this)) {
            level.scheduleTick(pos, this, CrystalGenOp.SCAN_INTERVAL);
            if (level instanceof ServerLevel serverLevel) {
                CrystalGrowthPacket.broadcast(serverLevel, pos);
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos).is(this)) return;
        CrystalBlockEntity crystal = level.getBlockEntity(pos) instanceof CrystalBlockEntity entity
                ? entity : null;
        if (ElementStorageManager.INSTANCE.get(level, pos).get(element) < CrystalGenOp.ELEMENT_THRESHOLD) {
            // Still unsupported: chip away instead of shattering instantly.
            int progress = (crystal == null ? 0 : crystal.breakProgress())
                    + SHATTER_PROGRESS_PER_TICK;
            if (progress >= CrystalBlockEntity.BREAK_PROGRESS_COMPLETE) {
                stopShattering(level, pos, crystal);
                level.destroyBlock(pos, true);
                return;
            }
            if (crystal != null) crystal.setBreakProgress(progress);
            level.destroyBlockProgress(breakerId(pos), pos,
                    progress * BREAK_OVERLAY_STAGES / CrystalBlockEntity.BREAK_PROGRESS_COMPLETE);
            level.scheduleTick(pos, this, CrystalGenOp.SCAN_INTERVAL);
            return;
        }
        // The local concentration recovered: the crystal stops chipping.
        stopShattering(level, pos, crystal);
        level.scheduleTick(pos, this, CrystalGenOp.SCAN_INTERVAL);
    }

    /** Clears the shatter progress and its client-side crack overlay. */
    private static void stopShattering(ServerLevel level, BlockPos pos,
                                       CrystalBlockEntity crystal) {
        if (crystal == null || crystal.breakProgress() == 0) return;
        crystal.setBreakProgress(0);
        level.destroyBlockProgress(breakerId(pos), pos, -1);
    }

    /** Stable 0-1023 breaker id so clients render the shatter overlay. */
    private static int breakerId(BlockPos pos) {
        long packed = pos.asLong();
        return (int) ((packed ^ (packed >>> 32)) & 0x3FF);
    }
}