package com.astune.gyromancy.item;

import com.astune.gyromancy.api.canvas.CanvasPenTool;
import com.astune.gyromancy.api.canvas.CanvasEditorTool;
import com.astune.gyromancy.api.canvas.CanvasEditorTool.EditorContext;
import com.astune.gyromancy.network.CompassRadiusPacket;
import com.astune.gyromancy.registry.ModDataComponents;
import com.astune.painter.api.CanvasFace;
import com.astune.painter.api.IPaintProvider;
import com.astune.painter.api.PaintPattern;
import com.astune.painter.api.PaintProviders;
import com.astune.painter.api.blend.BlendFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A compass that turns the player's view angle into a point on a circle.
 *
 * <p>The first block hit while right-click is held becomes the circle center
 * and its face becomes the circle plane. Painter is then supplied with
 * synthetic block hits on the circumference, keeping its normal brush
 * pipeline responsible for rasterisation and network updates.
 */
public final class CompassItem extends Item implements IPaintProvider, CanvasEditorTool {

    public static final double DEFAULT_RADIUS = 1.0;
    public static final double MIN_RADIUS = 0.1;
    public static final double MAX_RADIUS = 16.0;
    public static final double RADIUS_SCROLL_STEP = 0.1;

    private static final double EPSILON = 1.0E-6;
    private static final double FACE_EPSILON = 1.0E-5;
    private static final double TWO_PI = Math.PI * 2.0;

    private boolean editorStrokeActive;
    private int editorStrokeButton = -1;
    private double editorCenterMouseX;
    private double editorCenterMouseY;
    private double editorLastAngle;
    private double editorLastPointX;
    private double editorLastPointY;
    private boolean editorLastPointValid;

    @Nullable
    private static DrawingState active;

    public CompassItem() {
        super(new Properties()
                .stacksTo(1)
                .component(ModDataComponents.COMPASS_RADIUS.get(), DEFAULT_RADIUS)
                .component(ModDataComponents.COMPASS_PEN.get(), CompassContents.EMPTY));
        PaintProviders.register(this, this);
    }

    public static ItemStack getStoredPen(ItemStack compass) {
        return storedContents(compass).pen();
    }

    public static boolean hasStoredPen(ItemStack compass) {
        return storedContents(compass).pen().getItem() instanceof PenItem;
    }

    public static void setStoredPen(ItemStack compass, ItemStack pen) {
        if (!(compass.getItem() instanceof CompassItem)) return;
        if (!pen.isEmpty() && !(pen.getItem() instanceof PenItem)) return;
        compass.set(ModDataComponents.COMPASS_PEN.get(), storedContents(compass).withPen(pen));
    }

    private static CompassContents storedContents(ItemStack compass) {
        return compass.getOrDefault(ModDataComponents.COMPASS_PEN.get(), CompassContents.EMPTY);
    }

    private static ItemStack storedPen(ItemStack compass) {
        return storedContents(compass).pen();
    }

    @Nullable
    private static PenItem storedPenItem(ItemStack compass) {
        return storedPen(compass).getItem() instanceof PenItem pen ? pen : null;
    }

    private static Optional<CanvasPenTool.Stroke> penStroke(ItemStack compass, Player player) {
        PenItem pen = storedPenItem(compass);
        return pen == null
                ? Optional.empty()
                : pen.canvasStroke(storedPen(compass), player);
    }

    /** Handles right-clicking a carried compass onto a pen or an empty slot. */
    @Override
    public boolean overrideStackedOnOther(ItemStack compass,
                                          Slot slot,
                                          ClickAction action,
                                          Player player) {
        if (action != ClickAction.SECONDARY) return false;

        ItemStack target = slot.getItem();
        if (!hasStoredPen(compass)
                && target.getItem() instanceof PenItem
                && slot.mayPickup(player)) {
            setStoredPen(compass, target);
            slot.set(ItemStack.EMPTY);
            return true;
        }

        ItemStack stored = getStoredPen(compass);
        if (!stored.isEmpty() && target.isEmpty() && slot.mayPlace(stored)) {
            slot.set(stored);
            setStoredPen(compass, ItemStack.EMPTY);
            return true;
        }
        return false;
    }

