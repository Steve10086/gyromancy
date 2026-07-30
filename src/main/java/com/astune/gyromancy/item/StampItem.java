package com.astune.gyromancy.item;

import com.astune.gyromancy.registry.ModDataComponents;
import com.astune.painter.api.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class StampItem extends Item implements IPaintProvider {

    public StampItem() {
        super(new Properties().stacksTo(1));
        PaintProviders.register(this, this);
    }

    static boolean sActive = true;

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (level.isClientSide()) {
                player.displayClientMessage(
                        Component.translatable("item.gyromancy.stamp.mode",
                                Component.translatable("item.gyromancy.stamp.mode.default")),
                        true);
            }
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    private static Vec3 lastHitLoc = null;

    @Override
    public boolean shouldPaint(Player player, BlockHitResult result){
        if (!(player.level().isClientSide && !player.isShiftKeyDown() && Minecraft.getInstance().options.keyUse.isDown())) return false;
        if (lastHitLoc == null) {
            lastHitLoc = result.getLocation();
            spawnStampParticles(player, result);
            return true;
        }
        if (lastHitLoc.distanceTo(result.getLocation()) > getStep()){
            lastHitLoc = result.getLocation();
            spawnStampParticles(player, result);
            return true;
        }

        return false;
    }

    private void spawnStampParticles(Player player, BlockHitResult result) {
        CanvasFace stampFace = player.getMainHandItem().get(ModDataComponents.STAMP_FACE.get());
        if (stampFace == null) return;
        PixelMatrix matrix = stampFace.pixels();
        if (matrix.isEmpty()) return;

        double faceW = stampFace.corner0().distanceTo(stampFace.corner1());
        double faceH = stampFace.corner0().distanceTo(stampFace.corner3());
        if (faceW <= 0 || faceH <= 0) return;

        int pw = matrix.getWidth(), ph = matrix.getHeight();
        Direction face = result.getDirection();
        Vec3[] axes = tangents(face);
        Vec3 origin = result.getLocation().subtract(axes[0].scale(faceW / 2)).subtract(axes[1].scale(faceH / 2));
        int[] pixels = matrix.getPixels();

        for (int i = 0; i < 10; i++) {
            int px = player.level().random.nextInt(pw);
            int py = player.level().random.nextInt(ph);
            int color = pixels[py * pw + px];
            if (((color >> 24) & 0xFF) == 0) continue;
            double dx = (px + 0.5) / pw * faceW;
            double dy = (py + 0.5) / ph * faceH;
            Vec3 pos = origin.add(axes[0].scale(dx)).add(axes[1].scale(dy));
        }
    }

    private static boolean needFlipX(Direction face) {
        return switch (face) {
            case NORTH, WEST -> true;
            default -> false;
        };
    }

    private static boolean needFlipY(Direction face) {
        return switch (face) {
            case SOUTH, NORTH, DOWN, EAST, WEST -> true;
            default -> false;
        };
    }

    // ── IPaintProvider ──────────────────────────────────────────

    @Override
    public Integer getColor(ItemStack brush, Player player, Level level,
                            BlockPos pos, CanvasFace face, int pixelX, int pixelY) {
        return null;
    }

    @Override
    public PaintPattern getPattern(ItemStack brush, Player player, Level level,
                                   BlockPos pos, Vec3 hit) {
        CanvasFace stampFace = brush.get(ModDataComponents.STAMP_FACE.get());
        if (stampFace == null) return null;

        PixelMatrix matrix = stampFace.pixels();
        if (matrix.isEmpty()) return null;

        double faceW = stampFace.corner0().distanceTo(stampFace.corner1());
        double faceH = stampFace.corner0().distanceTo(stampFace.corner3());
        if (faceW <= 0 || faceH <= 0) return null;

        final int pw = matrix.getWidth();
        final int ph = matrix.getHeight();
        final int[] pixels = matrix.getPixels().clone();

        return new PaintPattern(faceW, faceH, new PixelProvider() {
            @Override
            public BlendMode getBlendMode() { return BlendMode.ADD; }

            @Override
            public Integer getPixel(double dx, double dy) {
                int x = (int)(dx / faceW * pw);
                int y = (int)(dy / faceH * ph);
                if (x < 0 || x >= pw || y < 0 || y >= ph) return null;
                int c = pixels[y * pw + x];
                return ((c >> 24) & 0xFF) == 0 ? null : c;
            }
        });
    }

    private static Vec3[] tangents(Direction face) {
        Vec3 u = switch (face) {
            case NORTH, SOUTH, UP, DOWN -> new Vec3(1, 0, 0);
            case EAST, WEST -> new Vec3(0, 0, 1);
        };
        Vec3 v = switch (face) {
            case NORTH, SOUTH, EAST, WEST -> new Vec3(0, -1, 0);
            case UP, DOWN -> new Vec3(0, 0, 1);
        };
        return new Vec3[] { u, v };
    }

    @Override
    public Vec3[] transformPatternAxes(Player player, Vec3 hitPoint, Direction face, double w, double h) {
        Vec3[] axes = tangents(face);
        Vec3 origin = hitPoint.subtract(axes[0].scale(w / 2)).subtract(axes[1].scale(h / 2));
        return new Vec3[] { origin, axes[0], axes[1] };
    }

    @Override
    public Double getStep() { return 0.02; }

    // ── Tooltip ──────────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.gyromancy.stamp.mode",
                Component.translatable("item.gyromancy.stamp.mode.default")));

        CanvasFace face = stack.get(ModDataComponents.STAMP_FACE.get());
        if (face != null && !face.pixels().isEmpty()) {
            PixelMatrix m = face.pixels();
            tooltip.add(Component.translatable("item.gyromancy.stamp.has_data",
                    m.getWidth(), m.getHeight()));
        } else {
            tooltip.add(Component.translatable("item.gyromancy.stamp.empty"));
        }
        tooltip.add(Component.translatable("item.gyromancy.stamp.help"));
    }
}
