package com.astune.gyromancy.mixin;

import com.astune.gyromancy.array.MagicArrayDetector;
import com.astune.gyromancy.api.element.ElementConcentrations;
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

import java.util.HashMap;
import java.util.Map;

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
        }
    }

    @Override
    public Map<BlockPos, ElementConcentrations> gyromancy$getElementOverrides() {
        LevelChunk self = (LevelChunk) (Object) this;
        if (!self.hasData(ModAttachments.ELEMENT_OVERRIDES.get())) {
            return new HashMap<>();
        }
        return new HashMap<>(self.getData(ModAttachments.ELEMENT_OVERRIDES.get()));
    }

    @Override
    public void gyromancy$setElementOverrides(Map<BlockPos, ElementConcentrations> overrides) {
        LevelChunk self = (LevelChunk) (Object) this;
        if (overrides == null || overrides.isEmpty()) {
            self.removeData(ModAttachments.ELEMENT_OVERRIDES.get());
        } else {
            self.setData(ModAttachments.ELEMENT_OVERRIDES.get(), new HashMap<>(overrides));
        }
    }

    @Override
    public boolean gyromancy$hasElementOverrides() {
        LevelChunk self = (LevelChunk) (Object) this;
        return self.hasData(ModAttachments.ELEMENT_OVERRIDES.get());
    }
}
