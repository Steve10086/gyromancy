package com.astune.gyromancy.array.compile;

import java.util.List;

public record EffectAttributes(
        boolean inverted,
        List<MotionAttribute> motion
) {
    public static final EffectAttributes EMPTY = new EffectAttributes(false, List.of());
}
