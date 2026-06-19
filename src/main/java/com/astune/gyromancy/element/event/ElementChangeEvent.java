package com.astune.gyromancy.element.event;

import com.astune.gyromancy.api.element.ElementType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Fired when an element concentration's change magnitude exceeds a registered minimum.
 */
public record ElementChangeEvent(
        Level level,
        BlockPos pos,
        ElementType element,
        long delta,
        long rateOfChange,
        long previousValue,
        long currentValue
) {}
