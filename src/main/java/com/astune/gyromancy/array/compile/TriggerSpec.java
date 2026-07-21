package com.astune.gyromancy.array.compile;

public record TriggerSpec(Kind kind) {
    public enum Kind { ON_ACTIVATE }

    public static final TriggerSpec ON_ACTIVATE = new TriggerSpec(Kind.ON_ACTIVATE);
}
