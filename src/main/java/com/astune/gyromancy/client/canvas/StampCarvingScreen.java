package com.astune.gyromancy.client.canvas;

import com.astune.gyromancy.api.canvas.StampCanvasMaterial;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.network.SubmitStampCarvingPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.List;

/**
 * Stamp-local carving editor.
 *
 * <p>This screen intentionally has no container menu, slots, hotbar, player
 * inventory snapshot, or inventory submission path. It reuses only the canvas
 * coordinate, history, zoom, and dynamic-texture helpers from painting mode.
 */
public final class StampCarvingScreen extends Screen {
    private static final int MARGIN = 12;
    private static final int TOP_MARGIN = 36;
    private static final int BOTTOM_LABEL_SPACE = 30;
    private static final int SIDE_GAP = 8;
    private static final int SIDE_TOOLBAR_WIDTH = 84;

    private final InteractionHand hand;
    private final int baseRevision;
    private final int physicalWidth;
    private final int physicalHeight;
    private final int initialScale;
    private final int[] initialColors;
    private final int[] initialEffects;
    private final StampCanvasMaterial material;
    private final CanvasEditHistory history = new CanvasEditHistory();
    private final CanvasViewState viewState = new CanvasViewState();

    private CanvasDynamicTexture carvingTexture;
    private int scale;
    private int[] colors;
    private int[] effects;
    private boolean changed;
    private boolean strokeActive;
    private int strokeButton = -1;
    private int lastStrokeX = -1;
    private int lastStrokeY = -1;
    private boolean panning;
    private boolean viewModifierHeld;
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
    private Button undoButton;
    private Button redoButton;
    private Button clearButton;

    public StampCarvingScreen(InteractionHand hand,
                              int baseRevision,
                              CanvasDocument document,
                              StampCanvasMaterial material) {
        super(Component.translatable("screen.gyromancy.stamp_carving"));
        this.hand = hand;
        this.baseRevision = baseRevision;
        this.physicalWidth = document.physicalWidth();
        this.physicalHeight = document.physicalHeight();
        this.scale = document.resolutionScale();
        this.colors = document.colors();
        this.effects = document.strokeEffects();
        this.initialScale = scale;
        this.initialColors = colors.clone();
        this.initialEffects = effects.clone();
        this.material = material;
    }

    @Override
    protected void init() {
        layoutPanel();
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
        int drawingRight = Math.max(MARGIN + 1,
                width - MARGIN - SIDE_TOOLBAR_WIDTH - SIDE_GAP);
        int availableWidth = Math.max(1, drawingRight - MARGIN);
        int availableHeight = Math.max(
                1, height - TOP_MARGIN - MARGIN - BOTTOM_LABEL_SPACE);
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
        toolbarY = Math.max(TOP_MARGIN,
                Math.min(panelY, height - MARGIN - 112));
        viewState.clamp(fittedCanvasRect(), viewportRect());
    }

    @Override
    public void render(GuiGraphics graphics,
                       int mouseX,
                       int mouseY,
                       float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics,
                                 int mouseX,
                                 int mouseY,
                                 float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        renderCanvas(graphics);
        renderLabels(graphics);
    }

