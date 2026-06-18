package com.astune.gyromancy.element.event;

import com.astune.gyromancy.api.element.ElementConcentrations;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Fired when a coordinate is first written with an element override.
 */
public record ElementActivatedEvent(
        Level level,
        BlockPos pos,
        ElementConcentrations initialValues
) {}
