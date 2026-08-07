package com.astune.gyromancy.item;

import com.astune.gyromancy.api.canvas.CanvasPenTool;
import com.astune.painter.api.*;
import com.astune.painter.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

/**
 * A debug brush that paints single-pixel mana dots (effect value 10).
 *
 * <p>Each painted pixel writes {@code gyromancy:mana = 10} to the
 * CanvasFace effect layer.
 *
 * <p>Fixed parameters:
 * <ul>
 *   <li>Color: solid white (0xFFFFFFFF)</li>
 *   <li>Size: 1/16 block = 1 pixel on 16×16 face</li>
 *   <li>Feather: 0 (hard edge)</li>
 *   <li>Opacity: 1 (fully opaque)</li>
 *   <li>Pattern: circular dot</li>
 *   <li>Mana value: 10</li>
 * </ul>
 */
public class DebugBrushItem extends Item implements IPaintProvider, CanvasPenTool {
    private static final int DEFAULT_CANVAS_COLOR = 0xFF24132F;
    private static final int DEFAULT_CANVAS_EFFECT = 1;
    private static final int WHITE = 0xFFFFFFFF;
    private static final double BRUSH_DIAMETER = 1.0 / 16.0; // 1 pixel
    private static final double BRUSH_RADIUS = BRUSH_DIAMETER / 2.0;
    private static final double STEP = 0.005;
    private static final String MANA_KEY = "gyromancy:mana";
    private static final int MANA_VALUE = 10;

    public DebugBrushItem() {
        super(new Properties()
                .stacksTo(1)
                .component(ModDataComponents.CURRENT_COLOR.get(), WHITE)
                .component(ModDataComponents.BRUSH_SIZE.get(), BRUSH_DIAMETER)
                .component(ModDataComponents.FEATHER_STRENGTH.get(), 0.0f)
                .component(ModDataComponents.OPACITY.get(), 1.0f)
                .component(ModDataComponents.BLEND_MODE.get(), BlendMode.OVERWRITE.name())
                .component(ModDataComponents.STEP_SIZE.get(), STEP));
        PaintProviders.register(this, this);
    }

    // ═══════════════════════════════════════════════════════════════
    // IPaintProvider
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Integer getColor(ItemStack stack, Player player, Level level,
                            BlockPos pos, CanvasFace face, int pixelX, int pixelY) {
        return WHITE;
    }

    @Override
    public Optional<Stroke> canvasStroke(ItemStack stack, Player player) {
        return Optional.of(new Stroke(DEFAULT_CANVAS_COLOR, DEFAULT_CANVAS_EFFECT));
    }

    @Nullable
    @Override
    public PaintPattern getPattern(ItemStack stack, Player player, Level level,
                                   BlockPos pos, Vec3 hitLoc) {
        double diameter = stack.getOrDefault(ModDataComponents.BRUSH_SIZE.get(), BRUSH_DIAMETER);
        if (diameter <= 0) return null;

        final double radius = diameter / 2.0;
        float opacity = stack.getOrDefault(ModDataComponents.OPACITY.get(), 1.0f);
        float feather = stack.getOrDefault(ModDataComponents.FEATHER_STRENGTH.get(), 0.0f);

        return new PaintPattern(diameter, diameter, new PixelProvider() {
            @Override
            public BlendMode getBlendMode() {
                return BlendMode.OVERWRITE;
            }

            @Nullable
            @Override
            public Integer getPixel(double dx, double dy) {
                double cx = dx - radius;
                double cy = dy - radius;
                double dist = Math.sqrt(cx * cx + cy * cy);
                if (dist > radius) return null;

                float alphaFactor = opacity;
                if (feather > 0 && radius > 0) {
                    double ratio = dist / radius;
                    alphaFactor = opacity * (float) Math.pow(1.0 - ratio, feather);
                }

                int a = (int) (255 * alphaFactor);
                if (a <= 0) return null;
                return (a << 24) | 0x00FFFFFF;
            }

            @Override
            public Map<String, Integer> getEffectValues(double dx, double dy) {
                double cx = dx - radius;
                double cy = dy - radius;
                double dist = Math.sqrt(cx * cx + cy * cy);
                if (dist <= radius) {
                    return java.util.Collections.singletonMap(MANA_KEY, MANA_VALUE);
                }
                return java.util.Collections.emptyMap();
            }
        });
    }

    @Override
    public Double getStep() {
        return STEP;
    }

    // ═══════════════════════════════════════════════════════════════
    // Paint distance tracking (per Pigmentum DebugPaintbrush pattern)
    // ═══════════════════════════════════════════════════════════════

    private Vec3 lastHitLoc = null;

    @Override
    public boolean shouldPaint(Player player, @Nullable BlockHitResult result) {
        if (!(player.level().isClientSide
                && net.minecraft.client.Minecraft.getInstance().options.keyUse.isDown())) {
            return false;
        }
        if (result == null) return false;
        if (lastHitLoc == null) {
            lastHitLoc = result.getLocation();
            return true;
        }
        if (lastHitLoc.distanceTo(result.getLocation()) > getStep()) {
            lastHitLoc = result.getLocation();
            return true;
        }
        return false;
    }
}
