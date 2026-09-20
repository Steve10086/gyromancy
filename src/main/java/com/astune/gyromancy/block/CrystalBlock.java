package com.astune.gyromancy.block;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.compile.operator.CrystalGenOp;
import com.astune.gyromancy.element.ElementStorageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * A decorative full block grown by a matching element crystal payload.
 *
 * <p>Each crystal schedules its own stability check every
 * {@link CrystalGenOp#SCAN_INTERVAL} ticks. Once the element concentration at
 * the block's own position falls below {@link CrystalGenOp#ELEMENT_THRESHOLD}
 * the crystal shatters and removes itself without dropping loot.</p>
 */
public final class CrystalBlock extends Block {
    private final ElementType element;

    private CrystalBlock(ElementType element, MapColor color) {
        super(BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(2.0F)
                .sound(SoundType.AMETHYST)
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