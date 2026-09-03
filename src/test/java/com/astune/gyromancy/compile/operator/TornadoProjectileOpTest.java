package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TornadoProjectileOpTest {
    @Test
    void matchesWindButRejectsDirectArrowRunes() {
        OpInput.Rune wind = new OpInput.Rune(glyph("wind", SymbolRole.CENTER_SYMBOL, 1));
        OpInput.Rune arrow = new OpInput.Rune(glyph("arrow", SymbolRole.PARAMETER_RUNE, 2));
        OpInput.Rune arrowUp = new OpInput.Rune(glyph("arrow_up", SymbolRole.PARAMETER_RUNE, 3));

        assertTrue(TornadoProjectileOp.DEFINITION.match().stream().anyMatch(matcher -> matcher.matches(wind)));
        assertFalse(TornadoProjectileOp.DEFINITION.accepted().stream().anyMatch(matcher -> matcher.matches(arrow)));
        assertFalse(TornadoProjectileOp.DEFINITION.accepted().stream().anyMatch(matcher -> matcher.matches(arrowUp)));
    }

    @Test
    void assignsAttractionAndNonDestructiveImpactPayloadsByDefault() {
        PositionedGlyph boundary = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 4);
        OpInput.Rune wind = new OpInput.Rune(glyph("wind", SymbolRole.CENTER_SYMBOL, 5));

        TornadoProjectileOp op = assertInstanceOf(TornadoProjectileOp.class,
                assertInstanceOf(CompileResult.Success.class,
                        TornadoProjectileOp.create(boundary, List.of(wind), List.of(wind))).value());

        List<EntityPayload> payload = op.defaultPayload().stream().map(EntityPayload.class::cast).toList();
        TornadoAttractionOp attraction = assertInstanceOf(TornadoAttractionOp.class, payload.getFirst());
        assertTrue(attraction.towardsCenter());
        assertInstanceOf(TornadoImpactOp.class, payload.get(1));
    }

    @Test
    void attractionUsesSizeScaledCappedInverseSquareRootForce() {
        double capped = TornadoAttractionOp.forceMagnitude(2.0F, 0.01);
        double farther = TornadoAttractionOp.forceMagnitude(2.0F, 9.0);

        assertEquals(2.0 * TornadoAttractionOp.MAX_FORCE_PER_SIZE, capped, 1.0E-9);
        assertEquals(2.0 * TornadoAttractionOp.CURVE_FORCE_PER_SIZE / Math.sqrt(9.0 / 2.0), farther, 1.0E-9);
        assertTrue(farther < capped);
    }

    @Test
    void attractionAddsAngularVelocityAlongTheConfiguredAxis() {
        Vec3 axis = new Vec3(0.0, 1.0, 0.0);
        Vec3 positive = TornadoAttractionOp.rotationalPush(new Vec3(2.0, 0.0, 0.0), axis, 0.25);
        Vec3 negative = TornadoAttractionOp.rotationalPush(new Vec3(2.0, 0.0, 0.0), axis, -0.25);

        assertEquals(0.0, positive.x, 1.0E-9);
        assertEquals(0.0, positive.y, 1.0E-9);
        assertEquals(-0.5, positive.z, 1.0E-9);
        assertEquals(0.5, negative.z, 1.0E-9);
    }

    private static PositionedGlyph glyph(String name, SymbolRole role, int id) {
        BlockPos pos = new BlockPos(0, 64, 0);
        return new PositionedGlyph(
                UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", id)),
                id,
                ResourceLocation.fromNamespaceAndPath("gyromancy", name),
                0.9F,
                role,
                new Vec3(1.0, 0.0, 0.0),
                2.0,
                1.0,
                pos,
                0.0,
                4.0,
                0.0,
                4.0,
                Set.of(new PixelPos(pos, Direction.NORTH, id, id, 0xFF00AA00)));
    }
}
