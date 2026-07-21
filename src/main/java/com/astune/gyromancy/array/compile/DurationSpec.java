package com.astune.gyromancy.array.compile;

public record DurationSpec(Kind kind) {
    public enum Kind { INSTANT, SUSTAIN }

    public static final DurationSpec INSTANT = new DurationSpec(Kind.INSTANT);
    public static final DurationSpec SUSTAIN = new DurationSpec(Kind.SUSTAIN);
}
