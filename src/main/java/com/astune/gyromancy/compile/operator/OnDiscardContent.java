package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.array.compile.OpInput;

import java.util.List;
import java.util.UUID;

/** Runtime content for an on-discard handler, including its owning array. */
public record OnDiscardContent(List<OpInput> inputs, UUID arrayId) {
    public OnDiscardContent {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }

    public OnDiscardContent withArrayId(UUID arrayId) {
        return new OnDiscardContent(inputs, arrayId);
    }
}
