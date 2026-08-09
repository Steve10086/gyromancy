package com.astune.gyromancy.api.array;

import com.astune.gyromancy.api.geometry.SurfaceFrame;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MagicArrayFreeSurfaceTest {
    @Test
    void hierarchyComposesGlyphsAcrossDifferentFramesOnTheSameFreePlane() {
        SurfaceFrame circleFrame = new SurfaceFrame(
                new Vec3(10.0, 20.0, 30.0),
                new Vec3(1.0, 0.0, 0.0),
                new Vec3(0.0, 1.0, 0.0),
                new Vec3(0.0, 0.0, 1.0));
        SurfaceFrame rotatedChildFrame = new SurfaceFrame(
                circleFrame.origin(),
                new Vec3(0.0, 1.0, 0.0),
                new Vec3(-1.0, 0.0, 0.0),
                new Vec3(0.0, 0.0, 1.0));
        SurfaceFrame parallelOtherPlane = new SurfaceFrame(
                circleFrame.origin().add(0.0, 0.0, 1.0),
                circleFrame.axisU(), circleFrame.axisV(), circleFrame.normal());
        double angle = Math.toRadians(4.0);
        SurfaceFrame tolerantFrame = SurfaceFrame.facing(
                circleFrame.origin().add(0.0, 0.0, 1.0 / 32.0),
                new Vec3(0.0, Math.sin(angle), Math.cos(angle)),
                new Vec3(0.0, 1.0, 0.0));

        PositionedGlyph circle = glyph(1, SymbolRole.OUTER_CIRCLE,
                -2.0, 2.0, -2.0, 2.0, circleFrame);
        PositionedGlyph child = glyph(2, SymbolRole.CENTER_SYMBOL,
                -0.5, 0.5, -0.5, 0.5, rotatedChildFrame);
        PositionedGlyph offPlane = glyph(3, SymbolRole.PARAMETER_RUNE,
                -0.5, 0.5, -0.5, 0.5, parallelOtherPlane);
        PositionedGlyph tolerantChild = glyph(4, SymbolRole.PARAMETER_RUNE,
                -0.5, 0.5, -0.5, 0.5, tolerantFrame);

        MagicArrayManager manager = new MagicArrayManager();
        manager.registerGlyph(child);
        manager.registerGlyph(offPlane);
        manager.registerGlyph(tolerantChild);
        manager.registerGlyph(circle);

        assertEquals(circle, manager.parentCircle(child));
        assertEquals(circle, manager.parentCircle(tolerantChild));
        assertNull(manager.parentCircle(offPlane));
    }

    private static PositionedGlyph glyph(int id, SymbolRole role,
                                         double minU, double maxU,
                                         double minV, double maxV,
                                         SurfaceFrame surface) {
        return new PositionedGlyph(
                new UUID(0L, id), id,
                ResourceLocation.fromNamespaceAndPath("gyromancy",
                        role == SymbolRole.OUTER_CIRCLE ? "circle_outer" : "fire"),
                1.0F, role, surface.axisU(), maxU - minU, maxV - minV,
                BlockPos.containing(surface.world((minU + maxU) * 0.5,
                        (minV + maxV) * 0.5)),
                minU, maxU, minV, maxV, Set.of(), Optional.empty(), surface);
    }
}
