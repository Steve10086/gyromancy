package com.astune.gyromancy.item;

import com.astune.gyromancy.api.canvas.StampCanvasMaterial;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.network.StampEditorSnapshotPacket;
import com.astune.gyromancy.registry.ModDataComponents;
import com.astune.painter.api.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;

public class StampItem extends Item implements IPaintProvider {
    private static final String MANA_KEY = "gyromancy:mana";
    private static final int MANA_VALUE = 10;

    public StampItem() {
        super(new Properties()
                .stacksTo(1)
                .component(ModDataComponents.STAMP_CANVAS.get(), CanvasDocument.blank(1, 1))
                .component(ModDataComponents.STAMP_MATERIAL.get(), StampCanvasMaterial.DEFAULT)
                .component(ModDataComponents.STAMP_REVISION.get(), 0));
        PaintProviders.register(this, this);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                CanvasDocument document = stack.getOrDefault(
                        ModDataComponents.STAMP_CANVAS.get(), CanvasDocument.blank(1, 1));
                StampCanvasMaterial material = stack.getOrDefault(
                        ModDataComponents.STAMP_MATERIAL.get(), StampCanvasMaterial.DEFAULT);
                int revision = stack.getOrDefault(ModDataComponents.STAMP_REVISION.get(), 0);
                PacketDistributor.sendToPlayer(serverPlayer,
                        new StampEditorSnapshotPacket(hand, revision, document, material));
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
        PatternData data = patternData(player.getMainHandItem());
        if (data == null) return;
        double faceW = data.width();
        double faceH = data.height();
        if (faceW <= 0 || faceH <= 0) return;

        int pw = data.pixelWidth(), ph = data.pixelHeight();
        Direction face = result.getDirection();
        Vec3[] axes = tangents(face);
        Vec3 origin = result.getLocation().subtract(axes[0].scale(faceW / 2)).subtract(axes[1].scale(faceH / 2));
        int[] pixels = data.pixels();

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
        PatternData data = patternData(brush);
        if (data == null) return null;
        double faceW = data.width();
        double faceH = data.height();
        if (faceW <= 0 || faceH <= 0) return null;

        final int pw = data.pixelWidth();
        final int ph = data.pixelHeight();
        final int[] pixels = data.pixels();

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

            @Override
            public Map<String, Integer> getEffectValues(double dx, double dy) {
                int x = (int)(dx / faceW * pw);
                int y = (int)(dy / faceH * ph);
                if (x < 0 || x >= pw || y < 0 || y >= ph) return null;
                int c = pixels[y * pw + x];
                return ((c >> 24) & 0xFF) == 0 ? java.util.Collections.emptyMap() : java.util.Collections.singletonMap(MANA_KEY, MANA_VALUE);
            }
        });
    }

    private static PatternData patternData(ItemStack stack) {
        CanvasDocument document = stack.get(ModDataComponents.STAMP_CANVAS.get());
        if (document != null) {
            // CanvasDocument uses a top-left raster origin, while Painter's
            // pattern coordinates grow upward from the stamp's bottom edge.
            int[] pixels = document.colorsBottomToTop();
            boolean empty = true;
            for (int pixel : pixels) {
                if (((pixel >>> 24) & 0xFF) != 0) {
                    empty = false;
                    break;
                }
            }
            if (!empty) {
                return new PatternData(
                        document.physicalWidth(),
                        document.physicalHeight(),
                        document.resolutionWidth(),
                        document.resolutionHeight(),
                        pixels);
            }
        }

        // Compatibility with stamps created before the private canvas component.
        CanvasFace face = stack.get(ModDataComponents.STAMP_FACE.get());
        if (face == null || face.pixels().isEmpty()) return null;
        PixelMatrix matrix = face.pixels();
        return new PatternData(
                face.corner0().distanceTo(face.corner1()),
                face.corner0().distanceTo(face.corner3()),
                matrix.getWidth(),
                matrix.getHeight(),
                matrix.getPixels().clone());
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
    public Double getStep() { return 0.02; }

    // ── Tooltip ──────────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.gyromancy.stamp.mode",
                Component.translatable("item.gyromancy.stamp.mode.default")));

        PatternData data = patternData(stack);
        if (data != null) {
            tooltip.add(Component.translatable("item.gyromancy.stamp.has_data",
                    data.pixelWidth(), data.pixelHeight()));
        } else {
            tooltip.add(Component.translatable("item.gyromancy.stamp.empty"));
        }
        tooltip.add(Component.translatable("item.gyromancy.stamp.help"));
    }

    private record PatternData(
            double width,
            double height,
            int pixelWidth,
            int pixelHeight,
            int[] pixels
    ) {}
}