    private static boolean returnStoredPenToInventory(Player player, ItemStack compass) {
        ItemStack stored = getStoredPen(compass);
        Inventory inventory = player.getInventory();
        if (stored.isEmpty()
                || !canAddToInventory(inventory, stored)
                || !inventory.add(stored)) {
            return false;
        }
        setStoredPen(compass, ItemStack.EMPTY);
        inventory.setChanged();
        player.containerMenu.broadcastChanges();
        return true;
    }

    private static boolean canAddToInventory(Inventory inventory, ItemStack stack) {
        int remaining = stack.getCount();
        for (ItemStack existing : inventory.items) {
            if (existing.isEmpty()
                    || !ItemStack.isSameItemSameComponents(existing, stack)) {
                continue;
            }
            remaining -= Math.max(0, existing.getMaxStackSize() - existing.getCount());
            if (remaining <= 0) return true;
        }
        for (ItemStack existing : inventory.items) {
            if (!existing.isEmpty()) continue;
            remaining -= stack.getMaxStackSize();
            if (remaining <= 0) return true;
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level,
                                                  Player player,
                                                  InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || !hasStoredPen(stack)) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        return returnStoredPenToInventory(player, stack)
                ? InteractionResultHolder.sidedSuccess(stack, false)
                : InteractionResultHolder.fail(stack);
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
                || !hasStoredPen(player.getMainHandItem())
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
        if (storedPenItem(stack) == null) return;
        if (!editorStrokeActive && !context.isOverCanvas(mouseX, mouseY)) return;
        int rasterWidth = context.rasterWidth();
        int rasterHeight = context.rasterHeight();
        double centerMouseX = editorStrokeActive ? editorCenterMouseX : mouseX;
        double centerMouseY = editorStrokeActive ? editorCenterMouseY : mouseY;
        int[] center = context.canvasPixelAt(centerMouseX, centerMouseY);
        double radiusPixels = getCanvasRadius(stack)
                * rasterWidth / (double) context.physicalWidth();
        double lineThickness = 1.0;
        int[] preview = new int[rasterWidth * rasterHeight];
        int minX = Math.max(0,
                (int) Math.floor(center[0] - radiusPixels - lineThickness));
        int maxX = Math.min(rasterWidth,
                (int) Math.ceil(center[0] + radiusPixels + lineThickness + 1.0));
        int minY = Math.max(0,
                (int) Math.floor(center[1] - radiusPixels - lineThickness));
        int maxY = Math.min(rasterHeight,
                (int) Math.ceil(center[1] + radiusPixels + lineThickness + 1.0));
        int previewColor = editorStrokeActive && editorStrokeButton == 1
                ? 0x80FF5555
                : 0x80F6D365;
        double thickness = lineThickness * 0.5;
        for (int y = minY; y < maxY; y++) {
            double deltaY = y + 0.5 - center[1];
            for (int x = minX; x < maxX; x++) {
                double deltaX = x + 0.5 - center[0];
                double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                if (Math.abs(distance - radiusPixels) <= thickness) {
                    preview[y * rasterWidth + x] = previewColor;
                }
            }
        }
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
        CanvasPenTool.Stroke stroke = penStroke(stack, player).orElse(null);
        if (stroke == null) return true;

        context.beginHistoryAction();
        context.beginToolAction(button);
        editorStrokeActive = true;
        editorStrokeButton = button;
        int[] center = context.canvasPixelAt(mouseX, mouseY);
        editorCenterMouseX = context.displayX()
                + center[0] * context.displayWidth() / context.rasterWidth();
        editorCenterMouseY = context.displayY()
                + center[1] * context.displayHeight() / context.rasterHeight();
        editorLastAngle = 0.0;
        editorLastPointValid = false;
        paintEditorAngle(context, stack, player, 0.0, button);
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
        if (!editorStrokeActive
                || !context.isToolActionActive()
                || button != editorStrokeButton) {
            return false;
        }
        double deltaX = mouseX - editorCenterMouseX;
        double deltaY = mouseY - editorCenterMouseY;
        if (deltaX * deltaX + deltaY * deltaY < EPSILON) return true;
        double angle = unwrapEditorAngle(
                Math.atan2(deltaY, deltaX), editorLastAngle);
        paintEditorAngle(context, stack, player, angle, button);
        return true;
    }

