package com.astune.gyromancy.api.array;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.runtime.emit.EmitResult;
import com.astune.gyromancy.array.runtime.emit.EmittedObject;
import com.astune.gyromancy.symbol.CenterSymbol;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArrayObjectCodecTest {
    @Test
    void roundTripsPersistentScratchEntityRefs() {
        UUID arrayId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID entityId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph center = glyph("fire", SymbolRole.CENTER_SYMBOL, 2);
        PositionedGlyph rune = glyph("arrow", SymbolRole.PARAMETER_RUNE, 3);
        ArrayObject array = new ArrayObject(arrayId, circle, List.of(circle, center, rune),
                Map.of(CenterSymbol.FIREBALL_KEY, new ArrayObject.EntityRef(entityId,
                        ResourceLocation.fromNamespaceAndPath("gyromancy", "fireball"))));

        var json = ArrayObject.CODEC.encodeStart(JsonOps.INSTANCE, array).getOrThrow();
        var decoded = ArrayObject.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(arrayId, decoded.arrayId());
        assertEquals(circle, decoded.rootCircleGlyph());
        assertEquals(List.of(circle, center, rune), decoded.boundGlyphs());
        assertEquals(array.scratchData(), decoded.scratchData());
    }

    @Test
    void roundTripsMultipleEmittedEntityRefs() {
        UUID arrayId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID firstEntityId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID secondEntityId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph center = glyph("fire", SymbolRole.CENTER_SYMBOL, 2);
        ResourceLocation fireballType = ResourceLocation.fromNamespaceAndPath("gyromancy", "fireball");
        ResourceLocation emitType = ResourceLocation.fromNamespaceAndPath("gyromancy", "fireball");
        List<EmittedObject> emissions = List.of(
                new EmittedObject(emitType,
                        new ArrayObject.EntityRef(firstEntityId, fireballType)),
                new EmittedObject(emitType,
                        new ArrayObject.EntityRef(secondEntityId, fireballType)));
        ArrayObject array = new ArrayObject(arrayId, circle, List.of(circle, center),
                Map.of(EmitResult.EMISSIONS_KEY, emissions));

        var json = ArrayObject.CODEC.encodeStart(JsonOps.INSTANCE, array).getOrThrow();
        var decoded = ArrayObject.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(array.scratchData(), decoded.scratchData());
    }

    @Test
    void compilationEffectWindowPersistsAndExpiresAtItsOwnEndTick() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph center = glyph("fire", SymbolRole.CENTER_SYMBOL, 2);
        ArrayObject array = new ArrayObject(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                circle,
                List.of(circle, center),
                1_200L,
                Map.of());

        var json = ArrayObject.CODEC.encodeStart(JsonOps.INSTANCE, array).getOrThrow();
        ArrayObject decoded = ArrayObject.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(1_200L, decoded.compilationEffectEndTick());
        assertEquals(200, decoded.remainingCompilationEffectTicks(1_000L));
        assertEquals(75, decoded.remainingCompilationEffectTicks(1_125L));
        assertEquals(0, decoded.remainingCompilationEffectTicks(1_200L));
        assertEquals(0, decoded.remainingCompilationEffectTicks(5_000L));
    }

    @Test
    void roundTripsSolvedManaElements() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        double[] values = new double[com.astune.gyromancy.api.element.ElementType.COUNT];
        values[com.astune.gyromancy.api.element.ElementType.MANA.ordinal()] = 8.5;
        com.astune.gyromancy.api.element.ManaElements elements =
                new com.astune.gyromancy.api.element.ManaElements(values);
        ArrayObject array = new ArrayObject(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                circle, List.of(circle), 0L, Map.of(), elements);

        var json = ArrayObject.CODEC.encodeStart(JsonOps.INSTANCE, array).getOrThrow();
        ArrayObject decoded = ArrayObject.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(elements, decoded.manaElements());
    }

    private static PositionedGlyph glyph(String name, SymbolRole role, int id) {
        BlockPos pos = new BlockPos(id, id + 1, id + 2);
        return new PositionedGlyph(
                UUID.fromString("00000000-0000-0000-0000-00000000000" + id),
                id,
                ResourceLocation.fromNamespaceAndPath("gyromancy", name),
                0.9f,
                role,
                new Vec3(1.0, 0.0, 0.0),
                2.0,
                1.0,
                pos,
                1.0, 2.0, 3.0, 4.0,
                Set.of(new PixelPos(pos, Direction.NORTH, 4, 5, 0xFF00AA00))
        );
    }
}
