package com.astune.gyromancy.client.canvas;

import com.astune.gyromancy.api.canvas.CanvasPenTool;
import com.astune.gyromancy.api.canvas.CanvasStampTool;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.network.SubmitCanvasEditPacket;
import com.astune.gyromancy.network.SubmitCanvasInventoryPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Simple pixel editor for an entity-backed canvas. */
public final class CanvasEditorScreen extends AbstractContainerScreen<CanvasEditorMenu> {
    private static final int MARGIN = 12;
    private static final int TOP_MARGIN = 24;
    private static final int SIDE_GAP = 8;
    private static final int SIDE_TOOLBAR_WIDTH = 84;
    private static final int HOTBAR_SLOT_SPACING = 20;
    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int EXPANDED_INVENTORY_ROWS = 4;
    private static final int EXPANDED_INVENTORY_HEIGHT =
            HOTBAR_HEIGHT * EXPANDED_INVENTORY_ROWS;
    private static final int INVENTORY_TOGGLE_SIZE = 6;
    private static final double MIN_STAMP_SIZE = 0.125;
    private static final double MAX_STAMP_SIZE = 8.0;
    private static final double STAMP_RESIZE_PIXELS_PER_DOUBLING = 96.0;
    private static final ResourceLocation HOTBAR_SPRITE =
            ResourceLocation.withDefaultNamespace("hud/hotbar");
    private static final ResourceLocation HOTBAR_SELECTION_SPRITE =
            ResourceLocation.withDefaultNamespace("hud/hotbar_selection");

    private final int entityId;
    private final int baseRevision;
    private final int physicalWidth;
    private final int physicalHeight;
    private final int initialScale;
    private final int[] initialColors;
    private final int[] initialEffects;
    private final ItemStack[] initialInventory;
    private final CanvasEditHistory history = new CanvasEditHistory();
    private final CanvasViewState viewState = new CanvasViewState();
    private final CanvasHotbarScroll hotbarScroll = new CanvasHotbarScroll();
    private CanvasDynamicTexture canvasTexture;
    private CanvasDynamicTexture stampPreviewTexture;
    private int scale;
    private int[] colors;
    private int[] effects;
    private boolean changed;
    private boolean strokeActive;
    private CanvasPenTool.Stroke activeStroke;
    private int strokeButton = -1;
    private int lastStrokeX = -1;
    private int lastStrokeY = -1;
    private boolean panning;
    private boolean viewModifierHeld;
    private boolean stampRotateKeyHeld;
    private boolean stampResizeModifierHeld;
    private boolean stampResizing;
    private boolean stampErasePreview;
    private double stampRotationDegrees;
    private double stampSizeMultiplier = 1.0;
    private double stampResizeAnchorMouseX;
    private double stampResizeAnchorSize;
    private boolean inventoryExpanded;
    private boolean sessionSubmitted;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int viewportX;
    private int viewportY;
    private int viewportWidth;
    private int viewportHeight;
    private int toolbarX;
    private int toolbarY;
    private int inventoryX;
    private int inventoryY;
    private int inventoryToggleX;
    private int inventoryToggleY;
    private Button undoButton;
    private Button redoButton;
    private Button clearButton;

    public CanvasEditorScreen(int entityId, int baseRevision, CanvasDocument document) {
        this(createMenu(), entityId, baseRevision, document);
    }

    private CanvasEditorScreen(CanvasEditorMenu menu,
                               int entityId,
                               int baseRevision,
                               CanvasDocument document) {
        super(menu, menu.inventory(), Component.translatable("screen.gyromancy.canvas"));
        this.entityId = entityId;
        this.baseRevision = baseRevision;
        this.physicalWidth = document.physicalWidth();
        this.physicalHeight = document.physicalHeight();
        this.scale = document.resolutionScale();
        this.colors = document.colors();
        this.effects = document.strokeEffects();
        this.initialScale = scale;
        this.initialColors = colors.clone();
        this.initialEffects = effects.clone();
        this.initialInventory = snapshotInventory(menu.inventory());
        this.imageWidth = HOTBAR_WIDTH;
        this.imageHeight = HOTBAR_HEIGHT;
    }

