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
                new ElementConversionOp(ElementType.FIRE, "storedMana", 10,
                        0.1, 1000.0, 200.0, 1000.0, 10.0, 0.05));

        CompoundTag tag = new CompoundTag();
        tag.put("Payload", OnEntityTickOp.savePayloadList(payload));

        List<OnEntityTickOp> loaded = OnEntityTickOp.loadPayloadList(tag, "Payload", List.of());

        assertInstanceOf(ExplosionOp.class, loaded.get(0));
        assertInstanceOf(SmeltOp.class, loaded.get(1));
        ElementConversionOp conversion = assertInstanceOf(ElementConversionOp.class, loaded.get(2));
        assertEquals(ElementType.FIRE, conversion.element());
        assertEquals("storedMana", conversion.storedManaKey());
        assertEquals(10, conversion.interval());
    }
}
