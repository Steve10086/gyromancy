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
    void attractionSlowsRadialMotionToZeroAtTheCenter() {
        double radius = 10.0;
        double farTarget = TornadoAttractionOp.targetRadialVelocity(true, 2.0F, 9.0, radius);
        double nearTarget = TornadoAttractionOp.targetRadialVelocity(true, 2.0F, 0.01, radius);
        double brakingPush = TornadoAttractionOp.radialCorrection(-0.20, nearTarget);

        assertEquals(-0.40, farTarget, 1.0E-9);
        assertTrue(nearTarget < 0.0);
        assertTrue(Math.abs(nearTarget) < Math.abs(farTarget));
        assertTrue(brakingPush > 0.0, "a too-fast inward entity is pushed outward to brake");
        assertEquals(0.0, TornadoAttractionOp.targetRadialVelocity(true, 2.0F, 0.0, radius), 1.0E-9);
    }

    @Test
    void attractionUsesOneTenthForceForNonItemEntitiesIncludingPlayers() {
        double itemForce = TornadoAttractionOp.maxVelocityCorrection(true);
        double entityForce = TornadoAttractionOp.maxVelocityCorrection(false);
        double itemBrakingSpeed = TornadoAttractionOp.targetRadialVelocity(
                true, 2.0F, 0.01, 10.0, itemForce);
        double entityBrakingSpeed = TornadoAttractionOp.targetRadialVelocity(
                true, 2.0F, 0.01, 10.0, entityForce);

        assertEquals(TornadoAttractionOp.MAX_VELOCITY_CORRECTION, itemForce, 1.0E-9);
        assertEquals(itemForce * 0.10, entityForce, 1.0E-9);
        assertTrue(Math.abs(entityBrakingSpeed) < Math.abs(itemBrakingSpeed),
                "the weaker non-item force begins braking at a lower speed");
    }

    @Test
    void attractionControlsAngularVelocityInsteadOfAddingItEveryTick() {
        Vec3 axis = new Vec3(0.0, 1.0, 0.0);
        Vec3 centerToTarget = new Vec3(2.0, 0.0, 0.0);
        Vec3 positive = TornadoAttractionOp.targetRotationalVelocity(centerToTarget, axis, 0.25);
        Vec3 negative = TornadoAttractionOp.targetRotationalVelocity(centerToTarget, axis, -0.25);
        Vec3 noPushAtTargetSpeed = TornadoAttractionOp.rotationalCorrection(
                positive, centerToTarget, axis, 0.25);
        Vec3 brakingPush = TornadoAttractionOp.rotationalCorrection(
                new Vec3(0.0, 0.0, -1.0), centerToTarget, axis, 0.25);

        assertEquals(0.0, positive.x, 1.0E-9);
        assertEquals(0.0, positive.y, 1.0E-9);
        assertEquals(-0.5, positive.z, 1.0E-9);
        assertEquals(0.5, negative.z, 1.0E-9);
        assertEquals(Vec3.ZERO, noPushAtTargetSpeed);
        assertEquals(0.18, brakingPush.z, 1.0E-9,
                "an entity faster than the target orbit receives braking force");
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
