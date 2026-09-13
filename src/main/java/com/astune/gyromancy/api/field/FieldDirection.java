package com.astune.gyromancy.api.field;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.world.phys.Vec3;

/**
 * The independently authored orientation of a magic field.
 *
 * <p>The vector identifies the field's forward direction; {@code angleDegrees}
 * is its roll around that vector. Neither value changes the field shape.</p>
 */
public record FieldDirection(Vec3 vector, float angleDegrees) {

    public static final Codec<FieldDirection> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Vec3.CODEC.fieldOf("vector")
                            .forGetter(FieldDirection::vector),
                    Codec.FLOAT.fieldOf("roll")
                            .forGetter(FieldDirection::angleDegrees)
            ).apply(instance, FieldDirection::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FieldDirection> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VECTOR3F,
                    d -> d.vector().toVector3f(),

                    ByteBufCodecs.FLOAT,
                    FieldDirection::angleDegrees,

                    (v, roll) -> new FieldDirection(
                            new Vec3(v.x, v.y, v.z),
                            roll
                    )
            );


    public FieldDirection {
        if (!isFinite(vector) || vector.lengthSqr() < 1.0E-12) {
            throw new IllegalArgumentException("Field direction must be finite and non-zero");
        }
        if (!Float.isFinite(angleDegrees)) {
            throw new IllegalArgumentException("Field direction angle must be finite");
        }
        vector = vector.normalize();
        angleDegrees = normalizeAngle(angleDegrees);
    }

    private static float normalizeAngle(float angleDegrees) {
        float normalized = angleDegrees % 360.0F;
        return normalized < 0.0F ? normalized + 360.0F : normalized;
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
