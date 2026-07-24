package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.element.ElementType;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class OnEntityTickOpCodecTest {
    @Test
    void roundTripsPayloadListThroughOwnCodecs() {
        List<OnEntityTickOp> payload = List.of(
                new ExplosionOp(),
                new SmeltOp(),
                new CarryItemsOp(),
                new WaterBurstOp(),
                new BrewingOp(),
                new ElementOp(ElementType.WATER),
                new ElementConversionOp(ElementType.FIRE, "storedMana", 10,
                        0.1, 1000.0, 200.0, 1000.0, 10.0, 0.05));

        CompoundTag tag = new CompoundTag();
        tag.put("Payload", EntityPayload.savePayloadList(payload));

        List<EntityPayload> loaded = EntityPayload.loadPayloadList(tag, "Payload", List.of());

        assertInstanceOf(ExplosionOp.class, loaded.get(0));
        assertInstanceOf(SmeltOp.class, loaded.get(1));
        assertInstanceOf(CarryItemsOp.class, loaded.get(2));
        assertInstanceOf(WaterBurstOp.class, loaded.get(3));
        assertInstanceOf(BrewingOp.class, loaded.get(4));
        ElementOp elementPayload = assertInstanceOf(ElementOp.class, loaded.get(5));
        assertEquals(ElementType.WATER, elementPayload.absorbedElement());
        ElementConversionOp conversion = assertInstanceOf(ElementConversionOp.class, loaded.get(6));
        assertEquals(ElementType.FIRE, conversion.element());
        assertEquals("storedMana", conversion.storedManaKey());
        assertEquals(10, conversion.interval());
    }
}