    @Override
    public boolean editorMouseReleased(EditorContext context,
                                       ItemStack stack,
                                       Player player,
                                       double mouseX,
                                       double mouseY,
                                       int button) {
        if (!editorStrokeActive || (button != 0 && button != 1)) return false;
        finishEditorAction(context, stack, player);
        return true;
    }

    @Override
    public boolean editorMouseScrolled(EditorContext context,
                                       ItemStack stack,
                                       Player player,
                                       double mouseX,
                                       double mouseY,
                                       double scrollX,
                                       double scrollY) {
        if (!context.isOverViewport(mouseX, mouseY)
                || !context.isShiftDown()) {
            return false;
        }
        double scroll = scrollY != 0.0 ? scrollY : -scrollX;
        if (scroll == 0.0 || !adjustRadius(stack, scroll)) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null) {
            minecraft.gui.setOverlayMessage(stack.getHoverName(), false);
        }
        PacketDistributor.sendToServer(new CompassRadiusPacket(
                player.getInventory().selected,
                getRadius(stack)));
        updateEditorPointForRadius(context, stack);
        return true;
    }

    @Override
    public void finishEditorAction(EditorContext context,
                                   ItemStack stack,
                                   Player player) {
        editorStrokeActive = false;
        editorStrokeButton = -1;
        editorLastPointValid = false;
        if (context.isToolActionActive()) context.finishToolAction();
    }

    private void paintEditorAngle(EditorContext context,
                                  ItemStack stack,
                                  Player player,
                                  double angle,
                                  int button) {
        double radiusPixels = getCanvasRadius(stack)
                * context.displayWidth() / context.physicalWidth();
        double angleDelta = angle - editorLastAngle;
        int rasterWidth = context.rasterWidth();
        int rasterHeight = context.rasterHeight();
        double rasterRadius = getCanvasRadius(stack)
                * rasterWidth / (double) context.physicalWidth();
        double samplingRadius = Math.min(
                Math.max(1.0, rasterRadius),
                Math.hypot(rasterWidth, rasterHeight));
        int sampleCount = Math.max(1, (int) Math.ceil(
                Math.abs(angleDelta) * samplingRadius * 2.0));
        for (int sample = 1; sample <= sampleCount; sample++) {
            double sampleAngle = editorLastAngle
                    + angleDelta * sample / sampleCount;
            paintEditorSample(
                    context,
                    stack,
                    player,
                    sampleAngle,
                    button,
                    radiusPixels,
                    rasterWidth,
                    rasterHeight);
        }
        editorLastAngle = angle;
        context.updateChanged();
        context.updateActionButtons();
    }

    private void paintEditorSample(EditorContext context,
                                   ItemStack stack,
                                   Player player,
                                   double angle,
                                   int button,
                                   double radiusPixels,
                                   int rasterWidth,
                                   int rasterHeight) {
        double pointX = editorCenterMouseX + Math.cos(angle) * radiusPixels;
        double pointY = editorCenterMouseY + Math.sin(angle) * radiusPixels;
        boolean pointValid = context.isOverCanvas(pointX, pointY);
        if (pointValid) {
            int[] point = context.canvasPixelAt(pointX, pointY);
            if (editorLastPointValid) {
                int[] previous = context.canvasPixelAt(
                        editorLastPointX, editorLastPointY);
                context.visitLine(
                        previous[0],
                        previous[1],
                        point[0],
                        point[1],
                        (x, y) -> writeEditorPixel(context, stack, player, x, y, button));
            } else {
                writeEditorPixel(context, stack, player, point[0], point[1], button);
            }
        }
        editorLastPointX = pointX;
        editorLastPointY = pointY;
        editorLastPointValid = pointValid;
    }

    private void writeEditorPixel(EditorContext context,
                                  ItemStack stack,
                                  Player player,
                                  int x,
                                  int y,
                                  int button) {
        CanvasPenTool.Stroke stroke = penStroke(
                stack, player).orElse(null);
        if (stroke == null) return;
        context.writePixel(
                x,
                y,
                button == 0 ? stroke.color() : 0,
                button == 0 ? stroke.effect() : 0);
    }

    private void updateEditorPointForRadius(EditorContext context,
                                             ItemStack stack) {
        if (!editorStrokeActive) return;
        double radiusPixels = getCanvasRadius(stack)
                * context.displayWidth() / context.physicalWidth();
        editorLastPointX = editorCenterMouseX
                + Math.cos(editorLastAngle) * radiusPixels;
        editorLastPointY = editorCenterMouseY
                + Math.sin(editorLastAngle) * radiusPixels;
        editorLastPointValid = context.isOverCanvas(
                editorLastPointX, editorLastPointY);
    }

    private static double unwrapEditorAngle(double angle, double previous) {
        while (angle - previous > Math.PI) angle -= TWO_PI;
        while (angle - previous < -Math.PI) angle += TWO_PI;
        return angle;
    }


    @Nullable
    @Override
    public Integer getColor(ItemStack stack, Player player, Level level,
                            BlockPos pos, CanvasFace face, int pixelX, int pixelY) {
        PenItem pen = storedPenItem(stack);
        return pen == null ? null
                : pen.getColor(storedPen(stack), player, level, pos, face, pixelX, pixelY);
    }

    @Nullable
    @Override
    public PaintPattern getPattern(ItemStack stack, Player player, Level level,
                                   BlockPos pos, Vec3 hitLoc) {
        PenItem pen = storedPenItem(stack);
        return pen == null ? null
                : pen.getPattern(storedPen(stack), player, level, pos, hitLoc);
    }

    @Override
    public Double getStep() {
        ItemStack compass = currentCompassStack();
        PenItem pen = storedPenItem(compass);
        return pen == null ? 0.02 : pen.getStep();
    }

    @Override
    public boolean onPaintTick(ItemStack stack, Player player, Level level) {
        PenItem pen = storedPenItem(stack);
        return pen != null && pen.onPaintTick(storedPen(stack), player, level);
    }

    @Override
    public int getPaintInterval() {
        PenItem pen = storedPenItem(currentCompassStack());
        return pen == null ? 1 : pen.getPaintInterval();
    }

    @Nullable
    @Override
    public BlendFunction getCustomBlendFunction(ItemStack stack) {
        PenItem pen = storedPenItem(stack);
        return pen == null ? null : pen.getCustomBlendFunction(storedPen(stack));
    }

    @Override
    public boolean shouldPaint(Player player) {
        ItemStack compass = compassInMainHand(player);
        PenItem pen = storedPenItem(compass);
        return pen != null && pen.shouldPaint(player);
    }

    @Override
    public boolean shouldPaint(Player player, @Nullable BlockHitResult result) {
        ItemStack compass = compassInMainHand(player);
        PenItem pen = storedPenItem(compass);
        return pen != null && pen.shouldPaint(player, result);
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
        ItemStack compass = compassInMainHand(player);
        PenItem pen = storedPenItem(compass);
        return pen == null
                ? IPaintProvider.super.transformPatternAxes(
                        player, hitLocation, direction, width, height)
                : pen.transformPatternAxes(
                        player, hitLocation, direction, width, height);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        double radius = stack.getOrDefault(ModDataComponents.COMPASS_RADIUS.get(), DEFAULT_RADIUS);
        tooltip.add(Component.translatable("item.gyromancy.compass.radius", radius));
        ItemStack pen = getStoredPen(stack);
        tooltip.add(pen.isEmpty()
                ? Component.translatable("item.gyromancy.compass.pen.empty")
                : Component.translatable("item.gyromancy.compass.pen", pen.getHoverName()));
        tooltip.add(Component.translatable("item.gyromancy.compass.help"));
    }

    @Override
    public Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage(
            ItemStack stack) {
        ItemStack pen = getStoredPen(stack);
        return pen.isEmpty()
                ? Optional.empty()
                : Optional.of(new BundleTooltip(new BundleContents(List.of(pen))));
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

    private static ItemStack currentCompassStack() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null
                ? ItemStack.EMPTY
                : compassInMainHand(minecraft.player);
    }

    private static ItemStack compassInMainHand(Player player) {
        ItemStack stack = player.getMainHandItem();
        return stack.getItem() instanceof CompassItem ? stack : ItemStack.EMPTY;
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
