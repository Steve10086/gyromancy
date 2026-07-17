package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractedGlyph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InteriorValidatorTest {

    @Test
    void findGlyphsInsideUsesThinWorldBoxWithoutRequiringSameNormal() {
        ExtractedGlyph circle = new ExtractedGlyph(
                Set.of(pixel(0, 10, 0, Direction.UP)),
                new double[]{0.0}, new double[]{0.0},
                0.0, 4.0, 0.0, 4.0, 1);

        PositionedGlyph samePlane = glyph(1, new BlockPos(2, 10, 2), Direction.UP);
        PositionedGlyph oppositeFaceSamePlane = glyph(2, new BlockPos(2, 11, 2), Direction.DOWN);
        PositionedGlyph differentHeight = glyph(3, new BlockPos(2, 11, 2), Direction.UP);

        Map<UUID, PositionedGlyph> glyphs = new LinkedHashMap<>();
        glyphs.put(samePlane.glyphUuid(), samePlane);
        glyphs.put(oppositeFaceSamePlane.glyphUuid(), oppositeFaceSamePlane);
        glyphs.put(differentHeight.glyphUuid(), differentHeight);

        assertEquals(
                java.util.List.of(samePlane, oppositeFaceSamePlane),
                InteriorValidator.findGlyphsInside(circle, null, glyphs));
    }

    private static PositionedGlyph glyph(int id, BlockPos pos, Direction face) {
        return new PositionedGlyph(
                UUID.fromString("00000000-0000-0000-0000-00000000000" + id),
                id,
                ResourceLocation.fromNamespaceAndPath("gyromancy", "fire"),
                0.9f,
                SymbolRole.PARAMETER_RUNE,
                Vec3.ZERO,
                1.0,
                1.0,
                pos,
                1.0, 3.0, 1.0, 3.0,
                Set.of(pixel(pos.getX(), pos.getY(), pos.getZ(), face)));
    }

    private static PixelPos pixel(int x, int y, int z, Direction face) {
        return new PixelPos(new BlockPos(x, y, z), face, 0, 0, 0xFF00AA00);
    }
}
