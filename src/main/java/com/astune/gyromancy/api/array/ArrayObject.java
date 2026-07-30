package com.astune.gyromancy.api.array;

import com.astune.gyromancy.array.runtime.emit.EmitResult;
import com.astune.gyromancy.array.runtime.emit.EmittedObject;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A validated magic array bound to its constituent glyphs.
 *
 * <p>When any bound glyph is invalidated, the array is destroyed and its runtime
 * gets the scratch data it produced during activation.
 */
public record ArrayObject(
        UUID arrayId,
        PositionedGlyph rootCircleGlyph,
        List<PositionedGlyph> boundGlyphs,
        long compilationEffectEndTick,
        Map<String, Object> scratchData
) {
    public static final int COMPILATION_EFFECT_TICKS = 200;
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    private static final Codec<Map<String, Object>> SCRATCH_CODEC = ScratchEntry.CODEC.listOf()
            .xmap(ArrayObject::decodeScratchData, ArrayObject::encodeScratchData);

    public static final Codec<ArrayObject> CODEC = RecordCodecBuilder.create(i ->
            i.group(UUID_CODEC.fieldOf("array_id").forGetter(ArrayObject::arrayId),
                    PositionedGlyph.CODEC.fieldOf("root_circle_glyph").forGetter(ArrayObject::rootCircleGlyph),
                    PositionedGlyph.CODEC.listOf().fieldOf("bound_glyphs").forGetter(ArrayObject::boundGlyphs),
                    Codec.LONG.optionalFieldOf("compilation_effect_end_tick", 0L)
                            .forGetter(ArrayObject::compilationEffectEndTick),
                    SCRATCH_CODEC.optionalFieldOf("scratch_data", Map.of()).forGetter(ArrayObject::scratchData))
             .apply(i, ArrayObject::new));

    /** Legacy/convenience constructor for arrays without a live compilation effect. */
    public ArrayObject(UUID arrayId,
                       PositionedGlyph rootCircleGlyph,
                       List<PositionedGlyph> boundGlyphs,
                       Map<String, Object> scratchData) {
        this(arrayId, rootCircleGlyph, boundGlyphs, 0L, scratchData);
    }

    /** All glyphs bound to this array. */
    public List<PositionedGlyph> allBoundGlyphs() {
        return boundGlyphs;
    }

    public int remainingCompilationEffectTicks(long gameTime) {
        long remaining = compilationEffectEndTick - gameTime;
        return (int) Math.clamp(remaining, 0L, COMPILATION_EFFECT_TICKS);
    }

    public record EntityRef(UUID uuid, ResourceLocation entityType) {
        public static EntityRef of(Entity entity) {
            return new EntityRef(entity.getUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
        }

        public Entity resolve(ServerLevel level) {
            return level.getEntity(uuid);
        }
    }

    private record ScratchEntry(String key, String kind, String value, Optional<ResourceLocation> entityType) {
        private static final Codec<ScratchEntry> CODEC = RecordCodecBuilder.create(i ->
                i.group(Codec.STRING.fieldOf("key").forGetter(ScratchEntry::key),
                        Codec.STRING.fieldOf("kind").forGetter(ScratchEntry::kind),
                        Codec.STRING.fieldOf("value").forGetter(ScratchEntry::value),
                        ResourceLocation.CODEC.optionalFieldOf("entity_type").forGetter(ScratchEntry::entityType))
                 .apply(i, ScratchEntry::new));
    }

    private static Map<String, Object> decodeScratchData(List<ScratchEntry> encoded) {
        Map<String, Object> decoded = new HashMap<>();
        List<EmittedObject> emissions = new ArrayList<>();
        for (ScratchEntry entry : encoded) {
            if (EmitResult.EMISSIONS_KEY.equals(entry.key()) && "emitted_entity".equals(entry.kind())) {
                decodeEmittedEntity(entry).ifPresent(emissions::add);
                continue;
            }
            Object value = decodeScratchValue(entry);
            if (value != null) decoded.put(entry.key(), value);
        }
        if (!emissions.isEmpty()) decoded.put(EmitResult.EMISSIONS_KEY, List.copyOf(emissions));
        return decoded;
    }

    private static Optional<EmittedObject> decodeEmittedEntity(ScratchEntry entry) {
        if (entry.entityType().isEmpty()) return Optional.empty();
        String[] parts = entry.value().split("\\|", 2);
        if (parts.length != 2) return Optional.empty();
        ResourceLocation emitType = ResourceLocation.parse(parts[0]);
        EntityRef ref = new EntityRef(UUID.fromString(parts[1]), entry.entityType().get());
        return Optional.of(new EmittedObject(emitType, ref));
    }

    private static Object decodeScratchValue(ScratchEntry entry) {
        return switch (entry.kind()) {
            case "entity" -> entry.entityType()
                    .<Object>map(type -> new EntityRef(UUID.fromString(entry.value()), type))
                    .orElse(null);
            case "uuid" -> UUID.fromString(entry.value());
            case "string" -> entry.value();
            case "int" -> Integer.parseInt(entry.value());
            case "long" -> Long.parseLong(entry.value());
            case "double" -> Double.parseDouble(entry.value());
            case "boolean" -> Boolean.parseBoolean(entry.value());
            default -> null;
        };
    }

    private static List<ScratchEntry> encodeScratchData(Map<String, Object> scratchData) {
        List<ScratchEntry> encoded = new ArrayList<>();
        for (Map.Entry<String, Object> entry : scratchData.entrySet()) {
            if (EmitResult.EMISSIONS_KEY.equals(entry.getKey()) && entry.getValue() instanceof List<?> emissions) {
                for (Object value : emissions) {
                    ScratchEntry encodedValue = encodeEmittedObject(entry.getKey(), value);
                    if (encodedValue != null) encoded.add(encodedValue);
                }
                continue;
            }
            ScratchEntry encodedValue = encodeScratchValue(entry.getKey(), entry.getValue());
            if (encodedValue != null) encoded.add(encodedValue);
        }
        return encoded;
    }

    private static ScratchEntry encodeEmittedObject(String key, Object value) {
        if (!(value instanceof EmittedObject emitted) || !(emitted.ref() instanceof EntityRef ref)) return null;
        return new ScratchEntry(key, "emitted_entity", emitted.emitType() + "|" + ref.uuid(),
                Optional.of(ref.entityType()));
    }

    private static ScratchEntry encodeScratchValue(String key, Object value) {
        if (value instanceof EntityRef ref) {
            return new ScratchEntry(key, "entity", ref.uuid().toString(), Optional.of(ref.entityType()));
        }
        if (value instanceof Entity entity) {
            EntityRef ref = EntityRef.of(entity);
            return new ScratchEntry(key, "entity", ref.uuid().toString(), Optional.of(ref.entityType()));
        }
        if (value instanceof UUID uuid) {
            return new ScratchEntry(key, "uuid", uuid.toString(), Optional.empty());
        }
        if (value instanceof String string) {
            return new ScratchEntry(key, "string", string, Optional.empty());
        }
        if (value instanceof Integer integer) {
            return new ScratchEntry(key, "int", integer.toString(), Optional.empty());
        }
        if (value instanceof Long longValue) {
            return new ScratchEntry(key, "long", longValue.toString(), Optional.empty());
        }
        if (value instanceof Float || value instanceof Double) {
            return new ScratchEntry(key, "double", value.toString(), Optional.empty());
        }
        if (value instanceof Boolean bool) {
            return new ScratchEntry(key, "boolean", bool.toString(), Optional.empty());
        }
        return null;
    }
}
