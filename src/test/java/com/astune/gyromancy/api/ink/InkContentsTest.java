package com.astune.gyromancy.api.ink;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InkContentsTest {

    private static final ResourceLocation MANA =
            ResourceLocation.fromNamespaceAndPath("gyromancy", "mana_ink");
    private static final ResourceLocation FIRE =
            ResourceLocation.fromNamespaceAndPath("gyromancy", "fire_ink");
    private static final int MAX = 6400;

    @Test
    void emptyBottleStartsAtRecipeAmount() {
        assertEquals(1600, InkContents.mix(null, 0, MANA, 1600, MAX));
    }

    @Test
    void sameInkStacksCharges() {
        assertEquals(2100, InkContents.mix(MANA, 500, MANA, 1600, MAX));
    }

    @Test
    void sameInkStopsAtCapacity() {
        assertEquals(MAX, InkContents.mix(MANA, 6300, MANA, 1600, MAX));
    }

    @Test
    void differentInkReplacesExistingContents() {
        assertEquals(1600, InkContents.mix(FIRE, 5000, MANA, 1600, MAX));
    }

    @Test
    void recipeAmountIsCappedAtCapacity() {
        assertEquals(MAX, InkContents.mix(null, 0, MANA, 9999, MAX));
    }

    @Test
    void negativeRemainingIsTreatedAsEmpty() {
        assertEquals(1600, InkContents.mix(MANA, -50, MANA, 1600, MAX));
    }
}