    private void renderLabels(GuiGraphics graphics) {
        int rasterWidth = CanvasDocument.PIXELS_PER_BLOCK * scale;
        int rasterHeight = CanvasDocument.PIXELS_PER_BLOCK * scale;
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF);
        graphics.drawCenteredString(
                font,
                Component.translatable("screen.gyromancy.stamp_carving.controls"),
                width / 2,
                20,
                0xFFBFBFBF);
        graphics.drawString(font,
                Component.translatable("screen.gyromancy.canvas.resolution",
                        rasterWidth, rasterHeight, scale),
                viewportX,
                viewportY + viewportHeight + 5,
                0xFFE8E8E8);
        Component zoomLabel = Component.translatable(
                "screen.gyromancy.canvas.view_zoom",
                Math.round(viewState.zoom() * 100.0));
        graphics.drawString(font,
                zoomLabel,
                viewportX + viewportWidth - font.width(zoomLabel),
                viewportY + viewportHeight + 5,
                0xFFBFBFBF);
    }

    private void renderCanvas(GuiGraphics graphics) {
        ensureCarvingTexture();
        CanvasViewState.DisplayRect display = displayRect();
        int canvasLeft = (int) Math.floor(display.x());
        int canvasTop = (int) Math.floor(display.y());
        int canvasRight = (int) Math.ceil(display.right());
        int canvasBottom = (int) Math.ceil(display.bottom());
        int canvasWidth = canvasRight - canvasLeft;
        int canvasHeight = canvasBottom - canvasTop;

        graphics.enableScissor(
                viewportX, viewportY,
                viewportX + viewportWidth, viewportY + viewportHeight);
        graphics.fill(canvasLeft - 2, canvasTop - 2,
                canvasRight + 2, canvasBottom + 2, 0xFF6E5432);
        carvingTexture.uploadIfDirty();
        graphics.blit(
                carvingTexture.location(),
                canvasLeft,
                canvasTop,
                canvasWidth,
                canvasHeight,
                0.0F,
                0.0F,
                carvingTexture.width(),
                carvingTexture.height(),
                carvingTexture.width(),
                carvingTexture.height());
        graphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverViewport(mouseX, mouseY)
                && isViewModifierActive()) {
            finishStroke();
            panning = true;
            return true;
        }
        if (isOverCanvas(mouseX, mouseY) && (button == 0 || button == 1)) {
            finishStroke();
            history.beginAction();
            strokeActive = true;
            strokeButton = button;
            paint(mouseX, mouseY, button);
            updateActionButtons();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX,
                                double mouseY,
                                int button,
                                double dragX,
                                double dragY) {
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
    public boolean mouseScrolled(double mouseX,
                                 double mouseY,
                                 double scrollX,
                                 double scrollY) {
        if (isOverViewport(mouseX, mouseY) && isViewModifierActive()) {
            double scroll = scrollY != 0.0 ? scrollY : -scrollX;
            if (scroll != 0.0) {
                double anchorX = Math.max(
                        viewportX, Math.min(viewportX + viewportWidth, mouseX));
                double anchorY = Math.max(
                        viewportY, Math.min(viewportY + viewportHeight, mouseY));
                viewState.zoomAt(
                        scroll, anchorX, anchorY,
                        fittedCanvasRect(), viewportRect());
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (CanvasEditorKeyMappings.matchesViewModifier(keyCode, scanCode)) {
            viewModifierHeld = true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (CanvasEditorKeyMappings.matchesViewModifier(keyCode, scanCode)) {
            viewModifierHeld = false;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private boolean paint(double mouseX, double mouseY, int button) {
        if (!strokeActive || button != strokeButton) return false;
        if (!isOverCanvas(mouseX, mouseY)) {
            resetStrokePosition();
            return false;
        }

        int rasterWidth = CanvasDocument.PIXELS_PER_BLOCK * scale;
        int rasterHeight = CanvasDocument.PIXELS_PER_BLOCK * scale;
        CanvasViewState.DisplayRect display = displayRect();
        int screenX = Math.min(rasterWidth - 1,
                Math.max(0, (int) ((mouseX - display.x())
                        * rasterWidth / display.width())));
        int x = CanvasEditorCoordinates.matrixColumnForScreenColumn(
                screenX, rasterWidth);
        int y = Math.min(rasterHeight - 1,
                Math.max(0, (int) ((mouseY - display.y())
                        * rasterHeight / display.height())));
        int startX = lastStrokeX < 0 ? x : lastStrokeX;
        int startY = lastStrokeY < 0 ? y : lastStrokeY;
        CanvasStrokeInterpolator.visitLine(
                startX, startY, x, y,
                (pixelX, pixelY) -> applyCarvingPixel(
                        pixelX, pixelY, rasterWidth, button));
        lastStrokeX = x;
        lastStrokeY = y;
        updateChanged();
        updateActionButtons();
        return true;
    }

    private void applyCarvingPixel(int x, int y, int rasterWidth, int button) {
        int index = y * rasterWidth + x;
        int nextColor = button == 0 ? material.markColor() : 0;
        int nextEffect = button == 0 ? material.markEffect() : 0;
        if (colors[index] == nextColor && effects[index] == nextEffect) return;

        history.recordChange(
                index, colors[index], effects[index], nextColor, nextEffect);
        colors[index] = nextColor;
        effects[index] = nextEffect;
        if (carvingTexture != null) {
            carvingTexture.setCanvasPixel(x, y, nextColor);
        }
    }

    private void changeScale(int direction) {
        finishStroke();
        int next = direction > 0
                ? Math.min(CanvasDocument.maxScale(), scale * 2)
                : Math.max(1, scale / 2);
        if (next == scale) return;

        int previousScale = scale;
        int[] previousColors = colors;
        int[] previousEffects = effects;
        CanvasDocument resized = currentDocument().resample(next);
        scale = next;
        colors = resized.colors();
        effects = resized.strokeEffects();
        rebuildCarvingTexture();
        history.recordResolutionChange(
                previousScale, previousColors, previousEffects,
                scale, colors, effects);
        updateChanged();
        updateActionButtons();
    }

    private CanvasDocument currentDocument() {
        return new CanvasDocument(
                physicalWidth, physicalHeight, scale,
                colors, effects, List.of(), List.of());
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
        rebuildCarvingTexture();
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
        refreshCarvingTexture();
        updateChanged();
        updateActionButtons();
    }

    private void finishStroke() {
        if (!strokeActive) return;
        history.commitAction();
        strokeActive = false;
        strokeButton = -1;
        resetStrokePosition();
        updateActionButtons();
    }

    private void resetStrokePosition() {
        lastStrokeX = -1;
        lastStrokeY = -1;
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

    private boolean isViewModifierActive() {
        return viewModifierHeld
                || CanvasEditorKeyMappings.isViewModifierDown()
                || (CanvasEditorKeyMappings.usesDefaultViewModifier() && hasControlDown());
    }

    private boolean isOverCanvas(double mouseX, double mouseY) {
        return isOverViewport(mouseX, mouseY)
                && displayRect().contains(mouseX, mouseY);
    }

    private boolean isOverViewport(double mouseX, double mouseY) {
        return mouseX >= viewportX && mouseX < viewportX + viewportWidth
                && mouseY >= viewportY && mouseY < viewportY + viewportHeight;
    }

    private void ensureCarvingTexture() {
        int size = CanvasDocument.PIXELS_PER_BLOCK * scale;
        if (carvingTexture == null
                || carvingTexture.width() != size
                || carvingTexture.height() != size) {
            rebuildCarvingTexture();
        }
    }

    private void refreshCarvingTexture() {
        if (carvingTexture != null) carvingTexture.replacePixels(colors);
    }

    private void rebuildCarvingTexture() {
        if (carvingTexture != null) carvingTexture.close();
        carvingTexture = CanvasDynamicTexture.createWithMaterial(
                "stamp/"
                        + (hand == InteractionHand.MAIN_HAND ? "main" : "off")
                        + "/" + baseRevision,
                scale,
                colors,
                false,
                material.texture());
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
        if (minecraft != null) minecraft.setScreen(null);
    }

    private void finishSession() {
        if (sessionSubmitted) return;
        sessionSubmitted = true;
        finishStroke();
        if (!changed || minecraft == null || minecraft.getConnection() == null) return;
        PacketDistributor.sendToServer(new SubmitStampCarvingPacket(
                hand, baseRevision, scale, colors, effects));
    }

    @Override
    public void removed() {
        finishSession();
        if (carvingTexture != null) {
            carvingTexture.close();
            carvingTexture = null;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
