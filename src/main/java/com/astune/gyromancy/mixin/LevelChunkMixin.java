package com.astune.gyromancy.mixin;

import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.element.IElementChunkAccessor;
import com.astune.gyromancy.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;

import java.util.HashMap;
import java.util.Map;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin implements IElementChunkAccessor {

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
