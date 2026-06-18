package com.astune.gyromancy.element.event;

import com.astune.gyromancy.api.element.ElementConcentrations;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Fired when a coordinate's element override is cleaned up (all values returned to biome defaults).
 */
public record ElementCleanedUpEvent(
        Level level,
        BlockPos pos,
        ElementConcentrations finalValues
) {}
