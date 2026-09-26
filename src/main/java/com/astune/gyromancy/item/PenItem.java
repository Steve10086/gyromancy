package com.astune.gyromancy.item;

import com.astune.gyromancy.api.canvas.CanvasPenTool;
import com.astune.gyromancy.api.ink.InkType;
import com.astune.gyromancy.api.ink.PenProperties;
import com.astune.gyromancy.registry.ModDataComponents;
import com.astune.gyromancy.symbol.ManaPixelDetector;
import com.astune.painter.api.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A pen that reads ink from the offhand {@link InkBottleItem} and paints on canvas blocks.
 *
 * <p>Color, mana value, and effect layers come from the {@link InkType} referenced
 * by the offhand ink bottle's {@code INK_TYPE} data component. Brush size and drawing
 * speed come from this item's {@code PEN_PROPERTIES} component.
 *
 * <p>Each successful paint action decrements {@code INK_REMAINING} on the offhand bottle.
 * When the bottle runs out, painting stops until a fresh bottle is provided.
 */
public class PenItem extends Item implements IPaintProvider, CanvasPenTool {

    private static final int DEFAULT_CANVAS_COLOR = 0xFF24132F;
    private static final int DEFAULT_CANVAS_EFFECT = 1;
    private static final double DEFAULT_BRUSH_DIAMETER = 1.0 / 32.0;
    private static final double STEP = 0.02;
    private static final String MANA_KEY = ManaPixelDetector.MANA_EFFECT_KEY;

    public PenItem() {
        super(new Properties()
                .stacksTo(1)
                .component(ModDataComponents.PEN_PROPERTIES.get(), PenProperties.DEFAULT)
                .component(com.astune.painter.registry.ModDataComponents.CURRENT_COLOR.get(), 0xFFFFFFFF)
                .component(com.astune.painter.registry.ModDataComponents.BRUSH_SIZE.get(), DEFAULT_BRUSH_DIAMETER)
                .component(com.astune.painter.registry.ModDataComponents.FEATHER_STRENGTH.get(), 0.0f)
                .component(com.astune.painter.registry.ModDataComponents.OPACITY.get(), 1.0f)
                .component(com.astune.painter.registry.ModDataComponents.BLEND_MODE.get(), BlendMode.OVERWRITE.name())
                .component(com.astune.painter.registry.ModDataComponents.STEP_SIZE.get(), STEP));
        PaintProviders.register(this, this);
    }

    /**
     * Freehand behavior used by the entity canvas editor. Subclasses can
     * override this independently from Painter's world-canvas provider.
     */
    @Override
    public Optional<Stroke> canvasStroke(ItemStack stack, Player player) {
        InkType ink = resolveOffhandInk(player);
        if (ink == null) {
            return Optional.empty();
        }
        return Optional.of(new Stroke(DEFAULT_CANVAS_COLOR, ink.getManaValue()));
    }

    // ═══════════════════════════════════════════════════════════════
    // IPaintProvider — color
    // ═══════════════════════════════════════════════════════════════

    @Nullable
    @Override
    public Integer getColor(ItemStack stack, Player player, Level level,
                            BlockPos pos, CanvasFace face, int pixelX, int pixelY) {
        InkType ink = resolveOffhandInk(player);
        return ink != null ? ink.getColor() : null;
    }

    // ═══════════════════════════════════════════════════════════════
    // IPaintProvider — pattern (circle brush from PenProperties)
    // ═══════════════════════════════════════════════════════════════

    @Nullable
    @Override
    public PaintPattern getPattern(ItemStack stack, Player player, Level level,
                                   BlockPos pos, Vec3 hitLoc) {
        InkType ink = resolveOffhandInk(player);
        if (ink == null) return null;

        PenProperties props = stack.getOrDefault(ModDataComponents.PEN_PROPERTIES.get(), PenProperties.DEFAULT);
        double diameter = props.brushSize();
        if (diameter <= 0) return null;

        final double radius = diameter / 2.0;
        final int manaValue = ink.getManaValue();
        final Map<String, Integer> effectKeys = ink.getEffectKeys();

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
                if (Math.sqrt(cx * cx + cy * cy) > radius) return null;
                return ink.getColor();
            }

            @Override
            public Map<String, Integer> getEffectValues(double dx, double dy) {
                double cx = dx - radius;
                double cy = dy - radius;
                if (Math.sqrt(cx * cx + cy * cy) > radius) {
                    return java.util.Collections.emptyMap();
                }
                Map<String, Integer> effects = new HashMap<>();
                effects.put(MANA_KEY, manaValue);
                effects.putAll(effectKeys);
                return effects;
            }
        });
    }

    @Override
    public Double getStep() {
        return STEP;
    }

    // ═══════════════════════════════════════════════════════════════
    // Paint distance tracking + ink consumption
    // ═══════════════════════════════════════════════════════════════

    private Vec3 lastHitLoc = null;
    private int consumeCounter = 0;

    @Override
    public boolean shouldPaint(Player player, @Nullable BlockHitResult result) {
        if (!(player.level().isClientSide
                && net.minecraft.client.Minecraft.getInstance().options.keyUse.isDown())) {
            return false;
        }
        if (result == null) return false;

        // Distance gate
        if (lastHitLoc == null) {
            lastHitLoc = result.getLocation();
        } else if (lastHitLoc.distanceTo(result.getLocation()) <= getStep()) {
            return false;
        }
        lastHitLoc = result.getLocation();

        // Check and consume ink from offhand
        ItemStack offhand = player.getOffhandItem();
        if (!InkBottleItem.hasInk(offhand)) {
            return false;
        }

        if (consumeCounter % 10 == 0) {
            InkBottleItem.consume(offhand, 1);
        }
        consumeCounter = (consumeCounter + 1) % 10;
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    @Nullable
    private static InkType resolveOffhandInk(Player player) {
        ItemStack offhand = player.getOffhandItem();
        return InkBottleItem.hasInk(offhand) ? InkBottleItem.resolveInk(offhand) : null;
    }
}
