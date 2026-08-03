package com.astune.gyromancy.compile.operator;

import java.util.List;
import java.util.UUID;

/** Runtime content for an on-discard handler, including its owning array. */
public record OnDiscardContent(List<EntityEffectOp> effects, UUID arrayId) {
    public OnDiscardContent {
        effects = effects == null ? List.of() : List.copyOf(effects);
    }

    public OnDiscardContent withArrayId(UUID arrayId) {
        return new OnDiscardContent(effects, arrayId);
    }
}
