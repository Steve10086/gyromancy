package com.astune.gyromancy.item;

import com.astune.gyromancy.api.canvas.CanvasPenTool;
import com.astune.gyromancy.registry.ModDataComponents;
import com.astune.painter.api.BlendMode;
import com.astune.painter.api.CanvasFace;
import com.astune.painter.api.IPaintProvider;
import com.astune.painter.api.PaintPattern;
import com.astune.painter.api.PaintProviders;
import com.astune.painter.api.PixelProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A compass that turns the player's view angle into a point on a circle.
 *
 * <p>The first block hit while right-click is held becomes the circle center
 * and its face becomes the circle plane. Painter is then supplied with
 * synthetic block hits on the circumference, keeping its normal brush
 * pipeline responsible for rasterisation and network updates.
 */
public final class CompassItem extends Item implements IPaintProvider, CanvasPenTool {
    private static final int DEFAULT_CANVAS_COLOR = 0xFF24132F;
    private static final int DEFAULT_CANVAS_EFFECT = 1;
    private static final int WHITE = 0xFFFFFFFF;
    private static final double BRUSH_DIAMETER = 1.0 / 16.0;
    private static final double BRUSH_RADIUS = BRUSH_DIAMETER / 2.0;
    private static final double STEP = 0.02;
    private static final String MANA_KEY = "gyromancy:mana";
    private static final int MANA_VALUE = 10;

    public static final double DEFAULT_RADIUS = 1.0;
    public static final double MIN_RADIUS = 0.1;
    public static final double MAX_RADIUS = 16.0;
    public static final double RADIUS_SCROLL_STEP = 0.1;

    private static final double EPSILON = 1.0E-6;
    private static final double FACE_EPSILON = 1.0E-5;
    private static final double TWO_PI = Math.PI * 2.0;

    @Nullable
    private static DrawingState active;

    public CompassItem() {
        super(new Properties()
                .stacksTo(1)
                .component(ModDataComponents.COMPASS_RADIUS.get(), DEFAULT_RADIUS)
                .component(com.astune.painter.registry.ModDataComponents.CURRENT_COLOR.get(), WHITE)
                .component(com.astune.painter.registry.ModDataComponents.BRUSH_SIZE.get(), BRUSH_DIAMETER)
                .component(com.astune.painter.registry.ModDataComponents.FEATHER_STRENGTH.get(), 0.0f)
                .component(com.astune.painter.registry.ModDataComponents.OPACITY.get(), 1.0f)
                .component(com.astune.painter.registry.ModDataComponents.BLEND_MODE.get(), BlendMode.OVERWRITE.name())
                .component(com.astune.painter.registry.ModDataComponents.STEP_SIZE.get(), STEP));
        PaintProviders.register(this, this);
    }

    /** Adjusts the persistent radius of a compass stack by the scroll amount. */
    public static boolean adjustRadius(ItemStack stack, double scrollDelta) {
        if (!(stack.getItem() instanceof CompassItem) || scrollDelta == 0.0) return false;

        double radius = getRadius(stack);
        double next = clampRadius(radius + scrollDelta * RADIUS_SCROLL_STEP);
        if (Math.abs(next - radius) < EPSILON) return true;
        setRadius(stack, next);
        return true;
    }

    public static double getRadius(ItemStack stack) {
        return clampRadius(stack.getOrDefault(ModDataComponents.COMPASS_RADIUS.get(), DEFAULT_RADIUS));
    }

    /** Canvas drawing uses a smaller radius scale than the world painter. */
    public static double getCanvasRadius(ItemStack stack) {
        return getRadius(stack) / 4.0;
    }

    public static void setRadius(ItemStack stack, double radius) {
        if (stack.getItem() instanceof CompassItem) {
            stack.set(ModDataComponents.COMPASS_RADIUS.get(), clampRadius(radius));
        }
    }

