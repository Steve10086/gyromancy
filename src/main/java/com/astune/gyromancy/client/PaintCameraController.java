package com.astune.gyromancy.client;

import com.astune.painter.api.PaintProviders;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

public final class PaintCameraController {

    private static final String KEY_CATEGORY = "key.categories.gyromancy";
    private static final double CAMERA_DISTANCE = 1.6;
    private static final double FACE_EPSILON = 1.0E-5;

    private static final KeyMapping PAINT_CAMERA_KEY = new KeyMapping(
            "key.gyromancy.paint_camera",
            KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            KEY_CATEGORY
    );

    @Nullable
    private static State active;
    private static boolean suppressScreenClose;
    private static boolean stopRequested;
    private static double activeFovDegrees = 70.0;

    private PaintCameraController() {}

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(PAINT_CAMERA_KEY);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (stopRequested) {
            stopRequested = false;
            stop();
            return;
        }

        while (PAINT_CAMERA_KEY.consumeClick()) {
            if (active != null) {
                stop();
            } else {
                tryStart(minecraft);
            }
        }

        if (active == null) return;
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            stop();
            return;
        }
        if (!(minecraft.screen instanceof PaintPointerScreen)) {
            stop();
            return;
        }
        if (player.getInventory().selected != active.selectedSlot || !isPaintProvider(player.getMainHandItem())) {
            stop();
            return;
        }

        lockPlayerRotation(player, active.yaw, active.pitch);
        updatePointerHitResult(minecraft);
    }

    public static void onRenderFramePre(RenderFrameEvent.Pre event) {
        updatePointerHitResult(Minecraft.getInstance());
    }

    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (active == null) return;
        event.setYaw(active.yaw);
        event.setPitch(active.pitch);
        event.setRoll(0.0F);
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (active == null || !event.usedConfiguredFov()) return;
        activeFovDegrees = event.getFOV();
        updatePointerHitResult(Minecraft.getInstance());
    }

    public static void applyCameraPose(Camera camera) {
        if (active == null) return;
        ((CameraPoseAccess) camera).gyromancy$setPaintCameraPose(active.cameraPosition, active.yaw, active.pitch);
    }

    public static boolean isActive() {
        return active != null;
    }

    @Nullable
    public static BlockHitResult hitOnActivePlane(Vec3 hitLocation) {
        if (active == null) return null;
        BlockPos blockPos = BlockPos.containing(hitLocation.subtract(active.normal.scale(FACE_EPSILON)));
        return new BlockHitResult(hitLocation, active.face, blockPos, false);
    }

    @Nullable
    public static Vec3[] transformPatternAxes(Direction direction, Vec3 hitLocation, double width, double height) {
        if (active == null || direction != active.face) return null;
        Vec3 axis1 = active.right.scale(width);
        Vec3 axis2 = active.up.scale(height);
        Vec3 origin = hitLocation.subtract(axis1.scale(0.5)).subtract(axis2.scale(0.5));
        return new Vec3[]{origin, axis1, axis2};
    }

    private static void tryStart(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null) return;
        if (!isPaintProvider(player.getMainHandItem())) return;
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || minecraft.hitResult.getType() != HitResult.Type.BLOCK) return;

        Direction face = hit.getDirection();
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 faceCenter = Vec3.atCenterOf(hit.getBlockPos()).add(normal.scale(0.5));
        Vec3 cameraPosition = faceCenter.add(normal.scale(CAMERA_DISTANCE));
        Rotation rotation = lookAt(cameraPosition, faceCenter);
        PlaneBasis planeBasis = planeBasis(normal);

        activeFovDegrees = minecraft.options.fov().get();
        active = new State(
                face,
                normal,
                faceCenter,
                cameraPosition,
                planeBasis.right,
                planeBasis.up,
                rotation.yaw,
                rotation.pitch,
                player.getInventory().selected
        );
        lockPlayerRotation(player, rotation.yaw, rotation.pitch);

        suppressScreenClose = true;
        minecraft.setScreen(new PaintPointerScreen());
        suppressScreenClose = false;
        minecraft.mouseHandler.releaseMouse();
        updatePointerHitResult(minecraft);
    }

    private static void stop() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.keyUse.setDown(false);
        KeyMapping.set(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_RIGHT), false);
        boolean wasPaintScreen = minecraft.screen instanceof PaintPointerScreen;
        active = null;
        if (wasPaintScreen) {
            suppressScreenClose = true;
            minecraft.setScreen(null);
            suppressScreenClose = false;
        }
        if (wasPaintScreen && minecraft.isWindowActive() && minecraft.player != null) {
            minecraft.mouseHandler.grabMouse();
        }
    }

    private static void requestStop() {
        stopRequested = true;
    }

    private static boolean isPaintProvider(ItemStack stack) {
        return !stack.isEmpty() && PaintProviders.getProvider(stack) != null;
    }

    private static void lockPlayerRotation(LocalPlayer player, float yaw, float pitch) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
    }

    public static void updatePointerHitResult(Minecraft minecraft) {
        if (active == null || minecraft.level == null || minecraft.player == null) return;
        HitResult hit = tracePointer(minecraft, active);
        minecraft.hitResult = hit;
        minecraft.crosshairPickEntity = null;
    }

    private static HitResult tracePointer(Minecraft minecraft, State state) {
        return hitOnActivePlane(pointerPlaneLocation(minecraft, state));
    }

    private static Vec3 pointerPlaneLocation(Minecraft minecraft, State state) {
        double screenWidth = minecraft.getWindow().getScreenWidth();
        double screenHeight = minecraft.getWindow().getScreenHeight();
        double ndcX = screenWidth <= 0.0 ? 0.0 : (minecraft.mouseHandler.xpos() / screenWidth) * 2.0 - 1.0;
        double ndcY = screenHeight <= 0.0 ? 0.0 : 1.0 - (minecraft.mouseHandler.ypos() / screenHeight) * 2.0;
        double aspect = screenHeight <= 0.0 ? 1.0 : screenWidth / screenHeight;
        double halfHeight = CAMERA_DISTANCE * Math.tan(Math.toRadians(activeFovDegrees) / 2.0);
        double halfWidth = halfHeight * aspect;
        return state.faceCenter
                .add(state.right.scale(ndcX * halfWidth))
                .add(state.up.scale(ndcY * halfHeight));
    }

    private static PlaneBasis planeBasis(Vec3 normal) {
        if (normal.y > 0.5) {
            return new PlaneBasis(new Vec3(0.0, 0.0, 1.0), new Vec3(1.0, 0.0, 0.0));
        }
        if (normal.y < -0.5) {
            return new PlaneBasis(new Vec3(0.0, 0.0, 1.0), new Vec3(-1.0, 0.0, 0.0));
        }
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        Vec3 right = up.cross(normal).normalize();
        return new PlaneBasis(right, up);
    }

    private static Rotation lookAt(Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) (-(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG));
        return new Rotation(yaw, pitch);
    }

    public interface CameraPoseAccess {
        void gyromancy$setPaintCameraPose(Vec3 position, float yaw, float pitch);
    }

    private record State(Direction face, Vec3 normal, Vec3 faceCenter, Vec3 cameraPosition,
                         Vec3 right, Vec3 up, float yaw, float pitch, int selectedSlot) {}

    private record Rotation(float yaw, float pitch) {}

    private record PlaneBasis(Vec3 right, Vec3 up) {}

    private static final class PaintPointerScreen extends Screen {
        private PaintPointerScreen() {
            super(Component.translatable("screen.gyromancy.paint_camera"));
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.options.keyUse.matchesMouse(button)) {
                updatePointerHitResult(minecraft);
                minecraft.options.keyUse.setDown(true);
                KeyMapping.set(InputConstants.Type.MOUSE.getOrCreate(button), true);
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            updatePointerHitResult(Minecraft.getInstance());
            return minecraftUseButtonMatches(button);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.options.keyUse.matchesMouse(button)) {
                minecraft.options.keyUse.setDown(false);
                KeyMapping.set(InputConstants.Type.MOUSE.getOrCreate(button), false);
                return true;
            }
            return false;
        }

        private static boolean minecraftUseButtonMatches(int button) {
            return Minecraft.getInstance().options.keyUse.matchesMouse(button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                double direction = scrollY != 0.0 ? scrollY : -scrollX;
                if (direction != 0.0) {
                    minecraft.player.getInventory().swapPaint(direction);
                }
            }
            requestStop();
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            Minecraft minecraft = Minecraft.getInstance();
            if (PAINT_CAMERA_KEY.matches(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                requestStop();
                return true;
            }
            for (int i = 0; i < minecraft.options.keyHotbarSlots.length; i++) {
                if (minecraft.options.keyHotbarSlots[i].matches(keyCode, scanCode)) {
                    if (minecraft.player != null) {
                        minecraft.player.getInventory().selected = i;
                    }
                    requestStop();
                    return true;
                }
            }
            if (minecraft.options.keyUse.matches(keyCode, scanCode)) {
                minecraft.options.keyUse.setDown(true);
                return true;
            }
            return false;
        }

        @Override
        public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.options.keyUse.matches(keyCode, scanCode)) {
                minecraft.options.keyUse.setDown(false);
                return true;
            }
            return false;
        }

        @Override
        public void onClose() {
            if (!suppressScreenClose) {
                stop();
            }
        }
    }
}
