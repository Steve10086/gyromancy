package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpDefinitionRegistry;
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

class CrushSpellOpTest {

    @Test
    void definitionIsDiscoveredByTheRegistry() {
        assertTrue(OpDefinitionRegistry.definitions().stream()
                .anyMatch(definition -> definition.id().equals(CrushSpellOp.ID)));
    }

    @Test
    void matchesEarthAndRevertAndAcceptsArrows() {
        OpInput.Rune earth = new OpInput.Rune(glyph("earth", SymbolRole.CENTER_SYMBOL,
                1, new Vec3(0.0, 0.0, 1.0), 1.0));
        OpInput.Rune revert = new OpInput.Rune(glyph("revert", SymbolRole.PARAMETER_RUNE,
                2, new Vec3(1.0, 0.0, 0.0), 1.0));
        OpInput.Rune arrow = new OpInput.Rune(glyph("arrow", SymbolRole.PARAMETER_RUNE,
                3, new Vec3(0.0, 1.0, 0.0), 2.0));
        OpInput.Rune arrowUp = new OpInput.Rune(glyph("arrow_up", SymbolRole.PARAMETER_RUNE,
                4, new Vec3(0.0, 1.0, 0.0), 1.0));

        assertTrue(CrushSpellOp.DEFINITION.match().stream().anyMatch(m -> m.matches(earth)));
        assertTrue(CrushSpellOp.DEFINITION.match().stream().anyMatch(m -> m.matches(revert)));
        assertTrue(CrushSpellOp.DEFINITION.accepted().stream().anyMatch(m -> m.matches(arrow)));
        assertTrue(CrushSpellOp.DEFINITION.accepted().stream().anyMatch(m -> m.matches(arrowUp)));
        assertTrue(CrushSpellOp.DEFINITION.accepted().stream()
                .anyMatch(m -> m.matches(new OpInput.Op(new MomentumOp(List.of())))));
    }

    @Test
    void compilesWithEarthAndRevertAndRejectsMissingRevert() {
        PositionedGlyph boundary = glyph("circle_outer", SymbolRole.OUTER_CIRCLE,
                0, new Vec3(0.0, 0.0, 1.0), 4.0);
        OpInput.Rune earth = new OpInput.Rune(glyph("earth", SymbolRole.CENTER_SYMBOL,
                1, new Vec3(0.0, 0.0, 1.0), 1.0));
        OpInput.Rune revert = new OpInput.Rune(glyph("revert", SymbolRole.PARAMETER_RUNE,
                2, new Vec3(1.0, 0.0, 0.0), 1.0));

        assertInstanceOf(CompileResult.Success.class,
                CrushSpellOp.create(boundary, List.of(earth, revert), List.of(earth, revert)));
        assertInstanceOf(CompileResult.Failure.class,
                CrushSpellOp.create(boundary, List.of(earth), List.of(earth)));
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