    /** Replaces the normal crosshair hit with the current circumference point. */
    public static void updateHitResult(Minecraft minecraft) {
        Player player = minecraft.player;
        if (player == null || minecraft.level == null
                || !(player.getMainHandItem().getItem() instanceof CompassItem)
                || !minecraft.options.keyUse.isDown()) {
            active = null;
            return;
        }

        if (active == null || active.player != player) {
            if (!(minecraft.hitResult instanceof BlockHitResult hit)
                    || hit.getType() != HitResult.Type.BLOCK) {
                return;
            }
            active = DrawingState.start(player, hit);
        } else {
            active.updateViewAngle(player);
        }

        minecraft.hitResult = active.hit(player.getMainHandItem());
    }

    /** Returns an interpolated synthetic hit for Painter's chord subdivision. */
    @Nullable
    public static BlockHitResult hitForTrace(Vec3 target) {
        if (active == null) return null;
        return active.hitAtAngle(angleFromPoint(active, target), currentRadius());
    }

    /** Returns a synthetic hit for Painter's per-pixel normal trace. */
    @Nullable
    public static BlockHitResult hitForNormalTrace(Vec3 point, Vec3 normal, Player player) {
        if (active == null || active.player != player) return null;
        return active.hitAtAngle(angleFromPoint(active, point), currentRadius());
    }

    @Override
    public Integer getColor(ItemStack stack, Player player, Level level,
                            BlockPos pos, CanvasFace face, int pixelX, int pixelY) {
        return WHITE;
    }

    @Override
    public Optional<CanvasPenTool.Stroke> canvasStroke(ItemStack stack, Player player) {
        return Optional.of(new CanvasPenTool.Stroke(DEFAULT_CANVAS_COLOR, DEFAULT_CANVAS_EFFECT));
    }

    @Nullable
    @Override
    public PaintPattern getPattern(ItemStack stack, Player player, Level level,
                                   BlockPos pos, Vec3 hitLoc) {
        final double radius = BRUSH_RADIUS;
        return new PaintPattern(BRUSH_DIAMETER, BRUSH_DIAMETER, new PixelProvider() {
            @Override
            public BlendMode getBlendMode() {
                return BlendMode.OVERWRITE;
            }

            @Nullable
            @Override
            public Integer getPixel(double dx, double dy) {
                double cx = dx - radius;
                double cy = dy - radius;
                return Math.sqrt(cx * cx + cy * cy) <= radius ? WHITE : null;
            }

            @Override
            public Map<String, Integer> getEffectValues(double dx, double dy) {
                double cx = dx - radius;
                double cy = dy - radius;
                if (Math.sqrt(cx * cx + cy * cy) > radius) {
                    return Collections.emptyMap();
                }
                return Collections.singletonMap(MANA_KEY, MANA_VALUE);
            }
        });
    }

    @Override
    public Double getStep() {
        return STEP;
    }

    @Override
    public Vec3[] transformPatternAxes(Player player, Vec3 hitLocation,
                                       Direction direction, double width, double height) {
        if (active != null && direction == active.face && active.player == player) {
            Vec3 axis1 = active.axisU.scale(width);
            Vec3 axis2 = active.axisV.scale(height);
            Vec3 origin = hitLocation.subtract(axis1.scale(0.5)).subtract(axis2.scale(0.5));
            return new Vec3[]{origin, axis1, axis2};
        }
        return IPaintProvider.super.transformPatternAxes(player, hitLocation, direction, width, height);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        double radius = stack.getOrDefault(ModDataComponents.COMPASS_RADIUS.get(), DEFAULT_RADIUS);
        tooltip.add(Component.translatable("item.gyromancy.compass.radius", radius));
        tooltip.add(Component.translatable("item.gyromancy.compass.help"));
    }

    @Override
    public Component getName(ItemStack stack) {
        String radius = String.format(Locale.ROOT, "%.2f", getRadius(stack));
        return Component.translatable("item.gyromancy.compass", radius);
    }

