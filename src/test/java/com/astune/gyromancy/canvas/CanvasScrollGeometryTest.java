package com.astune.gyromancy.canvas;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanvasScrollGeometryTest {
    @Test
    void authoredModelTouchesClickedFaceAndMatchesHitboxForAllFacesAndSizes() throws Exception {
        List<Vector3f> vertices = modelVertices();
        for (BlockPos support : List.of(new BlockPos(12, 34, 56), new BlockPos(-12, -34, -56))) {
            for (Direction face : Direction.values()) {
                for (Direction top : CanvasOrientationTest.topsFor(face)) {
                    for (int height : new int[]{1, 2, 4, 8}) {
                        BlockPos placement = support.relative(face);
                        Vec3 widthAxis = CanvasOrientation.rightAxis(top, face);
                        Vec3 longAxis = CanvasOrientation.topAxis(top);
                        Vec3 origin = CanvasScrollGeometry.origin(placement, face, widthAxis, longAxis);
                        Vec3 faceCenter = Vec3.atCenterOf(support).relative(face, 0.5);
                        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
                        // This is the rotation/scale order consumed by PoseStack in the renderer.
                        Matrix4f render = new Matrix4f()
                                .rotation(CanvasScrollGeometry.rotation(face, widthAxis, longAxis))
                                .scale(1, 1, CanvasScrollGeometry.lengthScale(height));
                        AABB renderedBounds = null;
                        double near = Double.POSITIVE_INFINITY, far = Double.NEGATIVE_INFINITY;
                        double bottom = Double.POSITIVE_INFINITY, top_ = Double.NEGATIVE_INFINITY;
                        double left = Double.POSITIVE_INFINITY, right = Double.NEGATIVE_INFINITY;
                        for (Vector3f vertex : vertices) {
                            Vector3f transformed = render.transformPosition(new Vector3f(vertex));
                            Vec3 world = origin.add(transformed.x, transformed.y, transformed.z);
                            Vec3 relative = world.subtract(faceCenter);
                            near = Math.min(near, relative.dot(normal));
                            far = Math.max(far, relative.dot(normal));
                            bottom = Math.min(bottom, relative.dot(longAxis));
                            top_ = Math.max(top_, relative.dot(longAxis));
                            left = Math.min(left, relative.dot(widthAxis));
                            right = Math.max(right, relative.dot(widthAxis));
                            AABB point = new AABB(world, world);
                            renderedBounds = renderedBounds == null ? point : renderedBounds.minmax(point);
                        }
                        String context = face + "/" + top + ", height=" + height + ", support=" + support;
                        assertEquals(0, near, 1.0E-5, "No floating or penetration: " + context);
                        assertEquals(4.5 / 16, far, 1.0E-5, context);
                        assertEquals(height, top_ - bottom, 1.0E-5, context);
                        assertEquals(-0.5 + height / 30.0, bottom, 1.0E-5, context);
                        assertEquals(0.5 - 5.25 / 16, left, 1.0E-5, context);
                        assertEquals(0.5 - 0.75 / 16, right, 1.0E-5, context);
                        AABB hitbox = CanvasScrollGeometry.bounds(
                                placement, face, widthAxis, longAxis, height);
                        assertEquals(hitbox.minX, renderedBounds.minX, 1.0E-5, context);
                        assertEquals(hitbox.minY, renderedBounds.minY, 1.0E-5, context);
                        assertEquals(hitbox.minZ, renderedBounds.minZ, 1.0E-5, context);
                        assertEquals(hitbox.maxX, renderedBounds.maxX, 1.0E-5, context);
                        assertEquals(hitbox.maxY, renderedBounds.maxY, 1.0E-5, context);
                        assertEquals(hitbox.maxZ, renderedBounds.maxZ, 1.0E-5, context);
                    }
                }
            }
        }
    }

    private static List<Vector3f> modelVertices() throws Exception {
        var stream = Objects.requireNonNull(CanvasScrollGeometryTest.class.getResourceAsStream(
                "/assets/gyromancy/models/entity/canvas_plot.json"));
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var elements = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("elements");
            List<Vector3f> result = new ArrayList<>();
            for (var element : elements) {
                var cube = element.getAsJsonObject();
                if (cube.has("rotation")) {
                    assertEquals(0, cube.getAsJsonObject("rotation").get("angle").getAsDouble(),
                            "Update geometry tests for rotated model elements");
                }
                var from = cube.getAsJsonArray("from");
                var to = cube.getAsJsonArray("to");
                for (int corner = 0; corner < 8; corner++) {
                    result.add(new Vector3f(
                            ((corner & 1) == 0 ? from : to).get(0).getAsFloat() / 16,
                            ((corner & 2) == 0 ? from : to).get(1).getAsFloat() / 16,
                            ((corner & 4) == 0 ? from : to).get(2).getAsFloat() / 16));
                }
            }
            return result;
        }
    }
}
