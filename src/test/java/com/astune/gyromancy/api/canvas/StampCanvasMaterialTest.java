package com.astune.gyromancy.api.canvas;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StampCanvasMaterialTest {
    @Test
    void codecRoundTripPreservesExternalMaterialProperties() {
        StampCanvasMaterial material = new StampCanvasMaterial(
                ResourceLocation.fromNamespaceAndPath(
                        "example", "textures/block/carving_plate.png"),
                0xFF123456,
                7);

        JsonElement encoded = StampCanvasMaterial.CODEC
                .encodeStart(JsonOps.INSTANCE, material)
                .getOrThrow();
        StampCanvasMaterial decoded = StampCanvasMaterial.CODEC
                .parse(JsonOps.INSTANCE, encoded)
                .getOrThrow();

        assertEquals(material, decoded);
    }

    @Test
    void rejectsEffectsOutsideCanvasRange() {
        assertThrows(IllegalArgumentException.class, () ->
                new StampCanvasMaterial(
                        ResourceLocation.withDefaultNamespace(
                                "textures/block/stone.png"),
                        0xFFFFFFFF,
                        0));
    }
}