    private static double currentRadius() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return DEFAULT_RADIUS;
        return getRadius(minecraft.player.getMainHandItem());
    }

    private static double clampRadius(double radius) {
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS,
                Double.isFinite(radius) ? radius : DEFAULT_RADIUS));
    }

    @Nullable
    private static Double angleFromPoint(DrawingState state, Vec3 point) {
        Vec3 planar = projectToPlane(point.subtract(state.center), state.normal);
        if (planar.lengthSqr() <= EPSILON) return null;
        return unwrapNear(Math.atan2(planar.dot(state.axisV), planar.dot(state.axisU)),
                state.currentAngle);
    }

    private static Vec3 projectToPlane(Vec3 vector, Vec3 normal) {
        return vector.subtract(normal.scale(vector.dot(normal)));
    }

    private static double unwrapNear(double angle, double reference) {
        while (angle - reference > Math.PI) angle -= TWO_PI;
        while (angle - reference < -Math.PI) angle += TWO_PI;
        return angle;
    }

    private static final class DrawingState {
        private final Player player;
        private final Vec3 center;
        private final Direction face;
        private final Vec3 normal;
        private final Vec3 axisU;
        private final Vec3 axisV;
        private final double startAngle;
        private double currentAngle;
        private double lastViewAngle;
        private double sweptAngle;
        private boolean completedCircle;

        private DrawingState(Player player, Vec3 center, Direction face, Vec3 normal,
                             Vec3 axisU, Vec3 axisV, double startAngle) {
            this.player = player;
            this.center = center;
            this.face = face;
            this.normal = normal;
            this.axisU = axisU;
            this.axisV = axisV;
            this.startAngle = startAngle;
            this.currentAngle = startAngle;
            this.lastViewAngle = startAngle;
        }

        private static DrawingState start(Player player, BlockHitResult hit) {
            Direction face = hit.getDirection();
            Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
            Vec3 reference = Math.abs(normal.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            Vec3 axisU = projectToPlane(reference, normal).normalize();
            Vec3 axisV = normal.cross(axisU).normalize();

            Vec3 projectedView = projectToPlane(player.getViewVector(1.0F), normal);
            if (projectedView.lengthSqr() <= EPSILON) {
                projectedView = projectToPlane(player.getEyePosition().subtract(hit.getLocation()), normal);
            }
            double angle = projectedView.lengthSqr() <= EPSILON
                    ? 0.0
                    : Math.atan2(projectedView.dot(axisV), projectedView.dot(axisU));
            return new DrawingState(player, hit.getLocation(), face, normal, axisU, axisV, angle);
        }

        private void updateViewAngle(Player player) {
            Vec3 projected = projectToPlane(player.getViewVector(1.0F), normal);
            if (projected.lengthSqr() <= EPSILON) return;

            double angle = Math.atan2(projected.dot(axisV), projected.dot(axisU));
            angle = unwrapNear(angle, lastViewAngle);
            double delta = angle - lastViewAngle;
            sweptAngle += delta;
            lastViewAngle = angle;
            currentAngle = angle;

            if (!completedCircle && Math.abs(sweptAngle) >= TWO_PI) {
                completedCircle = true;
                currentAngle = startAngle;
            }
        }

        private BlockHitResult hit(ItemStack stack) {
            return hitAtAngle(currentAngle, getRadius(stack));
        }

        private BlockHitResult hitAtAngle(@Nullable Double angle, double radius) {
            return hitAtAngle(angle == null ? currentAngle : angle, radius);
        }

        private BlockHitResult hitAtAngle(double angle, double radius) {
            Vec3 point = center
                    .add(axisU.scale(Math.cos(angle) * radius))
                    .add(axisV.scale(Math.sin(angle) * radius));
            BlockPos blockPos = BlockPos.containing(point.subtract(normal.scale(FACE_EPSILON)));
            return new BlockHitResult(point, face, blockPos, false);
        }
    }
}
