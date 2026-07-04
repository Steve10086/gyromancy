package com.astune.gyromancy.client.glyph;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlyphRendererTest {

    @Test
    void worldFaceCenterConvertsPainterLocalCorners() {
        Vec3[] corners = {
                new Vec3(-0.5, 0.501, -0.5),
                new Vec3(0.5, 0.501, -0.5),
                new Vec3(0.5, 0.501, 0.5),
                new Vec3(-0.5, 0.501, 0.5)
        };

        Vec3 center = GlyphRenderer.worldFaceCenter(new BlockPos(10, 64, -3), corners);

        assertEquals(10.5, center.x, 1e-9);
        assertEquals(65.001, center.y, 1e-9);
        assertEquals(-2.5, center.z, 1e-9);
    }
}
