package com.astune.gyromancy.mixin;

import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.element.IElementChunkAccessor;
import com.astune.gyromancy.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;

import java.util.HashMap;
import java.util.Map;

/**
 * Mixin to LevelChunk to implement IElementChunkAccessor.
 * Delegates to the NeoForge AttachmentType for persistent storage.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin implements IElementChunkAccessor {

    @Override
    public Map<BlockPos, ElementConcentrations> gyromancy$getElementOverrides() {
        LevelChunk self = (LevelChunk) (Object) this;
        Map<BlockPos, ElementConcentrations> data = self.getData(ModAttachments.ELEMENT_OVERRIDES.get());
        // Return the attachment's map directly — mutations persist
        return data;
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
