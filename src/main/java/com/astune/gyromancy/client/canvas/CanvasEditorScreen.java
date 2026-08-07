package com.astune.gyromancy.client.canvas;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.canvas.CanvasEditorTool;
import com.astune.gyromancy.api.canvas.CanvasPenTool;
import com.astune.gyromancy.api.canvas.CanvasStampTool;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.item.CompassItem;
import com.astune.gyromancy.network.CompassRadiusPacket;
import com.astune.gyromancy.network.SubmitCanvasEditPacket;
import com.astune.gyromancy.network.SubmitCanvasInventoryPacket;
import com.mojang.blaze3d.systems.RenderSystem;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Simple pixel editor for an entity-backed canvas. */
public final class CanvasEditorScreen extends AbstractContainerScreen<CanvasEditorMenu>
        implements CanvasEditorTool.EditorContext {
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
    private static final long RUNE_PREVIEW_INTERVAL_NANOS = 75_000_000L;
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
    private CanvasDynamicTexture compassPreviewTexture;
    private CanvasRunePreview runePreview = CanvasRunePreview.empty();
    private CompletableFuture<RunePreviewTaskResult> runePreviewTask;
    private long runePreviewGeneration;
    private long nextRunePreviewNanos;
    private boolean runePreviewDirty = true;
    private int scale;
    private int[] colors;
    private int[] effects;
    private boolean changed;
    private CanvasEditorTool activeEditorTool;
    private boolean toolActionActive;
    private int toolActionButton = -1;
    private boolean viewModifierHeld;
    private boolean panning;
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
        int availableHeight = Math.max(1, inventoryY - TOP_MARGIN - 41);
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
        clearRunePreview();
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
        CanvasRunePreview.RuneMatch hoveredRune = hoveredRune(mouseX, mouseY);
        int secondaryInfoY = viewportY + viewportHeight + 16;
        if (hoveredRune != null) {
            Component runeName = Component.translatable(
                    "symbol." + hoveredRune.symbolId().getNamespace()
                            + "." + hoveredRune.symbolId().getPath());
            graphics.drawString(
                    font,
                    Component.translatable(
                            "screen.gyromancy.canvas.rune_hover", runeName),
                    viewportX - leftPos,
                    secondaryInfoY - topPos,
                    0xFFE8E8E8);
            secondaryInfoY += 11;
        }
        CanvasEditorTool selectedTool = selectedEditorTool();
        if (selectedTool instanceof CanvasStampTool stampTool) {
            graphics.drawString(
                    font,
                    Component.translatable(
                            "screen.gyromancy.canvas.stamp_transform",
                            Math.round(stampTool.editorRotationDegrees()),
                            Math.round(stampTool.editorSizeMultiplier() * 100.0)),
                    viewportX - leftPos,
                    secondaryInfoY - topPos,
                    0xFFD7C79A);
        }
    }

    private void renderCanvas(GuiGraphics graphics, int mouseX, int mouseY) {
        updateRunePreview();
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
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        CanvasEditorTool tool = selectedEditorTool();
        if (tool != null && minecraft != null && minecraft.player != null) {
            tool.renderEditorPreview(
                    this,
                    minecraft.player.getMainHandItem(),
                    minecraft.player,
                    graphics,
                    mouseX,
                    mouseY,
                    canvasLeft,
                    canvasTop,
                    canvasRight - canvasLeft,
                    canvasBottom - canvasTop);
        }
        RenderSystem.disableBlend();
        graphics.disableScissor();
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

    private void ensureCompassPreviewTexture(int width,
                                             int height,
                                             int[] preview) {
        if (compassPreviewTexture == null
                || compassPreviewTexture.width() != width
                || compassPreviewTexture.height() != height) {
            closeCompassPreviewTexture();
            compassPreviewTexture = CanvasDynamicTexture.createOverlay(
                    "editor_compass_preview/" + entityId,
                    width,
                    height,
                    preview,
                    false);
            return;
        }
        compassPreviewTexture.replacePixels(preview);
    }

    private int[] canvasPixelAt(double mouseX,
                                double mouseY,
                                int rasterWidth,
                                int rasterHeight) {
        CanvasViewState.DisplayRect display = displayRect();
        int screenX = Math.min(rasterWidth - 1,
                Math.max(0, (int) ((mouseX - display.x())
                        * rasterWidth / display.width())));
        int matrixX = screenX;
        int y = Math.min(rasterHeight - 1,
                Math.max(0, (int) ((mouseY - display.y())
                        * rasterHeight / display.height())));
        return new int[]{matrixX, y};
    }

    @Override
    public int[] canvasPixelAt(double mouseX, double mouseY) {
        return canvasPixelAt(mouseX, mouseY, rasterWidth(), rasterHeight());
    }

    @Override
    public boolean isOverCanvas(double mouseX, double mouseY) {
        return isOverViewport(mouseX, mouseY) && displayRect().contains(mouseX, mouseY);
    }

    @Override
    public boolean isOverViewport(double mouseX, double mouseY) {
        return mouseX >= viewportX && mouseX < viewportX + viewportWidth
                && mouseY >= viewportY && mouseY < viewportY + viewportHeight;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverInventoryToggle(mouseX, mouseY)) {
            toggleInventory();
            return true;
        }
        CanvasEditorTool tool = selectedEditorTool();
        if (tool != null && minecraft != null && minecraft.player != null) {
            ItemStack stack = minecraft.player.getMainHandItem();
            if (tool.editorViewMouseClicked(this, mouseX, mouseY, button)
                    || tool.editorMouseClicked(
                            this, stack, minecraft.player,
                            mouseX, mouseY, button)) {
                activeEditorTool = tool;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX,
                                double mouseY,
                                int button,
                                double dragX,
                                double dragY) {
        CanvasEditorTool tool = activeEditorTool != null
                ? activeEditorTool : selectedEditorTool();
        if (tool != null && minecraft != null && minecraft.player != null) {
            ItemStack stack = minecraft.player.getMainHandItem();
            if (tool.editorViewMouseDragged(
                    this, mouseX, mouseY, button, dragX, dragY)
                    || tool.editorMouseDragged(
                            this, stack, minecraft.player,
                            mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        CanvasEditorTool tool = activeEditorTool != null
                ? activeEditorTool : selectedEditorTool();
        if (tool != null && minecraft != null && minecraft.player != null) {
            ItemStack stack = minecraft.player.getMainHandItem();
            boolean handled = tool.editorViewMouseReleased(
                    this, mouseX, mouseY, button)
                    || tool.editorMouseReleased(
                            this, stack, minecraft.player,
                            mouseX, mouseY, button);
            if (handled) {
                activeEditorTool = null;
                return true;
            }
        }
        if (activeEditorTool != null) {
            finishStroke();
            activeEditorTool = null;
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
    public boolean mouseScrolled(double mouseX,
                                 double mouseY,
                                 double scrollX,
                                 double scrollY) {
        CanvasEditorTool tool = selectedEditorTool();
        if (tool != null && minecraft != null && minecraft.player != null) {
            ItemStack stack = minecraft.player.getMainHandItem();
            if (tool.editorMouseScrolled(
                    this, stack, minecraft.player,
                    mouseX, mouseY, scrollX, scrollY)) {
                hotbarScroll.reset();
                return true;
            }
            if (tool.editorViewMouseScrolled(
                    this, mouseX, mouseY, scrollX, scrollY)) {
                hotbarScroll.reset();
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
        CanvasEditorTool tool = selectedEditorTool();
        if (tool != null && minecraft != null && minecraft.player != null
                && tool.editorKeyPressed(
                        this,
                        minecraft.player.getMainHandItem(),
                        minecraft.player,
                        keyCode,
                        scanCode,
                        modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        CanvasEditorTool tool = selectedEditorTool();
        if (tool != null && minecraft != null && minecraft.player != null
                && tool.editorKeyReleased(
                        this,
                        minecraft.player.getMainHandItem(),
                        minecraft.player,
                        keyCode,
                        scanCode,
                        modifiers)) {
            return true;
        }
        if (CanvasEditorKeyMappings.matchesViewModifier(keyCode, scanCode)) {
            viewModifierHeld = false;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
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

    @Override
    public boolean isViewModifierActive() {
        return viewModifierHeld
                || CanvasEditorKeyMappings.isViewModifierDown()
                || (CanvasEditorKeyMappings.usesDefaultViewModifier() && hasShiftDown());
    }

    private void finishStroke() {
        CanvasEditorTool tool = activeEditorTool != null
                ? activeEditorTool : selectedEditorTool();
        if (tool != null && minecraft != null && minecraft.player != null) {
            tool.finishEditorAction(
                    this,
                    minecraft.player.getMainHandItem(),
                    minecraft.player);
        } else if (toolActionActive) {
            finishToolAction();
        }
        activeEditorTool = null;
        if (runePreviewDirty) nextRunePreviewNanos = 0L;
        updateActionButtons();
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
        clearRunePreview();
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
        clearRunePreview();
        refreshCanvasTexture();
        updateChanged();
        updateActionButtons();
    }

    @Override
    public void updateChanged() {
        changed = scale != initialScale
                || !Arrays.equals(colors, initialColors)
                || !Arrays.equals(effects, initialEffects);
    }

    @Override
    public void updateActionButtons() {
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
            canvasTexture.replacePixels(runePreview.colorize(colors));
        }
    }

    private void rebuildCanvasTexture() {
        if (canvasTexture != null) {
            canvasTexture.close();
        }
        closeStampPreviewTexture();
        closeCompassPreviewTexture();
        canvasTexture = CanvasDynamicTexture.create(
                "editor/" + entityId,
                scale,
                runePreview.colorize(colors),
                false);
    }

    @Override
    public void invalidateRunePreview() {
        runePreviewGeneration++;
        runePreviewDirty = true;
    }

    private void clearRunePreview() {
        runePreview = CanvasRunePreview.empty();
        invalidateRunePreview();
    }

    private void updateRunePreview() {
        if (runePreviewTask != null && runePreviewTask.isDone()) {
            try {
                RunePreviewTaskResult result = runePreviewTask.join();
                if (result.generation() == runePreviewGeneration) {
                    runePreview = result.preview();
                    refreshCanvasTexture();
                }
            } catch (RuntimeException exception) {
                Gyromancy.LOGGER.warn("Client canvas rune preview failed", exception);
                runePreviewDirty = true;
            } finally {
                runePreviewTask = null;
            }
        }

        long now = System.nanoTime();
        if (!runePreviewDirty || runePreviewTask != null
                || now < nextRunePreviewNanos) {
            return;
        }

        int rasterWidth = CanvasDocument.PIXELS_PER_BLOCK * scale;
        int rasterHeight = CanvasDocument.PIXELS_PER_BLOCK * scale;
        int[] effectSnapshot = effects.clone();
        long generation = runePreviewGeneration;
        runePreviewDirty = false;
        nextRunePreviewNanos = now + RUNE_PREVIEW_INTERVAL_NANOS;
        runePreviewTask = CompletableFuture.supplyAsync(() ->
                new RunePreviewTaskResult(
                        generation,
                        CanvasRunePreview.compile(
                                rasterWidth, rasterHeight, effectSnapshot)));
    }

    private CanvasRunePreview.RuneMatch hoveredRune(
            double mouseX, double mouseY) {
        if (!isOverCanvas(mouseX, mouseY)) return null;
        int rasterWidth = CanvasDocument.PIXELS_PER_BLOCK * scale;
        int rasterHeight = CanvasDocument.PIXELS_PER_BLOCK * scale;
        int[] pixel = canvasPixelAt(
                mouseX, mouseY, rasterWidth, rasterHeight);
        return runePreview.runeAt(pixel[0], pixel[1]).orElse(null);
    }

    private record RunePreviewTaskResult(
            long generation, CanvasRunePreview preview) {}

    private void closeStampPreviewTexture() {
        if (stampPreviewTexture != null) {
            stampPreviewTexture.close();
            stampPreviewTexture = null;
        }
    }

    private void closeCompassPreviewTexture() {
        if (compassPreviewTexture != null) {
            compassPreviewTexture.close();
            compassPreviewTexture = null;
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
    public int entityId() {
        return entityId;
    }

    @Override
    public int physicalWidth() {
        return physicalWidth;
    }

    @Override
    public int physicalHeight() {
        return physicalHeight;
    }

    @Override
    public int rasterWidth() {
        return CanvasDocument.PIXELS_PER_BLOCK * scale;
    }

    @Override
    public int rasterHeight() {
        return CanvasDocument.PIXELS_PER_BLOCK * scale;
    }

    @Override
    public double displayX() {
        return displayRect().x();
    }

    @Override
    public double displayY() {
        return displayRect().y();
    }

    @Override
    public double displayWidth() {
        return displayRect().width();
    }

    @Override
    public double displayHeight() {
        return displayRect().height();
    }

    @Override
    public boolean carriedItemEmpty() {
        return menu.getCarried().isEmpty();
    }

    @Override
    public void beginHistoryAction() {
        history.beginAction();
    }

    @Override
    public void beginToolAction(int button) {
        toolActionActive = true;
        toolActionButton = button;
    }

    @Override
    public boolean isToolActionActive() {
        return toolActionActive;
    }

    @Override
    public int toolActionButton() {
        return toolActionButton;
    }

    @Override
    public void finishToolAction() {
        history.commitAction();
        toolActionActive = false;
        toolActionButton = -1;
        updateChanged();
        updateActionButtons();
    }

    @Override
    public void beginPenStroke(CanvasPenTool.Stroke stroke, int button) {
        beginToolAction(button);
    }

    @Override
    public void writePixel(int x, int y, int color, int effect) {
        if (x < 0 || x >= rasterWidth() || y < 0 || y >= rasterHeight()) return;
        int index = y * rasterWidth() + x;
        if (colors[index] == color && effects[index] == effect) return;
        history.recordChange(index, colors[index], effects[index], color, effect);
        colors[index] = color;
        effects[index] = effect;
        invalidateRunePreview();
        if (canvasTexture != null) canvasTexture.setCanvasPixel(x, y, color);
    }

    @Override
    public void visitLine(int startX,
                          int startY,
                          int endX,
                          int endY,
                          CanvasEditorTool.EditorContext.PixelVisitor visitor) {
        CanvasStrokeInterpolator.visitLine(startX, startY, endX, endY, visitor::accept);
    }

    @Override
    public void renderToolPreview(GuiGraphics graphics,
                                  String key,
                                  int[] pixels,
                                  int canvasLeft,
                                  int canvasTop,
                                  int canvasWidth,
                                  int canvasHeight) {
        CanvasDynamicTexture texture;
        if ("stamp".equals(key)) {
            ensureStampPreviewTexture(rasterWidth(), rasterHeight(), pixels);
            texture = stampPreviewTexture;
        } else if ("compass".equals(key)) {
            ensureCompassPreviewTexture(rasterWidth(), rasterHeight(), pixels);
            texture = compassPreviewTexture;
        } else {
            return;
        }
        texture.uploadIfDirty();
        graphics.blit(
                texture.location(),
                canvasLeft,
                canvasTop,
                canvasWidth,
                canvasHeight,
                0.0F,
                0.0F,
                rasterWidth(),
                rasterHeight(),
                rasterWidth(),
                rasterHeight());
    }

    @Override
    public boolean isViewPanning() {
        return panning;
    }

    @Override
    public void beginViewPan() {
        finishStroke();
        panning = true;
    }

    @Override
    public void endViewPan() {
        panning = false;
    }

    @Override
    public void panView(double deltaX, double deltaY) {
        viewState.panBy(deltaX, deltaY, fittedCanvasRect(), viewportRect());
        viewChanged();
    }

    @Override
    public void zoomView(double scroll, double mouseX, double mouseY) {
        double anchorX = Math.max(viewportX,
                Math.min(viewportX + viewportWidth, mouseX));
        double anchorY = Math.max(viewportY,
                Math.min(viewportY + viewportHeight, mouseY));
        viewState.zoomAt(
                scroll,
                anchorX,
                anchorY,
                fittedCanvasRect(),
                viewportRect());
        viewChanged();
    }

    @Override
    public void viewChanged() {
        CanvasEditorTool tool = activeEditorTool != null
                ? activeEditorTool : selectedEditorTool();
        if (tool != null && minecraft != null && minecraft.player != null) {
            tool.editorViewChanged(
                    this,
                    minecraft.player.getMainHandItem(),
                    minecraft.player);
        }
    }

    @Override
    public void syncCompassRadius(ItemStack stack) {
        if (minecraft == null || minecraft.player == null) return;
        minecraft.gui.setOverlayMessage(stack.getHoverName(), false);
        PacketDistributor.sendToServer(new CompassRadiusPacket(
                minecraft.player.getInventory().selected,
                CompassItem.getRadius(stack)));
        hotbarScroll.reset();
    }

    private CanvasEditorTool selectedEditorTool() {
        if (minecraft == null || minecraft.player == null) return null;
        return minecraft.player.getMainHandItem().getItem()
                instanceof CanvasEditorTool tool ? tool : null;
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
        runePreviewGeneration++;
        if (runePreviewTask != null) {
            runePreviewTask.cancel(true);
            runePreviewTask = null;
        }
        if (canvasTexture != null) {
            canvasTexture.close();
            canvasTexture = null;
        }
        closeStampPreviewTexture();
        closeCompassPreviewTexture();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
