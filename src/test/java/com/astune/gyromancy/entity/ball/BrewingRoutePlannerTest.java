package com.astune.gyromancy.entity.ball;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrewingRoutePlannerTest {
    @Test
    void choosesFirstStepOfCompleteRouteInsteadOfDeadEnd() {
        var first = BrewingRoutePlanner.findFirstIngredient("water", List.of("spider_eye", "nether_wart"), 4,
                (ingredient, potion) -> switch (potion + "+" + ingredient) {
                    case "water+spider_eye" -> "mundane";
                    case "water+nether_wart" -> "awkward";
                    case "awkward+spider_eye" -> "poison";
                    default -> "";
                }, state -> !state.isEmpty(), String::equals, "poison"::equals);

        assertTrue(first.isPresent());
        assertEquals(1, first.getAsInt());
    }

    @Test
    void doesNotUseOneItemInTwoRecipeSteps() {
        var first = BrewingRoutePlanner.findFirstIngredient("water", List.of("reagent"), 4,
                (ingredient, potion) -> switch (potion) {
                    case "water" -> "intermediate";
                    case "intermediate" -> "effect";
                    default -> "";
                }, state -> !state.isEmpty(), String::equals, "effect"::equals);

        assertTrue(first.isEmpty());
    }
}
