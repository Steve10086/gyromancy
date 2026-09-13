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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindFieldOpTest {
    @Test
    void acceptsWindArrowsAndRevertButNotProjectileArrowUp() {
        OpInput.Rune wind = new OpInput.Rune(glyph("wind", SymbolRole.CENTER_SYMBOL,
                1, new Vec3(0.0, 0.0, 1.0), 1.0));
        OpInput.Rune arrow = new OpInput.Rune(glyph("arrow", SymbolRole.PARAMETER_RUNE,
                2, new Vec3(1.0, 0.0, 0.0), 2.0));
        OpInput.Rune revert = new OpInput.Rune(glyph("revert", SymbolRole.PARAMETER_RUNE,
                3, new Vec3(1.0, 0.0, 0.0), 1.0));
        OpInput.Rune arrowUp = new OpInput.Rune(glyph("arrow_up", SymbolRole.PARAMETER_RUNE,
                4, new Vec3(1.0, 0.0, 0.0), 1.0));

        assertTrue(WindFieldOp.DEFINITION.match().stream().anyMatch(m -> m.matches(wind)));
        assertTrue(WindFieldOp.DEFINITION.accepted().stream().anyMatch(m -> m.matches(arrow)));
        assertTrue(WindFieldOp.DEFINITION.accepted().stream().anyMatch(m -> m.matches(revert)));
        assertTrue(WindFieldOp.DEFINITION.accepted().stream().noneMatch(m -> m.matches(arrowUp)));
        assertTrue(WindFieldOp.DEFINITION.accepted().stream()
                .anyMatch(m -> m.matches(new OpInput.Op(new MomentumOp(List.of())))));
    }

    @Test
    void sumsArrowsWithMomentumLiftAndRevertFlipsTheResult() {
        PositionedGlyph boundary = glyph("wind", SymbolRole.CENTER_SYMBOL,
                1, new Vec3(0.0, 0.0, 1.0), 1.0);
        OpInput.Rune wind = new OpInput.Rune(boundary);
        OpInput.Rune first = new OpInput.Rune(glyph("arrow", SymbolRole.PARAMETER_RUNE,
                2, new Vec3(1.0, 0.0, 0.0), 2.0));
        OpInput.Rune second = new OpInput.Rune(glyph("arrow", SymbolRole.PARAMETER_RUNE,
                3, new Vec3(-1.0, 0.0, 0.0), 1.0));

        Vec3 direction = WindFieldOp.directionFor(boundary, List.of(wind, first, second), null).vector();
        // Sum is +X with magnitude one. The momentum lift is
        // (3 - 1) = 2 on world-up.
        assertEquals(1.0 / Math.sqrt(5), direction.x, 1.0E-9);
        assertEquals(2.0 / Math.sqrt(5), direction.y, 1.0E-9);

        OpInput.Rune revert = new OpInput.Rune(glyph("revert", SymbolRole.PARAMETER_RUNE,
                4, new Vec3(1.0, 0.0, 0.0), 1.0));
        Vec3 reversed = WindFieldOp.directionFor(boundary,
                List.of(wind, first, second, revert), null).vector();
        assertEquals(-direction.x, reversed.x, 1.0E-9);
        assertEquals(-direction.y, reversed.y, 1.0E-9);
    }

    @Test
    void requiresAtLeastOneArrowAndMapsEnergyToWindForce() {
        OpInput.Rune wind = new OpInput.Rune(glyph("wind", SymbolRole.CENTER_SYMBOL,
                1, new Vec3(0.0, 0.0, 1.0), 1.0));
        CompileResult<CompiledOp> result = WindFieldOp.create(wind.glyph(), List.of(wind), List.of(wind));
        assertInstanceOf(CompileResult.Failure.class, result);
        assertEquals(0.0, WindFieldPushOp.forceFor(0.0));
        assertEquals(0.7, WindFieldPushOp.forceFor(7.0), 1.0E-9);
        assertEquals(Vec3.ZERO, WindFieldPushOp.pushFor(Vec3.ZERO, 7.0));
    }

    @Test
    void rejectsMomentumChildWhileKeepingOtherCompiledPayloadsAvailable() {
        PositionedGlyph boundary = glyph("wind", SymbolRole.CENTER_SYMBOL,
                1, new Vec3(0.0, 0.0, 1.0), 1.0);
        OpInput.Rune wind = new OpInput.Rune(boundary);
        OpInput.Rune arrow = new OpInput.Rune(glyph("arrow", SymbolRole.PARAMETER_RUNE,
                2, new Vec3(0.0, 0.0, 1.0), 1.0));
        CompileResult<CompiledOp> result = WindFieldOp.create(boundary, List.of(wind),
                List.of(wind, arrow, new OpInput.Op(new MomentumOp(List.of()))));
        CompileResult.Failure<CompiledOp> failure = assertInstanceOf(CompileResult.Failure.class, result);
        assertEquals("field_rejects_momentum", failure.diagnostics().getFirst().code());
    }

    private static PositionedGlyph glyph(String name, SymbolRole role, int id,
                                         Vec3 front, double length) {
        BlockPos pos = new BlockPos(0, 64, 0);
        return new PositionedGlyph(
                UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", id)),
                id,
                ResourceLocation.fromNamespaceAndPath("gyromancy", name),
                0.9F,
                role,
                front,
                length,
                1.0,
                pos,
                0.0,
                4.0,
                0.0,
                4.0,
                Set.of(new PixelPos(pos, Direction.NORTH, id, id, 0xFF00AA00)));
    }
}
