package com.astune.gyromancy.mixin;

import com.astune.gyromancy.array.MagicArrayDetector;
import com.astune.gyromancy.element.ElementChunkData;
import com.astune.gyromancy.element.ElementStorageManager;
import com.astune.gyromancy.element.IElementChunkAccessor;
import com.astune.gyromancy.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin implements IElementChunkAccessor {

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void gyromancy$onSetBlockState(BlockPos pos, BlockState state, boolean isMoving,
                                           CallbackInfoReturnable<BlockState> cir) {
        BlockState oldState = cir.getReturnValue();
        if (oldState == null || oldState.equals(state)) return;

        LevelChunk self = (LevelChunk) (Object) this;
        if (self.getLevel() instanceof ServerLevel level) {
            MagicArrayDetector.onBlockReplaced(level, self, pos, oldState, state);
            ElementStorageManager.INSTANCE.onBlockChanged(level, pos);
        }
    }

    @Override
    public ElementChunkData gyromancy$getElementData() {
        LevelChunk self = (LevelChunk) (Object) this;
        if (!self.hasData(ModAttachments.ELEMENT_OVERRIDES.get())) return null;
        ElementChunkData data = self.getData(ModAttachments.ELEMENT_OVERRIDES.get());
        return data.isEmpty() ? null : data;
    }

    @Override
    public ElementChunkData gyromancy$getOrCreateElementData() {
        LevelChunk self = (LevelChunk) (Object) this;
        return self.getData(ModAttachments.ELEMENT_OVERRIDES.get());
    }

    @Override
    public void gyromancy$clearElementData() {
        LevelChunk self = (LevelChunk) (Object) this;
        self.removeData(ModAttachments.ELEMENT_OVERRIDES.get());
        self.setUnsaved(true);
    }

    @Override
    public void gyromancy$markElementDataDirty() {
        ((LevelChunk) (Object) this).setUnsaved(true);
    }

    @Override
    public boolean gyromancy$hasElementData() {
        LevelChunk self = (LevelChunk) (Object) this;
        return self.hasData(ModAttachments.ELEMENT_OVERRIDES.get())
                && !self.getData(ModAttachments.ELEMENT_OVERRIDES.get()).isEmpty();
    }
}
