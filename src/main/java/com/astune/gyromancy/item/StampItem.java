package com.astune.gyromancy.item;

import com.astune.gyromancy.api.canvas.CanvasStampTool;
import com.astune.gyromancy.api.canvas.CanvasEditorTool.EditorContext;
import com.astune.gyromancy.api.canvas.StampCanvasMaterial;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.client.canvas.CanvasStampRaster;
import com.astune.gyromancy.network.StampEditorSnapshotPacket;
import com.astune.gyromancy.registry.ModDataComponents;
import com.astune.painter.api.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class StampItem extends Item implements IPaintProvider, CanvasStampTool {
    private static final String MANA_KEY = "gyromancy:mana";
    private static final int MANA_VALUE = 10;
    private static final double MIN_EDITOR_SIZE = 0.125;
    private static final double MAX_EDITOR_SIZE = 8.0;
    private static final double EDITOR_RESIZE_PIXELS_PER_DOUBLING = 96.0;

    private boolean editorRotateKeyHeld;
    private boolean editorResizeModifierHeld;
    private boolean editorResizing;
    private boolean editorErasePreview;
    private double editorRotationDegrees;
    private double editorSizeMultiplier = 1.0;
    private double editorResizeAnchorMouseX;
    private double editorResizeAnchorSize;

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
    public Optional<CanvasDocument> canvasStamp(ItemStack stack, Player player) {
        CanvasDocument document = stack.get(ModDataComponents.STAMP_CANVAS.get());
        if (document == null) return Optional.empty();
        int[] colors = document.colors();
        int[] effects = document.strokeEffects();
        for (int index = 0; index < colors.length; index++) {
            if ((colors[index] >>> 24) != 0 || effects[index] != 0) {
                return Optional.of(document);
            }
        }
        return Optional.empty();
    }

    @Override
    public void renderEditorPreview(EditorContext context,
                                    ItemStack stack,
                                    Player player,
                                    GuiGraphics graphics,
                                    double mouseX,
                                    double mouseY,
                                    int canvasLeft,
                                    int canvasTop,
                                    int canvasWidth,
                                    int canvasHeight) {
        CanvasDocument stamp = canvasStamp(stack, player).orElse(null);
        if (stamp == null || !context.isOverCanvas(mouseX, mouseY)) return;

        int rasterWidth = context.rasterWidth();
        int rasterHeight = context.rasterHeight();
        int[] center = context.canvasPixelAt(mouseX, mouseY);
        int[] preview = new int[rasterWidth * rasterHeight];
        CanvasStampRaster.visit(
                stamp,
                context.physicalWidth(),
                context.physicalHeight(),
                rasterWidth,
                rasterHeight,
                center[0],
                center[1],
                editorRotationDegrees,
                editorSizeMultiplier,
                (x, y, color, effect) -> preview[y * rasterWidth + x] =
                        editorPreviewColor(color, effect, editorErasePreview));
        context.renderPreview(
                graphics,
                preview,
                canvasLeft,
                canvasTop,
                canvasWidth,
                canvasHeight);
    }

    @Override
    public boolean editorMouseClicked(EditorContext context,
                                      ItemStack stack,
                                      Player player,
                                      double mouseX,
                                      double mouseY,
                                      int button) {
        if (!context.carriedItemEmpty()
                || !context.isOverCanvas(mouseX, mouseY)
                || (button != 0 && button != 1)) {
            return false;
        }
        CanvasDocument stamp = canvasStamp(stack, player).orElse(null);
        if (button == 0 && editorResizeModifierHeld && stamp != null) {
            context.beginToolAction(0);
            editorResizing = true;
            editorResizeAnchorMouseX = mouseX;
            editorResizeAnchorSize = editorSizeMultiplier;
            return true;
        }
        if (stamp == null) return true;

        editorErasePreview = button == 1;
        applyEditorStamp(context, mouseX, mouseY, button, stamp);
        return true;
    }

    @Override
    public boolean editorMouseDragged(EditorContext context,
                                      ItemStack stack,
                                      Player player,
                                      double mouseX,
                                      double mouseY,
                                      int button,
                                      double dragX,
                                      double dragY) {
        if (!editorResizing || button != 0) return false;
        updateEditorSize(mouseX);
        return true;
    }

    @Override
    public boolean editorMouseReleased(EditorContext context,
                                       ItemStack stack,
                                       Player player,
                                       double mouseX,
                                       double mouseY,
                                       int button) {
        if (editorResizing && button == 0) {
            updateEditorSize(mouseX);
            editorResizing = false;
            context.finishToolAction();
            return true;
        }
        if (button == 1) editorErasePreview = false;
        return false;
    }

    @Override
    public boolean editorKeyPressed(EditorContext context,
                                    ItemStack stack,
                                    Player player,
                                    int keyCode,
                                    int scanCode,
                                    int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_R) {
            if (!editorRotateKeyHeld) {
                double direction = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0
                        ? -45.0 : 45.0;
                editorRotationDegrees = normalizeDegrees(
                        editorRotationDegrees + direction);
                editorRotateKeyHeld = true;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_X) {
            editorResizeModifierHeld = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean editorKeyReleased(EditorContext context,
                                     ItemStack stack,
                                     Player player,
                                     int keyCode,
                                     int scanCode,
                                     int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_R && editorRotateKeyHeld) {
            editorRotateKeyHeld = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_X && editorResizeModifierHeld) {
            editorResizeModifierHeld = false;
            return true;
        }
        return false;
    }

    @Override
    public void finishEditorAction(EditorContext context,
                                   ItemStack stack,
                                   Player player) {
        editorResizing = false;
        if (context.isToolActionActive()) context.finishToolAction();
    }

    private void applyEditorStamp(EditorContext context,
                                  double mouseX,
                                  double mouseY,
                                  int button,
                                  CanvasDocument stamp) {
        int[] center = context.canvasPixelAt(mouseX, mouseY);
        context.beginHistoryAction();
        CanvasStampRaster.visit(
                stamp,
                context.physicalWidth(),
                context.physicalHeight(),
                context.rasterWidth(),
                context.rasterHeight(),
                center[0],
                center[1],
                editorRotationDegrees,
                editorSizeMultiplier,
                (x, y, color, effect) -> context.writePixel(
                        x,
                        y,
                        button == 0 ? color : 0,
                        button == 0 ? effect : 0));
        context.finishToolAction();
        context.updateChanged();
        context.updateActionButtons();
    }

    private void updateEditorSize(double mouseX) {
        double exponent = (mouseX - editorResizeAnchorMouseX)
                / EDITOR_RESIZE_PIXELS_PER_DOUBLING;
        editorSizeMultiplier = Math.max(
                MIN_EDITOR_SIZE,
                Math.min(
                        MAX_EDITOR_SIZE,
                        editorResizeAnchorSize * Math.pow(2.0, exponent)));
    }

    private static int editorPreviewColor(int color,
                                          int effect,
                                          boolean erasing) {
        int sourceAlpha = color >>> 24;
        if (sourceAlpha == 0 && effect != 0) sourceAlpha = 0xFF;
        int alpha = Math.max(40, Math.min(128,
                (int) Math.round(sourceAlpha * 0.45)));
        int rgb = erasing
                ? 0x00FF5555
                : (sourceAlpha == 0 ? 0x00FFFFFF : color & 0x00FFFFFF);
        return alpha << 24 | rgb;
    }

    private static double normalizeDegrees(double degrees) {
        double normalized = degrees % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
    }


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
