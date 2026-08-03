package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PositionedGlyphCodecTest {

    @Test
    void roundTripsPersistentGlyphData() {
        PositionedGlyph glyph = new PositionedGlyph(
                UUID.fromString("00000000-0000-0000-0000-000000000123"),
                7,
                ResourceLocation.fromNamespaceAndPath("gyromancy", "fire"),
                0.9f,
                SymbolRole.PARAMETER_RUNE,
                new Vec3(1.0, 0.0, 0.0),
                2.0,
                1.0,
                new BlockPos(1, 2, 3),
                1.0, 2.0, 3.0, 4.0,
                Set.of(new PixelPos(new BlockPos(1, 2, 3), Direction.NORTH, 4, 5, 0xFF00AA00))
        );

        var json = PositionedGlyph.CODEC.encodeStart(JsonOps.INSTANCE, glyph).getOrThrow();
        var decoded = PositionedGlyph.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(glyph, decoded);
    }

    @Test
    void decodesOldPersistentGlyphData() {
        PositionedGlyph glyph = new PositionedGlyph(
                UUID.fromString("00000000-0000-0000-0000-000000000123"),
                7,
                ResourceLocation.fromNamespaceAndPath("gyromancy", "fire"),
                0.9f,
                SymbolRole.PARAMETER_RUNE,
                new Vec3(1.0, 0.0, 0.0),
                2.0,
                1.0,
                new BlockPos(1, 2, 3),
                1.0, 2.0, 3.0, 4.0,
                Set.of(new PixelPos(new BlockPos(1, 2, 3), Direction.NORTH, 4, 5, 0xFF00AA00))
        );

        var json = PositionedGlyph.CODEC.encodeStart(JsonOps.INSTANCE, glyph).getOrThrow();
        json.getAsJsonObject().remove("front");
        json.getAsJsonObject().remove("length");
        json.getAsJsonObject().remove("width");
        json.getAsJsonObject().remove("surface");

        var decoded = PositionedGlyph.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(Vec3.ZERO, decoded.front());
        assertEquals(0.0, decoded.length());
        assertEquals(0.0, decoded.width());
        assertEquals(Direction.NORTH, decoded.surface().requireAxisAlignedDirection());
    }
}
