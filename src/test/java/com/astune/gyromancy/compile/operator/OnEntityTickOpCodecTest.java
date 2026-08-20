package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.compile.vector.VectorContext;
import com.astune.gyromancy.compile.vector.StaticVectorOp;
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
                new MomentumOp(List.of(
                        new MomentumOp.AccelerationInput(StaticVectorOp.literal(
                                new net.minecraft.world.phys.Vec3(1.0, 2.0, 3.0).normalize().scale(4.0)),
                                MomentumOp.MotionMode.TANGENTIAL),
                        new MomentumOp.AccelerationInput(StaticVectorOp.literal(
                                new net.minecraft.world.phys.Vec3(0.0, 0.0, 5.0)),
                                MomentumOp.MotionMode.DIRECT))),
                new RotationOp(-3.0),
                new ElementOp(ElementType.WATER),
                new ElementVolumeOp(ElementType.FIRE, "storedMana", 10,
                        0.1, 1000.0, 200.0, 0.05),
                new ElementConversionOp(ElementType.FIRE, 10));

        CompoundTag tag = new CompoundTag();
        tag.put("Payload", EntityPayload.savePayloadList(payload));

        List<EntityPayload> loaded = EntityPayload.loadPayloadList(tag, "Payload", List.of());

        assertInstanceOf(ExplosionOp.class, loaded.get(0));
        assertInstanceOf(SmeltOp.class, loaded.get(1));
        assertInstanceOf(CarryItemsOp.class, loaded.get(2));
        assertInstanceOf(WaterBurstOp.class, loaded.get(3));
        assertInstanceOf(BrewingOp.class, loaded.get(4));
        MomentumOp momentum = assertInstanceOf(MomentumOp.class, loaded.get(5));
        assertEquals(2, momentum.accelerationInputs().size());
        assertEquals(4.0, momentum.accelerationInputs().getFirst()
                .vector().provide(new VectorContext((com.astune.gyromancy.api.geometry.SurfaceFrame) null,
                        (com.astune.gyromancy.api.geometry.SurfaceFrame) null,
                        (com.astune.gyromancy.api.geometry.SurfaceFrame) null,
                        net.minecraft.world.phys.Vec3.ZERO,
                        net.minecraft.world.phys.Vec3.ZERO,
                        net.minecraft.world.phys.Vec3.ZERO)).length());
        assertEquals(MomentumOp.MotionMode.DIRECT,
                momentum.accelerationInputs().get(1).motionMode());
        RotationOp rotation = assertInstanceOf(RotationOp.class, loaded.get(6));
        assertEquals(-3.0, rotation.rotationSpeed());
        ElementOp elementPayload = assertInstanceOf(ElementOp.class, loaded.get(7));
        assertEquals(ElementType.WATER, elementPayload.absorbedElement());
        ElementVolumeOp volume = assertInstanceOf(ElementVolumeOp.class, loaded.get(8));
        assertEquals(ElementType.FIRE, volume.element());
        assertEquals("storedMana", volume.storedManaKey());
        assertEquals(10, volume.interval());
        ElementConversionOp conversion = assertInstanceOf(ElementConversionOp.class, loaded.get(9));
        assertEquals(ElementType.FIRE, conversion.element());
        assertEquals(10, conversion.interval());
    }
}
