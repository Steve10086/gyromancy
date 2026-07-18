package com.astune.gyromancy.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelChunkSection.class)
public interface LevelChunkSectionAccessor {
    @Accessor("biomes")
    PalettedContainerRO<Holder<Biome>> gyromancy$getBiomes();

    @Accessor("biomes")
    void gyromancy$setBiomes(PalettedContainerRO<Holder<Biome>> biomes);
}
