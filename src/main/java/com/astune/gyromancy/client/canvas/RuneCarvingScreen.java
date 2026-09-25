package com.astune.gyromancy.client.canvas;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.canvas.Carvable;
import com.astune.gyromancy.api.canvas.CanvasEditorTool;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.network.SubmitRuneCarvingPacket;
import com.astune.gyromancy.rune.RuneCarvingMenu;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.CompletableFuture;

/** Canvas-like rune engraving editor backed by the table's temporary slot. */
public final class RuneCarvingScreen extends AbstractContainerScreen<RuneCarvingMenu>
        implements CanvasEditorTool.EditorContext {
    private static final int MARGIN = 12;
    private static final int TOP_MARGIN = 24;
    private static final int SIDE_GAP = 8;
    private static final double CANVAS_AREA_WIDTH_FRACTION = 0.4D;
    private static final int TOOLBAR_WIDTH = 100;
    private static final int HOTBAR_SLOT_SPACING = 20;
    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int EXPANDED_INVENTORY_ROWS = 4;
    private static final int EXPANDED_INVENTORY_HEIGHT = HOTBAR_HEIGHT * EXPANDED_INVENTORY_ROWS;
    private static final int INVENTORY_TOGGLE_SIZE = 6;
    private static final long RUNE_PREVIEW_INTERVAL_NANOS = 75_000_000L;
    private static final ResourceLocation HOTBAR_SPRITE =
            ResourceLocation.withDefaultNamespace("hud/hotbar");
    private static final ResourceLocation HOTBAR_SELECTION_SPRITE =
            ResourceLocation.withDefaultNamespace("hud/hotbar_selection");

    private final CanvasEditHistory history = new CanvasEditHistory();
    private final CanvasViewport viewportController = new CanvasViewport();
    private final CanvasHotbarScroll hotbarScroll = new CanvasHotbarScroll();
    private final RuneCarvingMenu carvingMenu;
    private CanvasDynamicTexture canvasTexture;
    private CanvasDynamicTexture maskTexture;
    private CanvasDynamicTexture previewTexture;
    private CanvasRunePreview runePreview = CanvasRunePreview.empty();
    private CompletableFuture<RunePreviewTaskResult> runePreviewTask;
    private long runePreviewGeneration;
    private long nextRunePreviewNanos;
    private boolean runePreviewDirty = true;
    private final BitSet runePreviewDirtyRegion = new BitSet();
    private boolean inventoryExpanded;
    private boolean toolActionActive;
    private int toolActionButton = -1;
    private CanvasEditorTool activeEditorTool;
    private boolean changed;
    private boolean pendingSubmission;
    private boolean closing;
    private int submissionSequence;

    private ItemStack observedInput = ItemStack.EMPTY;
    private Carvable.CarvingProperties properties;
    private int physicalWidth = 1;
    private int physicalHeight = 1;
    private int scale;
    private int[] colors = new int[0];
    private int[] effects = new int[0];

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
    private boolean initialized;

    public RuneCarvingScreen(RuneCarvingMenu menu,
                             Inventory inventory,
                             Component title) {
        super(menu, inventory, title);
        this.carvingMenu = menu;
        imageWidth = HOTBAR_WIDTH;
        imageHeight = HOTBAR_HEIGHT;
    }

    @Override
    protected void init() {
        // JEI may temporarily replace this screen and then reuse the same
        // instance when its recipe view closes. removed() marks the screen as
        // closing during that replacement, so re-align the guard with the
        // screen becoming visible again.
        closing = false;
        super.init();
        refreshInput(!initialized);
        layoutPanel();
        carvingMenu.setExpanded(inventoryExpanded);
        undoButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.gyromancy.canvas.undo"),
                        button -> undo())
                .bounds(toolbarX, toolbarY + 28, TOOLBAR_WIDTH, 20).build());
        redoButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.gyromancy.canvas.redo"),
                        button -> redo())
                .bounds(toolbarX, toolbarY + 50, TOOLBAR_WIDTH, 20).build());
        clearButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.gyromancy.canvas.clear"),
                        button -> clearCanvas())
                .bounds(toolbarX, toolbarY + 72, TOOLBAR_WIDTH, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                        button -> onClose())
                .bounds(toolbarX, toolbarY + 94, TOOLBAR_WIDTH, 20).build());
        updateActionButtons();
        initialized = true;
    }

    private void refreshInput(boolean force) {
        ItemStack current = carvingMenu.carvingStack();
        boolean stackChanged = force || !ItemStack.matches(current, observedInput);
        boolean itemIdentityChanged = force
                || current.getItem() != observedInput.getItem()
                || current.getCount() != observedInput.getCount();
        Carvable.CarvingProperties next = null;
        CanvasDocument storedCanvas = null;
        int nextPhysicalWidth = 1;
        int nextPhysicalHeight = 1;
        if (current.getItem() instanceof Carvable carvable) {
            try {
                next = carvable.carvingProperties(current);
                storedCanvas = carvable.carvingCanvas(current);
                if (storedCanvas != null) {
                    nextPhysicalWidth = storedCanvas.physicalWidth();
                    nextPhysicalHeight = storedCanvas.physicalHeight();
                }
            } catch (RuntimeException exception) {
                Gyromancy.LOGGER.warn("Invalid client carving properties for {}",
                        current.getItem(), exception);
            }
        }
        boolean surfaceChanged = !sameProperties(properties, next);
        boolean aspectChanged = physicalWidth != nextPhysicalWidth
                || physicalHeight != nextPhysicalHeight;
        if (!stackChanged && !surfaceChanged && !aspectChanged) return;

        // The server broadcasts the item after every accepted carving action.
        // Its raster is unchanged, while the rune/array cache and therefore
        // ItemStack.matches() are different. Do not reset the local editor or
        // throw away its incremental preview for a cache-only update.
        if (!itemIdentityChanged && !surfaceChanged && !aspectChanged
                && sameLocalRaster(storedCanvas)) {
            observedInput = current.copy();
            return;
        }

        finishStrokeWithoutSubmit();
        properties = next;
        physicalWidth = nextPhysicalWidth;
        physicalHeight = nextPhysicalHeight;
        observedInput = current.copy();
        if (properties == null) {
            scale = 0;
            colors = new int[0];
            effects = new int[0];
        } else {
            scale = properties.resolutionScale();
            int size = CanvasDocument.PIXELS_PER_BLOCK * scale;
            int expected = size * size;
            if (storedCanvas != null
                    && storedCanvas.resolutionScale() == scale
                    && storedCanvas.colors().length == expected
                    && storedCanvas.strokeEffects().length == expected) {
                colors = storedCanvas.colors();
                effects = storedCanvas.strokeEffects();
            } else {
                colors = new int[expected];
                effects = new int[expected];
            }
        }
        changed = false;
        pendingSubmission = false;
        history.clear();
        clearRunePreview();
        rebuildTextures();
        if (width > 0 && height > 0) {
            layoutPanel();
        }
        updateActionButtons();
    }

    private boolean sameLocalRaster(CanvasDocument storedCanvas) {
        if (storedCanvas == null || storedCanvas.resolutionScale() != scale) return false;
        return Arrays.equals(storedCanvas.colors(), colors)
                && Arrays.equals(storedCanvas.strokeEffects(), effects);
    }

    private static boolean sameProperties(Carvable.CarvingProperties first,
                                          Carvable.CarvingProperties second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        return first.resolutionScale() == second.resolutionScale()
                && first.material().equals(second.material())
                && Arrays.equals(first.allowedPixels(), second.allowedPixels());
    }

    private void layoutPanel() {
        int inventoryWidth = HOTBAR_WIDTH;
        int inventoryHeight = inventoryExpanded ? EXPANDED_INVENTORY_HEIGHT : HOTBAR_HEIGHT;
        inventoryX = (width - inventoryWidth) / 2;
        inventoryY = height - inventoryHeight - 8;
        int hotbarRow = inventoryExpanded ? EXPANDED_INVENTORY_ROWS - 1 : 0;
        leftPos = inventoryX;
        topPos = inventoryY + hotbarRow * HOTBAR_HEIGHT;
        inventoryToggleX = inventoryX + inventoryWidth;
        inventoryToggleY = inventoryY + (inventoryHeight - INVENTORY_TOGGLE_SIZE) / 2;

        int canvasAreaWidth = Math.max(1,
                (int) Math.round(width * CANVAS_AREA_WIDTH_FRACTION));
        int maxCanvasWidth = Math.max(1,
                width - MARGIN * 2 - TOOLBAR_WIDTH - SIDE_GAP);
        int availableWidth = Math.max(1,
                Math.min(canvasAreaWidth, maxCanvasWidth));
        int availableHeight = Math.max(1, inventoryY - TOP_MARGIN - 41);
        int contentWidth = availableWidth + SIDE_GAP + TOOLBAR_WIDTH;
        int contentX = Math.max(MARGIN, (width - contentWidth) / 2);
        viewportX = contentX;
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
        panelX = viewportX + (availableWidth - panelWidth) / 2;
        panelY = TOP_MARGIN + (availableHeight - panelHeight) / 2;
        toolbarX = viewportX + availableWidth + SIDE_GAP;
        toolbarY = Math.max(TOP_MARGIN, Math.min(panelY, inventoryY - 122));
        viewportController.layout(fittedCanvasRect(), viewportRect());
    }

    /** Area reserved for JEI's right-side ingredient panel. */
    public Rect2i jeiToolbarArea() {
        return new Rect2i(toolbarX - 3, toolbarY - 3,
                TOOLBAR_WIDTH + 6, 123);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshInput(false);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (isOverInputSlot(mouseX, mouseY)) {
            hoveredSlot = carvingMenu.getSlot(RuneCarvingMenu.INPUT_SLOT);
        }
        renderInputSlot(graphics, mouseX, mouseY);
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
        graphics.fill(toolbarX - 3, toolbarY - 3,
                toolbarX + TOOLBAR_WIDTH + 3, toolbarY + 120, 0xAA202027);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(font, title, width / 2 - leftPos, 8 - topPos, 0xFFFFFFFF);
        if (properties == null) {
            Component empty = Component.translatable("screen.gyromancy.rune_carving.empty");
            graphics.drawString(font, empty, toolbarX + 4 - leftPos,
                    toolbarY + 6 - topPos, 0xFFFF7777);
            return;
        }

        Component resolution = Component.translatable("screen.gyromancy.canvas.resolution",
                CanvasDocument.PIXELS_PER_BLOCK * scale,
                CanvasDocument.PIXELS_PER_BLOCK * scale, scale);
        graphics.drawString(font, resolution,
                viewportX - leftPos, viewportY + viewportHeight + 5 - topPos, 0xFFE8E8E8);
        Component zoom = Component.translatable("screen.gyromancy.canvas.view_zoom",
                Math.round(viewportController.zoom() * 100.0));
        graphics.drawString(font, zoom,
                viewportX + viewportWidth - font.width(zoom) - leftPos,
                viewportY + viewportHeight + 5 - topPos, 0xFFBFBFBF);

        CanvasRunePreview.RuneMatch hovered = hoveredRune(mouseX, mouseY);
        if (hovered != null) {
            Component runeName = Component.translatable(
                    "symbol." + hovered.symbolId().getNamespace()
                            + "." + hovered.symbolId().getPath());
            graphics.drawString(font,
                    Component.translatable("screen.gyromancy.canvas.rune_hover", runeName),
                    viewportX - leftPos, viewportY + viewportHeight + 16 - topPos,
                    0xFFE8E8E8);
        }
        graphics.drawString(font,
                Component.translatable("screen.gyromancy.rune_carving.input"),
                toolbarX + 4 - leftPos, toolbarY - 15 - topPos, 0xFFE8E8E8);
    }

    private void renderCanvas(GuiGraphics graphics, int mouseX, int mouseY) {
        if (properties == null) {
            graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF29242A);
            return;
        }
        updateRunePreview();
        ensureCanvasTexture();
        ensureMaskTexture();
        CanvasViewState.DisplayRect display = displayRect();
        int canvasLeft = (int) Math.floor(display.x());
        int canvasTop = (int) Math.floor(display.y());
        int canvasRight = (int) Math.ceil(display.right());
        int canvasBottom = (int) Math.ceil(display.bottom());
        int drawWidth = canvasRight - canvasLeft;
        int drawHeight = canvasBottom - canvasTop;

        graphics.enableScissor(viewportX, viewportY,
                viewportX + viewportWidth, viewportY + viewportHeight);
        graphics.fill(canvasLeft - 2, canvasTop - 2,
                canvasRight + 2, canvasBottom + 2, 0xFF5B3A29);
        canvasTexture.uploadIfDirty();
        graphics.blit(canvasTexture.location(), canvasLeft, canvasTop,
                drawWidth, drawHeight, 0.0F, 0.0F,
                canvasTexture.width(), canvasTexture.height(),
                canvasTexture.width(), canvasTexture.height());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        maskTexture.uploadIfDirty();
        graphics.blit(maskTexture.location(), canvasLeft, canvasTop,
                drawWidth, drawHeight, 0.0F, 0.0F,
                maskTexture.width(), maskTexture.height(),
                maskTexture.width(), maskTexture.height());

        CanvasEditorTool tool = selectedEditorTool();
        if (tool != null && minecraft != null && minecraft.player != null) {
            tool.renderEditorPreview(this, minecraft.player.getMainHandItem(), minecraft.player,
                    graphics, mouseX, mouseY, canvasLeft, canvasTop, drawWidth, drawHeight);
        }
        RenderSystem.disableBlend();
        graphics.disableScissor();
    }

    private void ensureCanvasTexture() {
        int size = CanvasDocument.PIXELS_PER_BLOCK * scale;
        if (canvasTexture == null || canvasTexture.width() != size || canvasTexture.height() != size) {
            rebuildTextures();
        }
    }

    private void ensureMaskTexture() {
        int size = CanvasDocument.PIXELS_PER_BLOCK * scale;
        if (maskTexture == null || maskTexture.width() != size || maskTexture.height() != size) {
            rebuildMaskTexture();
        }
    }

    private void rebuildTextures() {
        closeTexture(canvasTexture);
        closeTexture(maskTexture);
        closeTexture(previewTexture);
        canvasTexture = null;
        maskTexture = null;
        previewTexture = null;
        if (properties == null || colors.length == 0) return;
        canvasTexture = CanvasDynamicTexture.createWithMaterial(
                "rune_carving/" + carvingMenu.menuId(), scale,
                runePreview.colorize(colors), false, properties.material().texture());
        rebuildMaskTexture();
    }

    private void rebuildMaskTexture() {
        closeTexture(maskTexture);
        maskTexture = null;
        if (properties == null || colors.length == 0) return;
        int[] overlay = new int[colors.length];
        boolean[] allowed = properties.allowedPixels();
        for (int index = 0; index < overlay.length; index++) {
            if (!allowed[index]) overlay[index] = 0x55332222;
        }
        maskTexture = CanvasDynamicTexture.createOverlay(
                "rune_carving_mask/" + carvingMenu.menuId(),
                CanvasDocument.PIXELS_PER_BLOCK * scale,
                CanvasDocument.PIXELS_PER_BLOCK * scale,
                overlay, false);
    }

    private static void closeTexture(CanvasDynamicTexture texture) {
        if (texture != null) texture.close();
    }

    @Override
    public boolean isOverCanvas(double mouseX, double mouseY) {
        return viewportController.isOverCanvas(mouseX, mouseY);
    }

    @Override
    public boolean isOverViewport(double mouseX, double mouseY) {
        return viewportController.isOverViewport(mouseX, mouseY);
    }

    @Override
    public int[] canvasPixelAt(double mouseX, double mouseY) {
        return viewportController.canvasPixelAt(
                mouseX, mouseY, rasterWidth(), rasterHeight());
    }

    private CanvasRunePreview.RuneMatch hoveredRune(double mouseX, double mouseY) {
        if (!isOverCanvas(mouseX, mouseY)) return null;
        int[] pixel = canvasPixelAt(mouseX, mouseY);
        return runePreview.runeAt(pixel[0], pixel[1]).orElse(null);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverInventoryToggle(mouseX, mouseY)) {
            toggleInventory();
            return true;
        }
        if (isOverInputSlot(mouseX, mouseY)) {
            return withInputSlotCoordinates(
                    () -> super.mouseClicked(mouseX, mouseY, button), mouseX, mouseY);
        }
        if (viewportController.mouseClicked(mouseX, mouseY, button, hasControlDown())) {
            finishStroke();
            return true;
        }
        CanvasEditorTool tool = selectedEditorTool();
        if (tool != null && minecraft != null && minecraft.player != null) {
            ItemStack stack = minecraft.player.getMainHandItem();
            if (tool.editorMouseClicked(this, stack, minecraft.player,
                    mouseX, mouseY, button)) {
                activeEditorTool = tool;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (viewportController.mouseDragged(button, dragX, dragY)) return true;
        if (isOverInputSlot(mouseX, mouseY)) {
            return withInputSlotCoordinates(
                    () -> super.mouseDragged(mouseX, mouseY, button, dragX, dragY),
                    mouseX, mouseY);
        }
        CanvasEditorTool tool = activeEditorTool != null ? activeEditorTool : selectedEditorTool();
        if (tool != null && minecraft != null && minecraft.player != null) {
            ItemStack stack = minecraft.player.getMainHandItem();
            if (tool.editorMouseDragged(this, stack, minecraft.player,
                    mouseX, mouseY, button, dragX, dragY)) return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (viewportController.mouseReleased(button)) return true;
        if (isOverInputSlot(mouseX, mouseY)) {
            return withInputSlotCoordinates(
                    () -> super.mouseReleased(mouseX, mouseY, button), mouseX, mouseY);
        }
        CanvasEditorTool tool = activeEditorTool != null ? activeEditorTool : selectedEditorTool();
        if (tool != null && minecraft != null && minecraft.player != null) {
            ItemStack stack = minecraft.player.getMainHandItem();
            boolean handled = tool.editorMouseReleased(this, stack, minecraft.player,
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
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        if (viewportController.mouseScrolled(
                mouseX, mouseY, scrollX, scrollY, hasControlDown())) {
            hotbarScroll.reset();
            return true;
        }
        CanvasEditorTool tool = selectedEditorTool();
        if (tool != null && minecraft != null && minecraft.player != null) {
            ItemStack stack = minecraft.player.getMainHandItem();
            if (tool.editorMouseScrolled(this, stack, minecraft.player,
                    mouseX, mouseY, scrollX, scrollY)) {
                hotbarScroll.reset();
                return true;
            }
        }
        // Modified wheel gestures belong to the canvas/tool controls. Never
        // let an unhandled Shift/Ctrl wheel event fall through to hotbar swap.
        if ((hasShiftDown() || hasControlDown())
                && (scrollX != 0.0 || scrollY != 0.0)) {
            hotbarScroll.reset();
            return true;
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
        // Undo and redo take priority so a tool cannot swallow Ctrl+Z/Ctrl+R.
        if (CanvasEditorKeyMappings.matchesUndo(keyCode, scanCode)) {
            undo();
            return true;
        }
        if (CanvasEditorKeyMappings.matchesRedo(keyCode, scanCode)) {
            redo();
            return true;
        }
        CanvasEditorTool tool = selectedEditorTool();
        if (tool != null && minecraft != null && minecraft.player != null
                && tool.editorKeyPressed(this, minecraft.player.getMainHandItem(),
                minecraft.player, keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        CanvasEditorTool tool = selectedEditorTool();
        if (tool != null && minecraft != null && minecraft.player != null
                && tool.editorKeyReleased(this, minecraft.player.getMainHandItem(),
                minecraft.player, keyCode, scanCode, modifiers)) return true;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        // The menu owns this slot and all of its state. Its visual position is
        // responsive in the canvas toolbar, so render that same slot there
        // instead of also rendering its legacy menu coordinates.
        if (slot.index == RuneCarvingMenu.INPUT_SLOT
                || slot.index == RuneCarvingMenu.RESULT_SLOT) {
            return;
        }
        super.renderSlot(graphics, slot);
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

    private CanvasEditorTool selectedEditorTool() {
        if (minecraft == null || minecraft.player == null) return null;
        return minecraft.player.getMainHandItem().getItem() instanceof CanvasEditorTool tool ? tool : null;
    }

    private void finishStroke() {
        CanvasEditorTool tool = activeEditorTool != null ? activeEditorTool : selectedEditorTool();
        if (tool != null && minecraft != null && minecraft.player != null) {
            tool.finishEditorAction(this, minecraft.player.getMainHandItem(), minecraft.player);
        } else if (toolActionActive) {
            finishToolAction();
        }
        activeEditorTool = null;
        updateActionButtons();
    }

    private void finishStrokeWithoutSubmit() {
        if (toolActionActive) {
            history.commitAction();
            toolActionActive = false;
            toolActionButton = -1;
        }
        activeEditorTool = null;
    }

    private void undo() {
        finishStroke();
        CanvasEditHistory.RasterState state = history.undo(scale, colors, effects);
        if (state != null) {
            applyHistoryState(state);
            updateChanged();
            pendingSubmission = true;
            submitLiveCarving();
        }
        updateActionButtons();
    }

    private void redo() {
        finishStroke();
        CanvasEditHistory.RasterState state = history.redo(scale, colors, effects);
        if (state != null) {
            applyHistoryState(state);
            updateChanged();
            pendingSubmission = true;
            submitLiveCarving();
        }
        updateActionButtons();
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
        refreshTexture();
        updateChanged();
        pendingSubmission = true;
        submitLiveCarving();
        updateActionButtons();
    }

    private void applyHistoryState(CanvasEditHistory.RasterState state) {
        scale = state.scale();
        colors = state.colors();
        effects = state.effects();
        clearRunePreview();
        rebuildTextures();
    }

    private boolean hasCanvasContent() {
        for (int index = 0; index < colors.length; index++) {
            if (colors[index] != 0 || effects[index] != 0) return true;
        }
        return false;
    }

    private void submitLiveCarving() {
        if (closing || properties == null || minecraft == null
                || minecraft.getConnection() == null || !pendingSubmission) return;
        PacketDistributor.sendToServer(new SubmitRuneCarvingPacket(
                carvingMenu.menuId(), ++submissionSequence, scale,
                colors.clone(), effects.clone()));
        pendingSubmission = false;
    }

    private void refreshTexture() {
        if (canvasTexture != null) canvasTexture.replacePixels(runePreview.colorize(colors));
    }

    @Override
    public void invalidateRunePreview() {
        runePreviewGeneration++;
        runePreviewDirty = true;
        markRunePreviewDirtyFully();
    }

    private void clearRunePreview() {
        runePreview = CanvasRunePreview.empty();
        invalidateRunePreview();
    }

    private void invalidateRunePreviewAt(int x, int y) {
        int width = rasterWidth();
        int height = rasterHeight();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    runePreviewDirtyRegion.set(ny * width + nx);
                }
            }
        }
        runePreviewGeneration++;
        runePreviewDirty = true;
    }

    private void markRunePreviewDirtyFully() {
        int size = rasterWidth() * rasterHeight();
        if (size > 0) runePreviewDirtyRegion.set(0, size);
    }

    private void updateRunePreview() {
        if (runePreviewTask != null && runePreviewTask.isDone()) {
            try {
                RunePreviewTaskResult result = runePreviewTask.join();
                if (result.generation() == runePreviewGeneration) {
                    runePreview = result.preview();
                    refreshTexture();
                } else {
                    runePreviewDirty = true;
                    markRunePreviewDirtyFully();
                }
            } catch (RuntimeException exception) {
                Gyromancy.LOGGER.warn("Rune carving preview failed", exception);
                runePreviewDirty = true;
                markRunePreviewDirtyFully();
            } finally {
                runePreviewTask = null;
            }
        }
        long now = System.nanoTime();
        if (!runePreviewDirty || runePreviewTask != null || now < nextRunePreviewNanos) return;
        int width = rasterWidth();
        int height = rasterHeight();
        int[] effectSnapshot = effects.clone();
        BitSet dirtyRegion = (BitSet) runePreviewDirtyRegion.clone();
        runePreviewDirtyRegion.clear();
        CanvasRunePreview previousPreview = runePreview;
        long generation = runePreviewGeneration;
        runePreviewDirty = false;
        nextRunePreviewNanos = now + RUNE_PREVIEW_INTERVAL_NANOS;
        runePreviewTask = CompletableFuture.supplyAsync(() -> new RunePreviewTaskResult(
                generation, CanvasRunePreview.compileIncremental(
                        width, height, effectSnapshot,
                        previousPreview, dirtyRegion)));
    }

    private void toggleInventory() {
        inventoryExpanded = !inventoryExpanded;
        carvingMenu.setExpanded(inventoryExpanded);
        rebuildWidgets();
    }

    private boolean isOverInventoryToggle(double mouseX, double mouseY) {
        return mouseX >= inventoryToggleX && mouseX < inventoryToggleX + INVENTORY_TOGGLE_SIZE
                && mouseY >= inventoryToggleY && mouseY < inventoryToggleY + INVENTORY_TOGGLE_SIZE;
    }

    private void renderInventoryBackground(GuiGraphics graphics) {
        int rows = inventoryExpanded ? EXPANDED_INVENTORY_ROWS : 1;
        for (int row = 0; row < rows; row++) {
            graphics.blitSprite(HOTBAR_SPRITE, inventoryX, inventoryY + row * HOTBAR_HEIGHT,
                    HOTBAR_WIDTH, HOTBAR_HEIGHT);
        }
        int hotbarRow = inventoryExpanded ? EXPANDED_INVENTORY_ROWS - 1 : 0;
        int hotbarY = inventoryY + hotbarRow * HOTBAR_HEIGHT;
        graphics.blitSprite(HOTBAR_SELECTION_SPRITE,
                inventoryX - 1 + minecraft.player.getInventory().selected * HOTBAR_SLOT_SPACING,
                hotbarY - 1, 24, 23);
    }

    private void renderInputSlot(GuiGraphics graphics, int mouseX, int mouseY) {
        Slot slot = carvingMenu.getSlot(RuneCarvingMenu.INPUT_SLOT);
        int x = toolbarX + 38;
        int y = toolbarY + 2;
        int border = isOverInputSlot(mouseX, mouseY) ? 0xFFFFFFFF : 0xFF555555;
        graphics.fill(x - 1, y - 1, x + 17, y + 17, border);
        graphics.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
        if (!slot.getItem().isEmpty()) {
            graphics.renderItem(slot.getItem(), x, y);
            graphics.renderItemDecorations(font, slot.getItem(), x, y);
        }
    }

    private boolean isOverInputSlot(double mouseX, double mouseY) {
        int x = toolbarX + 38;
        int y = toolbarY + 2;
        return mouseX >= x - 1 && mouseX < x + 17
                && mouseY >= y - 1 && mouseY < y + 17;
    }

    /**
     * The canvas toolbar is responsive, while vanilla Slot coordinates are
     * fixed in the menu. Temporarily use the toolbar as the menu origin while
     * invoking AbstractContainerScreen's interaction state machine. No
     * second slot or alternate item-transfer path is involved.
     */
    private boolean withInputSlotCoordinates(MouseAction action,
                                             double mouseX,
                                             double mouseY) {
        Slot slot = carvingMenu.getSlot(RuneCarvingMenu.INPUT_SLOT);
        int oldLeft = leftPos;
        int oldTop = topPos;
        leftPos = (int) Math.round(mouseX) - slot.x;
        topPos = (int) Math.round(mouseY) - slot.y;
        try {
            return action.invoke();
        } finally {
            leftPos = oldLeft;
            topPos = oldTop;
        }
    }

    @FunctionalInterface
    private interface MouseAction {
        boolean invoke();
    }

    private void renderInventoryToggle(GuiGraphics graphics) {
        graphics.blitSprite(HOTBAR_SELECTION_SPRITE, inventoryToggleX, inventoryToggleY,
                INVENTORY_TOGGLE_SIZE, INVENTORY_TOGGLE_SIZE);
        int color = 0xFF404040;
        if (inventoryExpanded) {
            graphics.fill(inventoryToggleX + 1, inventoryToggleY + 2,
                    inventoryToggleX + 5, inventoryToggleY + 3, color);
            graphics.fill(inventoryToggleX + 2, inventoryToggleY + 3,
                    inventoryToggleX + 4, inventoryToggleY + 4, color);
        } else {
            graphics.fill(inventoryToggleX + 2, inventoryToggleY + 2,
                    inventoryToggleX + 4, inventoryToggleY + 3, color);
            graphics.fill(inventoryToggleX + 1, inventoryToggleY + 3,
                    inventoryToggleX + 5, inventoryToggleY + 4, color);
        }
    }

    private CanvasViewState.Rect fittedCanvasRect() {
        return new CanvasViewState.Rect(panelX, panelY, panelWidth, panelHeight);
    }

    private CanvasViewState.Rect viewportRect() {
        return new CanvasViewState.Rect(viewportX, viewportY, viewportWidth, viewportHeight);
    }

    private CanvasViewState.DisplayRect displayRect() {
        return viewportController.displayRect();
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
    public int entityId() {
        return carvingMenu.menuId();
    }

    @Override
    public int rasterWidth() {
        return scale <= 0 ? 0 : CanvasDocument.PIXELS_PER_BLOCK * scale;
    }

    @Override
    public int rasterHeight() {
        return rasterWidth();
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
        return carvingMenu.getCarried().isEmpty();
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
        submitLiveCarving();
    }

    @Override
    public void writePixel(int x, int y, int color, int effect) {
        if (properties == null || !properties.allows(x, y)) return;
        int width = rasterWidth();
        if (width <= 0 || x < 0 || x >= width || y < 0 || y >= width) return;
        int index = y * width + x;
        int normalizedColor = ((color >>> 24) & 0xFF) == 0 ? 0 : color;
        int normalizedEffect = normalizedColor == 0 ? 0 : Math.max(1, effect);
        if (colors[index] == normalizedColor && effects[index] == normalizedEffect) return;
        history.recordChange(index, colors[index], effects[index], normalizedColor, normalizedEffect);
        colors[index] = normalizedColor;
        effects[index] = normalizedEffect;
        pendingSubmission = true;
        invalidateRunePreviewAt(x, y);
        if (canvasTexture != null) canvasTexture.setCanvasPixel(x, y, normalizedColor);
    }

    @Override
    public void visitLine(int startX, int startY, int endX, int endY, PixelVisitor visitor) {
        CanvasStrokeInterpolator.visitLine(startX, startY, endX, endY, visitor::accept);
    }

    @Override
    public void renderPreview(GuiGraphics graphics, int[] pixels,
                              int canvasLeft, int canvasTop,
                              int canvasWidth, int canvasHeight) {
        if (rasterWidth() <= 0) return;
        if (previewTexture == null || previewTexture.width() != rasterWidth()
                || previewTexture.height() != rasterHeight()) {
            closeTexture(previewTexture);
            previewTexture = CanvasDynamicTexture.createOverlay(
                    "rune_carving_preview/" + carvingMenu.menuId(),
                    rasterWidth(), rasterHeight(), pixels, false);
        } else {
            previewTexture.replacePixels(pixels);
        }
        previewTexture.uploadIfDirty();
        graphics.blit(previewTexture.location(), canvasLeft, canvasTop,
                canvasWidth, canvasHeight, 0.0F, 0.0F,
                rasterWidth(), rasterHeight(), rasterWidth(), rasterHeight());
    }

    @Override
    public boolean isViewModifierActive() {
        return hasControlDown();
    }

    @Override
    public boolean isShiftDown() {
        return hasShiftDown();
    }

    @Override
    public void updateChanged() {
        changed = hasCanvasContent();
    }

    @Override
    public void updateActionButtons() {
        if (undoButton != null) undoButton.active = history.canUndo();
        if (redoButton != null) redoButton.active = history.canRedo();
        if (clearButton != null) clearButton.active = hasCanvasContent();
    }

    @Override
    public void onClose() {
        if (closing) return;
        finishStroke();
        if (pendingSubmission) submitLiveCarving();
        closing = true;
        super.onClose();
    }

    @Override
    public void removed() {
        closing = true;
        finishStrokeWithoutSubmit();
        runePreviewGeneration++;
        if (runePreviewTask != null) {
            runePreviewTask.cancel(true);
            runePreviewTask = null;
        }
        closeTexture(canvasTexture);
        closeTexture(maskTexture);
        closeTexture(previewTexture);
        canvasTexture = null;
        maskTexture = null;
        previewTexture = null;
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record RunePreviewTaskResult(long generation, CanvasRunePreview preview) {}
}
