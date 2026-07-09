package com.astune.gyromancy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

public final class RenderAnimation {
    private RenderAnimation() {}

    public static State evaluate(float ageTicks, List<Action> actions) {
        State state = new State();
        if (actions == null) return state;
        for (Action action : actions) {
            if (action != null) action.apply(ageTicks, state);
        }
        return state;
    }

    public static void applyPose(PoseStack poseStack, State state) {
        applyPose(poseStack, state, true);
    }

    public static void applyPose(PoseStack poseStack, State state, boolean applySize) {
        poseStack.translate(state.offset.x, state.offset.y, state.offset.z);
        for (Rotation rotation : state.rotations) {
            Vec3 axis = rotation.axis.normalize();
            poseStack.mulPose(new Quaternionf().rotationAxis(
                    (float) Math.toRadians(rotation.degrees), (float) axis.x, (float) axis.y, (float) axis.z));
        }
        if (applySize) {
            poseStack.scale(state.size.x, state.size.y, state.size.x);
        }
    }

    public static Action move(float start, float length, Vec3 delta) {
        return new TimedAction(start, length, (progress, state) -> state.offset = state.offset.add(delta.scale(progress)));
    }

    public static Action rotate(float start, float length, Vec3 axis, float degrees) {
        return new TimedAction(start, length, (progress, state) -> {
            if (axis.lengthSqr() > 1e-8) state.rotations.add(new Rotation(axis, degrees * progress));
        });
    }

    public static Action opacity(float start, float length, float from, float to) {
        return new TimedAction(start, length, (progress, state) -> state.alpha *= lerp(from, to, progress));
    }

    public static Action size(float start, float length, Vec2 from, Vec2 to) {
        return new TimedAction(start, length, (progress, state) -> state.size = from.add(to.add(from.negated()).scale(progress)));
    }

    public static Action color(float start, float length, int fromArgb, int toArgb) {
        return new TimedAction(start, length, (progress, state) -> {
            state.red = lerp(channel(fromArgb, 16), channel(toArgb, 16), progress);
            state.green = lerp(channel(fromArgb, 8), channel(toArgb, 8), progress);
            state.blue = lerp(channel(fromArgb, 0), channel(toArgb, 0), progress);
        });
    }

    public static Action composition(float start, float length, List<Action> actions) {
        List<Action> children = actions == null ? List.of() : List.copyOf(actions);
        return new TimedAction(start, length, (progress, state) -> {
            float localAge = progress * length;
            for (Action child : children) {
                if (child != null) child.apply(localAge, state);
            }
        });
    }

    public interface Action {
        void apply(float ageTicks, State state);
    }

    public static final class State {
        public Vec3 offset = Vec3.ZERO;
        public final List<Rotation> rotations = new ArrayList<>();
        public Vec2 size = Vec2.ONE;
        public float red = 1.0F;
        public float green = 1.0F;
        public float blue = 1.0F;
        public float alpha = 1.0F;

        public int argb() {
            return (toByte(alpha) << 24) | (toByte(red) << 16) | (toByte(green) << 8) | toByte(blue);
        }
    }

    public record Rotation(Vec3 axis, float degrees) {}

    private record TimedAction(float start, float length, Effect effect) implements Action {
        @Override
        public void apply(float ageTicks, State state) {
            if (ageTicks < start) return;
            float progress = length <= 0.0F ? 1.0F : Math.clamp((ageTicks - start) / length, 0.0F, 1.0F);
            effect.apply(progress, state);
        }
    }

    private interface Effect {
        void apply(float progress, State state);
    }

    private static float channel(int argb, int shift) {
        return ((argb >> shift) & 0xFF) / 255.0F;
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    private static int toByte(float value) {
        return Math.round(Math.clamp(value, 0.0F, 1.0F) * 255.0F);
    }
}
