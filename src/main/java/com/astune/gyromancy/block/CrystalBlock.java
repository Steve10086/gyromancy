package com.astune.gyromancy.block;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.compile.operator.CrystalGenOp;
import com.astune.gyromancy.element.ElementStorageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A decorative block grown by a matching element crystal payload.
 *
 * <p>Each crystal schedules its own stability check every
 * {@link CrystalGenOp#SCAN_INTERVAL} ticks. Once the element concentration at
 * the block's own position falls below {@link CrystalGenOp#ELEMENT_THRESHOLD}
 * the crystal shatters and drops its crystal loot.</p>
 */
public final class CrystalBlock extends Block {
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

    private CrystalBlock(ElementType element, MapColor color) {
        super(BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(2.0F)
                .sound(SoundType.AMETHYST)
                // The floating crystal model is smaller than a full cube, so the
                // block must not occlude its neighbours or hide their faces.
                .noOcclusion()
                .requiresCorrectToolForDrops());
        this.element = element;
    }

    public static CrystalBlock fire() {
        return new CrystalBlock(ElementType.FIRE, MapColor.FIRE);
    }

    public static CrystalBlock water() {
        return new CrystalBlock(ElementType.WATER, MapColor.WATER);
    }

    public static CrystalBlock wind() {
        return new CrystalBlock(ElementType.WIND, MapColor.COLOR_LIGHT_GREEN);
    }

    public static CrystalBlock earth() {
        return new CrystalBlock(ElementType.EARTH, MapColor.COLOR_BROWN);
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
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !oldState.is(this)) {
            level.scheduleTick(pos, this, CrystalGenOp.SCAN_INTERVAL);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos).is(this)) return;
        if (ElementStorageManager.INSTANCE.get(level, pos).get(element) < CrystalGenOp.ELEMENT_THRESHOLD) {
            level.destroyBlock(pos, true);
            return;
        }
        level.scheduleTick(pos, this, CrystalGenOp.SCAN_INTERVAL);
    }
}