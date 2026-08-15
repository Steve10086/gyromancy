package com.astune.gyromancy.entity;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.geometry.SurfaceFrame;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ArrayRelativePositionTest {
    @Test
    void preservesLocalPositionWhenArrayMovesAndRotates() {
        SurfaceFrame initial = SurfaceFrame.facing(
                new Vec3(1.0, 2.0, 3.0), new Vec3(0.0, 0.0, -1.0),
                new Vec3(0.0, 1.0, 0.0));
        Vec3 initialCenter = new Vec3(1.0, 2.0, 3.0);
        Vec3 position = initialCenter
                .add(initial.axisU().scale(0.75))
                .add(initial.axisV().scale(-0.25))
                .add(initial.normal().scale(2.0));

        ArrayRelativePosition relative = ArrayRelativePosition.capture(
                position, initialCenter, initial);
        SurfaceFrame moved = SurfaceFrame.facing(
                new Vec3(8.0, 5.0, -2.0), new Vec3(1.0, 0.0, 0.0),
                new Vec3(0.0, 1.0, 0.0));
        Vec3 resolved = relative.resolve(moved.origin(), moved);

        assertEquals(moved.origin()
                .add(moved.axisU().scale(0.75))
                .add(moved.axisV().scale(-0.25))
                .add(moved.normal().scale(2.0)), resolved);
    }

    @Test
    void runtimeContextResolvesNestedGlyphAgainstLiveArrayFrame() {
        SurfaceFrame initial = SurfaceFrame.facing(
                new Vec3(1.0, 2.0, 3.0), new Vec3(0.0, 0.0, -1.0),
                new Vec3(0.0, 1.0, 0.0));
        SurfaceFrame moved = SurfaceFrame.facing(
                new Vec3(8.0, 5.0, -2.0), new Vec3(1.0, 0.0, 0.0),
                new Vec3(0.0, 1.0, 0.0));

        PositionedGlyph compiledRoot = glyph("root", 1, initial, -2.0, 2.0, -2.0, 2.0);
        PositionedGlyph compiledRune = glyph("fire", 2, initial, 0.5, 1.0, -0.5, 0.0);
        PositionedGlyph liveRoot = glyph("root", 1, moved, -2.0, 2.0, -2.0, 2.0);
        PositionedGlyph liveRune = glyph("fire", 2, moved, 0.5, 1.0, -0.5, 0.0);
        ArrayObject array = new ArrayObject(
                UUID.randomUUID(), liveRoot, List.of(liveRoot, liveRune), java.util.Map.of());

        OpRuntimeContext context = new OpRuntimeContext(null, null)
                .withArray(array, compiledRoot);

        assertEquals(liveRune.center(), context.positionFor(compiledRune));
        assertEquals(moved.normal(), context.normalFor(compiledRune));
        assertSame(liveRune, context.liveGlyph(compiledRune));
    }

    private static PositionedGlyph glyph(
            String name, int id, SurfaceFrame surface,
            double minU, double maxU, double minV, double maxV) {
        return new PositionedGlyph(
                UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                id,
                ResourceLocation.fromNamespaceAndPath("gyromancy", name),
                1.0f,
                "root".equals(name) ? SymbolRole.OUTER_CIRCLE : SymbolRole.CENTER_SYMBOL,
                Vec3.ZERO,
                maxU - minU,
                maxV - minV,
                BlockPos.ZERO,
                minU, maxU, minV, maxV,
                Set.of(),
                Optional.empty(),
                surface);
    }
}
