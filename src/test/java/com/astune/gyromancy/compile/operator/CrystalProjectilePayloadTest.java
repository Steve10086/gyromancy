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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrystalProjectilePayloadTest {
    @Test
    void fireProjectileMountsFireCrystalOpByDefault() {
        PositionedGlyph boundary = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        OpInput.Rune fire = new OpInput.Rune(glyph("fire", SymbolRole.CENTER_SYMBOL, 2));

        FireProjectileOp op = assertInstanceOf(FireProjectileOp.class,
                assertInstanceOf(CompileResult.Success.class,
                        FireProjectileOp.create(boundary, List.of(fire), List.of(fire))).value());

        assertTrue(op.defaultPayload().stream().anyMatch(FireCrystalOp.class::isInstance));
    }

    @Test
    void waterProjectileMountsWaterCrystalOpByDefault() {
        PositionedGlyph boundary = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        OpInput.Rune water = new OpInput.Rune(glyph("water", SymbolRole.CENTER_SYMBOL, 2));

        WaterProjectileOp op = assertInstanceOf(WaterProjectileOp.class,
                assertInstanceOf(CompileResult.Success.class,
                        WaterProjectileOp.create(boundary, List.of(water), List.of(water))).value());

        assertTrue(op.defaultPayload().stream().anyMatch(WaterCrystalOp.class::isInstance));
    }

    @Test
    void tornadoProjectileMountsWindCrystalOpByDefault() {
        PositionedGlyph boundary = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        OpInput.Rune wind = new OpInput.Rune(glyph("wind", SymbolRole.CENTER_SYMBOL, 2));

        TornadoProjectileOp op = assertInstanceOf(TornadoProjectileOp.class,
                assertInstanceOf(CompileResult.Success.class,
                        TornadoProjectileOp.create(boundary, List.of(wind), List.of(wind))).value());

        assertTrue(op.defaultPayload().stream().anyMatch(WindCrystalOp.class::isInstance));
    }

    @Test
    void earthCrystalOpRemainsDefinedButUnmounted() {
        assertInstanceOf(EarthCrystalOp.class,
                EntityPayload.loadPayload(new EarthCrystalOp().savePayload()).orElseThrow());
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