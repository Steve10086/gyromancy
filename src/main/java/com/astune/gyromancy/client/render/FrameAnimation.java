package com.astune.gyromancy.client.render;

import java.util.List;

public final class FrameAnimation {
    private final List<Segment> segments;
    private final float duration;

    private FrameAnimation(List<Segment> segments) {
        this.segments = List.copyOf(segments);
        float total = 0.0F;
        for (Segment segment : this.segments) {
            total += segment.duration();
        }
        this.duration = total;
    }

    public static FrameAnimation loop(int frameCount, float ticksPerFrame) {
        return of(Segment.range(0, frameCount, ticksPerFrame));
    }

    public static FrameAnimation of(Segment... segments) {
        return new FrameAnimation(List.of(segments));
    }

    public int frame(float ageTicks) {
        if (segments.isEmpty() || duration <= 0.0F) return 0;

        float time = ageTicks % duration;
        for (Segment segment : segments) {
            float segmentDuration = segment.duration();
            if (time < segmentDuration) {
                return segment.frame(time);
            }
            time -= segmentDuration;
        }
        return segments.getLast().lastFrame();
    }

    public record Segment(int startFrame, int frameCount, float ticksPerFrame) {
        public Segment {
            frameCount = Math.max(1, frameCount);
            ticksPerFrame = Math.max(0.001F, ticksPerFrame);
        }

        public static Segment range(int startFrame, int frameCount, float ticksPerFrame) {
            return new Segment(startFrame, frameCount, ticksPerFrame);
        }

        float duration() {
            return frameCount * ticksPerFrame;
        }

        int frame(float time) {
            int offset = Math.min(frameCount - 1, (int)(time / ticksPerFrame));
            return startFrame + offset;
        }

        int lastFrame() {
            return startFrame + frameCount - 1;
        }
    }
}