    private static CanvasEditorMenu createMenu() {
        Minecraft minecraft = Minecraft.getInstance();
        return new CanvasEditorMenu(Objects.requireNonNull(
                minecraft.player,
                "Canvas editor requires a client player").getInventory());
    }

    private static ItemStack[] snapshotInventory(Inventory inventory) {
        ItemStack[] snapshot = new ItemStack[Inventory.INVENTORY_SIZE];
        for (int slot = 0; slot < snapshot.length; slot++) {
            snapshot[slot] = inventory.getItem(slot).copy();
        }
        return snapshot;
    }

    @Override
    protected void init() {
        super.init();
        layoutPanel();
        menu.setExpanded(inventoryExpanded);
        undoButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.gyromancy.canvas.undo"),
                        button -> undo())
                .bounds(toolbarX, toolbarY, SIDE_TOOLBAR_WIDTH, 20).build());
        redoButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.gyromancy.canvas.redo"),
                        button -> redo())
                .bounds(toolbarX, toolbarY + 22, SIDE_TOOLBAR_WIDTH, 20).build());
        clearButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.gyromancy.canvas.clear"),
                        button -> clearCanvas())
                .bounds(toolbarX, toolbarY + 44, SIDE_TOOLBAR_WIDTH, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), button -> changeScale(-1))
                .bounds(toolbarX, toolbarY + 68, 38, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> changeScale(1))
                .bounds(toolbarX + 46, toolbarY + 68, 38, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(toolbarX, toolbarY + 92, SIDE_TOOLBAR_WIDTH, 20).build());
        updateActionButtons();
    }

    private void layoutPanel() {
        int inventoryWidth = inventoryBackgroundWidth();
        int inventoryHeight = inventoryBackgroundHeight();
        inventoryX = (width - inventoryWidth) / 2;
        inventoryY = height - inventoryHeight - 8;
        int hotbarRow = inventoryExpanded ? EXPANDED_INVENTORY_ROWS - 1 : 0;
        leftPos = inventoryX;
        topPos = inventoryY + hotbarRow * HOTBAR_HEIGHT;
        inventoryToggleX = inventoryX + inventoryWidth;
        inventoryToggleY = inventoryY + (inventoryHeight - INVENTORY_TOGGLE_SIZE) / 2;

        int drawingRight = Math.max(MARGIN + 1,
                width - MARGIN - SIDE_TOOLBAR_WIDTH - SIDE_GAP);
        int availableWidth = Math.max(1, drawingRight - MARGIN);
        int availableHeight = Math.max(1, inventoryY - TOP_MARGIN - 30);
        viewportX = MARGIN;
        viewportY = TOP_MARGIN;
        viewportWidth = availableWidth;
        viewportHeight = availableHeight;
        double aspect = (double) physicalWidth / physicalHeight;
        panelWidth = availableWidth;
        panelHeight = Math.max(1, (int) Math.round(panelWidth / aspect));
        if (panelHeight > availableHeight) {
            panelHeight = availableHeight;
            panelWidth = Math.max(1, (int) Math.round(panelHeight * aspect));
        }
        panelX = MARGIN + (availableWidth - panelWidth) / 2;
        panelY = TOP_MARGIN + (availableHeight - panelHeight) / 2;
        toolbarX = drawingRight + SIDE_GAP;
        int latestToolbarY = inventoryY - 112;
        toolbarY = Math.max(TOP_MARGIN, Math.min(panelY, latestToolbarY));
        viewState.clamp(fittedCanvasRect(), viewportRect());
    }

    private int inventoryBackgroundWidth() {
        return HOTBAR_WIDTH;
    }

    private int inventoryBackgroundHeight() {
        return inventoryExpanded ? EXPANDED_INVENTORY_HEIGHT : HOTBAR_HEIGHT;
    }

    private void changeScale(int direction) {
        finishStroke();
        int maxScale = CanvasDocument.maxScale();
        int next = direction > 0 ? Math.min(maxScale, scale * 2) : Math.max(1, scale / 2);
        if (next == scale) return;
        int previousScale = scale;
        int[] previousColors = colors;
        int[] previousEffects = effects;
        CanvasDocument resized = currentDocument().resample(next);
        scale = next;
        colors = resized.colors();
        effects = resized.strokeEffects();
        rebuildCanvasTexture();
        history.recordResolutionChange(
                previousScale, previousColors, previousEffects,
                scale, colors, effects);
        updateChanged();
        updateActionButtons();
    }

    private CanvasDocument currentDocument() {
        return new CanvasDocument(physicalWidth, physicalHeight, scale,
                colors, effects, java.util.List.of(), java.util.List.of());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics,
                            float partialTick,
                            int mouseX,
                            int mouseY) {
        renderCanvas(graphics, mouseX, mouseY);
        renderInventoryBackground(graphics);
        renderInventoryToggle(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int rasterWidth = CanvasDocument.PIXELS_PER_BLOCK * scale;
        int rasterHeight = CanvasDocument.PIXELS_PER_BLOCK * scale;

        graphics.drawCenteredString(
                font,
                title,
                width / 2 - leftPos,
                8 - topPos,
                0xFFFFFFFF);
        graphics.drawString(font,
                Component.translatable("screen.gyromancy.canvas.resolution",
                        rasterWidth, rasterHeight, scale),
                viewportX - leftPos,
                viewportY + viewportHeight + 5 - topPos,
                0xFFE8E8E8);
        Component zoomLabel = Component.translatable(
                "screen.gyromancy.canvas.view_zoom",
                Math.round(viewState.zoom() * 100.0));
        graphics.drawString(font,
                zoomLabel,
                viewportX + viewportWidth - font.width(zoomLabel) - leftPos,
                viewportY + viewportHeight + 5 - topPos,
                0xFFBFBFBF);
        if (hasSelectedStampTool()) {
            graphics.drawString(
                    font,
                    Component.translatable(
                            "screen.gyromancy.canvas.stamp_transform",
                            Math.round(stampRotationDegrees),
                            Math.round(stampSizeMultiplier * 100.0)),
                    viewportX - leftPos,
                    viewportY + viewportHeight + 16 - topPos,
                    0xFFD7C79A);
        }
    }

    private void renderCanvas(GuiGraphics graphics, int mouseX, int mouseY) {
        ensureCanvasTexture();
        CanvasViewState.DisplayRect display = displayRect();
        int canvasLeft = (int) Math.floor(display.x());
        int canvasTop = (int) Math.floor(display.y());
        int canvasRight = (int) Math.ceil(display.right());
        int canvasBottom = (int) Math.ceil(display.bottom());

        graphics.enableScissor(
                viewportX, viewportY,
                viewportX + viewportWidth, viewportY + viewportHeight);
        graphics.fill(canvasLeft - 2, canvasTop - 2,
                canvasRight + 2, canvasBottom + 2, 0xFF5B3A29);
        canvasTexture.uploadIfDirty();
        graphics.blit(
                canvasTexture.location(),
                canvasLeft,
                canvasTop,
                canvasRight - canvasLeft,
                canvasBottom - canvasTop,
                0.0F,
                0.0F,
                canvasTexture.width(),
                canvasTexture.height(),
                canvasTexture.width(),
                canvasTexture.height());
        renderStampPreview(
                graphics, mouseX, mouseY,
                canvasLeft, canvasTop,
                canvasRight - canvasLeft,
                canvasBottom - canvasTop);
        graphics.disableScissor();
    }

    private void renderStampPreview(GuiGraphics graphics,
                                    double mouseX,
                                    double mouseY,
                                    int canvasLeft,
                                    int canvasTop,
                                    int canvasWidth,
                                    int canvasHeight) {
        CanvasDocument stamp = selectedStamp();
        if (stamp == null || !isOverCanvas(mouseX, mouseY)) return;

        int rasterWidth = CanvasDocument.PIXELS_PER_BLOCK * scale;
        int rasterHeight = CanvasDocument.PIXELS_PER_BLOCK * scale;
        int[] center = canvasPixelAt(mouseX, mouseY, rasterWidth, rasterHeight);
        int[] preview = new int[rasterWidth * rasterHeight];
        CanvasStampRaster.visit(
                stamp,
                physicalWidth,
                physicalHeight,
                rasterWidth,
                rasterHeight,
                center[0],
                center[1],
                stampRotationDegrees,
                stampSizeMultiplier,
                (x, y, color, effect) -> preview[y * rasterWidth + x] =
                        stampPreviewColor(color, effect, stampErasePreview));

        ensureStampPreviewTexture(rasterWidth, rasterHeight, preview);
        stampPreviewTexture.uploadIfDirty();
        graphics.blit(
                stampPreviewTexture.location(),
                canvasLeft,
                canvasTop,
                canvasWidth,
                canvasHeight,
                0.0F,
                0.0F,
                rasterWidth,
                rasterHeight,
                rasterWidth,
                rasterHeight);
    }

    private void ensureStampPreviewTexture(int width,
                                           int height,
                                           int[] preview) {
        if (stampPreviewTexture == null
                || stampPreviewTexture.width() != width
                || stampPreviewTexture.height() != height) {
            closeStampPreviewTexture();
            stampPreviewTexture = CanvasDynamicTexture.createOverlay(
                    "editor_preview/" + entityId,
                    width,
                    height,
                    preview,
                    false);
            return;
        }
        stampPreviewTexture.replacePixels(preview);
    }

    private static int stampPreviewColor(int color,
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverInventoryToggle(mouseX, mouseY)) {
            toggleInventory();
            return true;
        }
        if (menu.getCarried().isEmpty()
                && button == 0
                && stampResizeModifierHeld
                && selectedStamp() != null
                && isOverCanvas(mouseX, mouseY)) {
            finishStroke();
            stampResizing = true;
            stampResizeAnchorMouseX = mouseX;
            stampResizeAnchorSize = stampSizeMultiplier;
            return true;
        }
        if (menu.getCarried().isEmpty()
                && button == 0 && isOverViewport(mouseX, mouseY)
                && isViewModifierActive()) {
            finishStroke();
            panning = true;
            return true;
        }
        if (menu.getCarried().isEmpty()
                && isOverCanvas(mouseX, mouseY)
                && (button == 0 || button == 1)) {
            finishStroke();
            CanvasPenTool.Stroke selectedStroke = selectedPenStroke();
            if (selectedStroke != null) {
                history.beginAction();
                strokeActive = true;
                activeStroke = selectedStroke;
                strokeButton = button;
                paint(mouseX, mouseY, button);
            } else {
                CanvasDocument stamp = selectedStamp();
                if (stamp != null) {
                    stampErasePreview = button == 1;
                    applyStamp(mouseX, mouseY, button, stamp);
                }
            }
            updateActionButtons();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (stampResizing && button == 0) {
            updateStampSize(mouseX);
            return true;
        }
        if (panning && button == 0) {
            viewState.panBy(dragX, dragY, fittedCanvasRect(), viewportRect());
            return true;
        }
        if (strokeActive) {
            paint(mouseX, mouseY, button);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (stampResizing && button == 0) {
            updateStampSize(mouseX);
            stampResizing = false;
            return true;
        }
        if (button == 1) stampErasePreview = false;
        if (panning && button == 0) {
            panning = false;
            return true;
        }
        if (strokeActive && (button == 0 || button == 1)) {
            finishStroke();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void slotClicked(Slot slot,
                               int slotId,
                               int mouseButton,
                               ClickType type) {
        if (slot != null) slotId = slot.index;
        if (slotId < 0
                || type == ClickType.THROW
                || type == ClickType.CLONE
                || (type == ClickType.SWAP && mouseButton == Inventory.SLOT_OFFHAND)
                || minecraft == null
                || minecraft.player == null) {
            return;
        }
        menu.clicked(slotId, mouseButton, type, minecraft.player);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX,
                                        double mouseY,
                                        int guiLeft,
                                        int guiTop,
                                        int mouseButton) {
        return false;
    }

    private boolean paint(double mouseX, double mouseY, int button) {
        if (!strokeActive || activeStroke == null || button != strokeButton) return false;
        if (!isOverCanvas(mouseX, mouseY)) {
            resetStrokePosition();
            return false;
        }
        int rasterWidth = CanvasDocument.PIXELS_PER_BLOCK * scale;
        int rasterHeight = CanvasDocument.PIXELS_PER_BLOCK * scale;
        CanvasViewState.DisplayRect display = displayRect();
        int screenX = Math.min(rasterWidth - 1,
                Math.max(0, (int) ((mouseX - display.x()) * rasterWidth / display.width())));
        int x = CanvasEditorCoordinates.matrixColumnForScreenColumn(screenX, rasterWidth);
        int y = Math.min(rasterHeight - 1,
                Math.max(0, (int) ((mouseY - display.y()) * rasterHeight / display.height())));
        int startX = lastStrokeX < 0 ? x : lastStrokeX;
        int startY = lastStrokeY < 0 ? y : lastStrokeY;
        CanvasStrokeInterpolator.visitLine(
                startX, startY, x, y,
                (pixelX, pixelY) -> applyStrokePixel(
                        pixelX, pixelY, rasterWidth, button));
        lastStrokeX = x;
        lastStrokeY = y;
        updateChanged();
        updateActionButtons();
        return true;
    }

    private void applyStrokePixel(int x, int y, int rasterWidth, int button) {
        int index = y * rasterWidth + x;
        int nextColor = button == 0 ? activeStroke.color() : 0;
        int nextEffect = button == 0 ? activeStroke.effect() : 0;
        if (colors[index] != nextColor || effects[index] != nextEffect) {
            history.recordChange(index, colors[index], effects[index], nextColor, nextEffect);
            colors[index] = nextColor;
            effects[index] = nextEffect;
            if (canvasTexture != null) {
                canvasTexture.setCanvasPixel(x, y, nextColor);
            }
        }
    }

    private CanvasPenTool.Stroke selectedPenStroke() {
        if (minecraft == null || minecraft.player == null) return null;
        ItemStack selected = minecraft.player.getMainHandItem();
        if (!(selected.getItem() instanceof CanvasPenTool pen)) return null;
        return pen.canvasStroke(selected, minecraft.player).orElse(null);
    }

    private CanvasDocument selectedStamp() {
        if (minecraft == null || minecraft.player == null) return null;
        ItemStack selected = minecraft.player.getMainHandItem();
        if (!(selected.getItem() instanceof CanvasStampTool stamp)) return null;
        return stamp.canvasStamp(selected, minecraft.player).orElse(null);
    }

    private void applyStamp(double mouseX,
                            double mouseY,
                            int button,
                            CanvasDocument stamp) {
        int rasterWidth = CanvasDocument.PIXELS_PER_BLOCK * scale;
        int rasterHeight = CanvasDocument.PIXELS_PER_BLOCK * scale;
        int[] center = canvasPixelAt(
                mouseX, mouseY, rasterWidth, rasterHeight);

        history.beginAction();
        CanvasStampRaster.visit(
                stamp,
                physicalWidth,
                physicalHeight,
                rasterWidth,
                rasterHeight,
                center[0],
                center[1],
                stampRotationDegrees,
                stampSizeMultiplier,
                (x, y, color, effect) -> applyStampPixel(
                        x, y, rasterWidth, button, color, effect));
        history.commitAction();
        updateChanged();
    }

    private void applyStampPixel(int x,
                                 int y,
                                 int rasterWidth,
                                 int button,
                                 int stampColor,
                                 int stampEffect) {
        int index = y * rasterWidth + x;
        int nextColor = button == 0 ? stampColor : 0;
        int nextEffect = button == 0 ? stampEffect : 0;
        if (colors[index] == nextColor && effects[index] == nextEffect) return;
        history.recordChange(
                index, colors[index], effects[index], nextColor, nextEffect);
        colors[index] = nextColor;
        effects[index] = nextEffect;
        if (canvasTexture != null) {
            canvasTexture.setCanvasPixel(x, y, nextColor);
        }
    }

    private int[] canvasPixelAt(double mouseX,
                                double mouseY,
                                int rasterWidth,
                                int rasterHeight) {
        CanvasViewState.DisplayRect display = displayRect();
        int screenX = Math.min(rasterWidth - 1,
                Math.max(0, (int) ((mouseX - display.x())
                        * rasterWidth / display.width())));
        int matrixX = CanvasEditorCoordinates.matrixColumnForScreenColumn(
                screenX, rasterWidth);
        int y = Math.min(rasterHeight - 1,
                Math.max(0, (int) ((mouseY - display.y())
                        * rasterHeight / display.height())));
        return new int[]{matrixX, y};
    }

    private void updateStampSize(double mouseX) {
        double exponent = (mouseX - stampResizeAnchorMouseX)
                / STAMP_RESIZE_PIXELS_PER_DOUBLING;
        stampSizeMultiplier = Math.max(
                MIN_STAMP_SIZE,
                Math.min(MAX_STAMP_SIZE,
                        stampResizeAnchorSize * Math.pow(2.0, exponent)));
    }

    private boolean isOverCanvas(double mouseX, double mouseY) {
        return isOverViewport(mouseX, mouseY) && displayRect().contains(mouseX, mouseY);
    }

    private boolean isOverViewport(double mouseX, double mouseY) {
        return mouseX >= viewportX && mouseX < viewportX + viewportWidth
                && mouseY >= viewportY && mouseY < viewportY + viewportHeight;
    }

    @Override
    public boolean mouseScrolled(double mouseX,
                                 double mouseY,
                                 double scrollX,
                                 double scrollY) {
        if (isOverViewport(mouseX, mouseY)
                && isViewModifierActive()) {
            double scroll = scrollY != 0.0 ? scrollY : -scrollX;
            if (scroll != 0.0) {
                hotbarScroll.reset();
                double anchorX = Math.max(viewportX,
                        Math.min(viewportX + viewportWidth, mouseX));
                double anchorY = Math.max(viewportY,
                        Math.min(viewportY + viewportHeight, mouseY));
                viewState.zoomAt(
                        scroll, anchorX, anchorY,
                        fittedCanvasRect(), viewportRect());
                return true;
            }
        }
        if (minecraft != null && minecraft.player != null) {
            int direction = hotbarScroll.add(scrollX, scrollY);
            if (direction != 0) {
                finishStroke();
                minecraft.player.getInventory().swapPaint(direction);
            }
            return scrollX != 0.0 || scrollY != 0.0;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (CanvasEditorKeyMappings.matchesViewModifier(keyCode, scanCode)) {
            viewModifierHeld = true;
        }
        if (hasSelectedStampTool() && keyCode == GLFW.GLFW_KEY_R) {
            if (!stampRotateKeyHeld) {
                double direction = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0
                        ? -45.0 : 45.0;
                stampRotationDegrees = normalizeDegrees(
                        stampRotationDegrees + direction);
                stampRotateKeyHeld = true;
            }
            return true;
        }
        if (hasSelectedStampTool() && keyCode == GLFW.GLFW_KEY_X) {
            stampResizeModifierHeld = true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (CanvasEditorKeyMappings.matchesViewModifier(keyCode, scanCode)) {
            viewModifierHeld = false;
        }
        if (keyCode == GLFW.GLFW_KEY_R && stampRotateKeyHeld) {
            stampRotateKeyHeld = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_X && stampResizeModifierHeld) {
            stampResizeModifierHeld = false;
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private boolean hasSelectedStampTool() {
        return minecraft != null
                && minecraft.player != null
                && minecraft.player.getMainHandItem().getItem()
                instanceof CanvasStampTool;
    }

    private static double normalizeDegrees(double degrees) {
        double normalized = degrees % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
    }

    @Override
    protected boolean checkHotbarKeyPressed(int keyCode, int scanCode) {
        if (minecraft == null || minecraft.player == null) return false;
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        for (int slot = 0; slot < minecraft.options.keyHotbarSlots.length; slot++) {
            if (minecraft.options.keyHotbarSlots[slot].isActiveAndMatches(key)) {
                finishStroke();
                minecraft.player.getInventory().selected = slot;
                return true;
            }
        }
        return false;
    }

    private boolean isViewModifierActive() {
        return viewModifierHeld
                || CanvasEditorKeyMappings.isViewModifierDown()
                || (CanvasEditorKeyMappings.usesDefaultViewModifier() && hasShiftDown());
    }

    private void finishStroke() {
        if (!strokeActive) return;
        history.commitAction();
        strokeActive = false;
        activeStroke = null;
        strokeButton = -1;
        resetStrokePosition();
        updateActionButtons();
    }

    private void resetStrokePosition() {
        lastStrokeX = -1;
        lastStrokeY = -1;
    }

    private void undo() {
        finishStroke();
        CanvasEditHistory.RasterState state = history.undo(scale, colors, effects);
        if (state != null) {
            applyHistoryState(state);
            updateChanged();
        }
        updateActionButtons();
    }

    private void redo() {
        finishStroke();
        CanvasEditHistory.RasterState state = history.redo(scale, colors, effects);
        if (state != null) {
            applyHistoryState(state);
            updateChanged();
        }
        updateActionButtons();
    }

    private void applyHistoryState(CanvasEditHistory.RasterState state) {
        scale = state.scale();
        colors = state.colors();
        effects = state.effects();
        rebuildCanvasTexture();
    }

    private void clearCanvas() {
        finishStroke();
        history.beginAction();
        for (int index = 0; index < colors.length; index++) {
            if (colors[index] == 0 && effects[index] == 0) continue;
            history.recordChange(index, colors[index], effects[index], 0, 0);
            colors[index] = 0;
            effects[index] = 0;
        }
        history.commitAction();
        refreshCanvasTexture();
        updateChanged();
        updateActionButtons();
    }

    private void updateChanged() {
        changed = scale != initialScale
                || !Arrays.equals(colors, initialColors)
                || !Arrays.equals(effects, initialEffects);
    }

    private void updateActionButtons() {
        if (undoButton != null) undoButton.active = history.canUndo();
        if (redoButton != null) redoButton.active = history.canRedo();
        if (clearButton != null) clearButton.active = hasCanvasContent();
    }

    private boolean hasCanvasContent() {
        for (int index = 0; index < colors.length; index++) {
            if (colors[index] != 0 || effects[index] != 0) return true;
        }
        return false;
    }

    private void renderInventoryBackground(GuiGraphics graphics) {
        Inventory inventory = menu.inventory();
        int backgroundRows = inventoryExpanded ? EXPANDED_INVENTORY_ROWS : 1;
        for (int row = 0; row < backgroundRows; row++) {
            graphics.blitSprite(
                    HOTBAR_SPRITE,
                    inventoryX, inventoryY + row * HOTBAR_HEIGHT,
                    HOTBAR_WIDTH, HOTBAR_HEIGHT);
        }

        int hotbarRow = inventoryExpanded ? EXPANDED_INVENTORY_ROWS - 1 : 0;
        int hotbarBackgroundY = inventoryY + hotbarRow * HOTBAR_HEIGHT;
        graphics.blitSprite(
                HOTBAR_SELECTION_SPRITE,
                inventoryX - 1 + inventory.selected * HOTBAR_SLOT_SPACING,
                hotbarBackgroundY - 1,
                24, 23);
    }

    private void renderInventoryToggle(GuiGraphics graphics) {
        graphics.blitSprite(
                HOTBAR_SELECTION_SPRITE,
                inventoryToggleX, inventoryToggleY,
                INVENTORY_TOGGLE_SIZE, INVENTORY_TOGGLE_SIZE);
        int arrowColor = 0xFF404040;
        if (inventoryExpanded) {
            graphics.fill(
                    inventoryToggleX + 1, inventoryToggleY + 2,
                    inventoryToggleX + 5, inventoryToggleY + 3,
                    arrowColor);
            graphics.fill(
                    inventoryToggleX + 2, inventoryToggleY + 3,
                    inventoryToggleX + 4, inventoryToggleY + 4,
                    arrowColor);
        } else {
            graphics.fill(
                    inventoryToggleX + 2, inventoryToggleY + 2,
                    inventoryToggleX + 4, inventoryToggleY + 3,
                    arrowColor);
            graphics.fill(
                    inventoryToggleX + 1, inventoryToggleY + 3,
                    inventoryToggleX + 5, inventoryToggleY + 4,
                    arrowColor);
        }
    }

    private boolean isOverInventoryToggle(double mouseX, double mouseY) {
        return mouseX >= inventoryToggleX
                && mouseX < inventoryToggleX + INVENTORY_TOGGLE_SIZE
                && mouseY >= inventoryToggleY
                && mouseY < inventoryToggleY + INVENTORY_TOGGLE_SIZE;
    }

    private void toggleInventory() {
        inventoryExpanded = !inventoryExpanded;
        menu.setExpanded(inventoryExpanded);
        rebuildWidgets();
    }

    private void ensureCanvasTexture() {
        int size = CanvasDocument.PIXELS_PER_BLOCK * scale;
        if (canvasTexture == null
                || canvasTexture.width() != size
                || canvasTexture.height() != size) {
            rebuildCanvasTexture();
        }
    }

    private void refreshCanvasTexture() {
        if (canvasTexture != null) {
            canvasTexture.replacePixels(colors);
        }
    }

    private void rebuildCanvasTexture() {
        if (canvasTexture != null) {
            canvasTexture.close();
        }
        closeStampPreviewTexture();
        canvasTexture = CanvasDynamicTexture.create(
                "editor/" + entityId,
                scale,
                colors,
                false);
    }

    private void closeStampPreviewTexture() {
        if (stampPreviewTexture != null) {
            stampPreviewTexture.close();
            stampPreviewTexture = null;
        }
    }

    private CanvasViewState.Rect fittedCanvasRect() {
        return new CanvasViewState.Rect(panelX, panelY, panelWidth, panelHeight);
    }

    private CanvasViewState.Rect viewportRect() {
        return new CanvasViewState.Rect(
                viewportX, viewportY, viewportWidth, viewportHeight);
    }

    private CanvasViewState.DisplayRect displayRect() {
        return viewState.displayRect(fittedCanvasRect());
    }

    @Override
    public void onClose() {
        finishSession();
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    private void finishSession() {
        if (sessionSubmitted) return;
        sessionSubmitted = true;
        finishStroke();
        if (minecraft == null || minecraft.player == null) return;
        menu.finishInteraction(minecraft.player);
        if (minecraft.getConnection() == null) return;

        if (changed) {
            PacketDistributor.sendToServer(new SubmitCanvasEditPacket(
                    entityId, baseRevision, scale, colors.clone(), effects.clone()));
        }
        List<SubmitCanvasInventoryPacket.SlotChange> inventoryChanges =
                inventoryChanges();
        if (!inventoryChanges.isEmpty()) {
            PacketDistributor.sendToServer(new SubmitCanvasInventoryPacket(
                    entityId, inventoryChanges));
        }
    }

    private List<SubmitCanvasInventoryPacket.SlotChange> inventoryChanges() {
        Inventory inventory = menu.inventory();
        List<SubmitCanvasInventoryPacket.SlotChange> changes = new ArrayList<>();
        for (int slot = 0; slot < initialInventory.length; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (!ItemStack.matches(initialInventory[slot], current)) {
                changes.add(new SubmitCanvasInventoryPacket.SlotChange(
                        slot,
                        initialInventory[slot],
                        current));
            }
        }
        return changes;
    }

    @Override
    public void removed() {
        finishSession();
        if (canvasTexture != null) {
            canvasTexture.close();
            canvasTexture = null;
        }
        closeStampPreviewTexture();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
