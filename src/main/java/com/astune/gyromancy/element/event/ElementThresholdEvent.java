package com.astune.gyromancy.element.event;

import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.api.element.ElementType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Fired when an element concentration crosses a registered threshold value.
 */
public record ElementThresholdEvent(
        Level level,
        BlockPos pos,
        ElementType element,
        float oldValue,
        float newValue,
        float threshold,
        ThresholdDirection direction
) {}
