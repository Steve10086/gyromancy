package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.element.ElementType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayloadTickPhaseTest {
    @Test
    void elementEconomyOpsDeclareTheirPhases() {
        assertEquals(EntityPayload.TickPhase.PRODUCE, new ElementOp(ElementType.FIRE).tickPhase());
        assertEquals(EntityPayload.TickPhase.GROW, fireVolumeOp().tickPhase());
        assertEquals(EntityPayload.TickPhase.CONVERT,
                new ElementConversionOp(ElementType.FIRE, 10).tickPhase());
        assertEquals(EntityPayload.TickPhase.EFFECT, new ExplosionOp().tickPhase());
    }

    @Test
    void phaseOrderingRunsProducersBeforeGrowersBeforeConverters() {
        EntityPayload convert = new ElementConversionOp(ElementType.FIRE, 10);
        EntityPayload effectA = new ExplosionOp();
        EntityPayload grow = fireVolumeOp();
        EntityPayload produce = new ElementOp(ElementType.FIRE);
        EntityPayload effectB = new SmeltOp();

        List<EntityPayload> ordered = EntityPayload.orderedByTickPhase(
                new ArrayList<>(List.of(convert, effectA, grow, produce, effectB)));

        assertEquals(List.of(produce, grow, convert, effectA, effectB), ordered);
    }

    private static ElementVolumeOp fireVolumeOp() {
        return new ElementVolumeOp(ElementType.FIRE, "storedMana", 10,
                0.1, 1000.0, 200.0, 0.05);
    }
}