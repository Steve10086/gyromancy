package com.astune.gyromancy.block;

import com.astune.gyromancy.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity for crystal blocks.
 *
 * <p>It stores the shatter progress accumulated while the local element
 * concentration stays below the crystal's requirement, and doubles as the
 * render hook for the grow-in animation.
 */
public final class CrystalBlockEntity extends BlockEntity {
    /** Shatter progress in percent; the crystal breaks at 100. */
    public static final int BREAK_PROGRESS_COMPLETE = 100;
    private static final String BREAK_PROGRESS_TAG = "break_progress";

    private int breakProgress;

    public CrystalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYSTAL.get(), pos, state);
    }

    /** Current shatter progress in percent. */
    public int breakProgress() {
        return breakProgress;
    }

    /** Stores a new shatter progress. */
    public void setBreakProgress(int progress) {
        if (breakProgress == progress) return;
        breakProgress = Math.clamp(progress, 0, BREAK_PROGRESS_COMPLETE);
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (breakProgress != 0) {
            tag.putInt(BREAK_PROGRESS_TAG, breakProgress);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        breakProgress = Math.clamp(
                tag.getInt(BREAK_PROGRESS_TAG), 0, BREAK_PROGRESS_COMPLETE);
    }
}
