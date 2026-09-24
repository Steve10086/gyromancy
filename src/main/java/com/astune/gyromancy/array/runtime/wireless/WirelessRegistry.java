package com.astune.gyromancy.array.runtime.wireless;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.ArrayAstBuilder;
import com.astune.gyromancy.array.compile.GroupNode;
import com.astune.gyromancy.symbol.SecretText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Level-local directory for Wireless source circles.
 *
 * <p>The directory persists only the authored source snapshot.  Subscriber
 * array ids are deliberately runtime-only: an array subscribes again when it
 * resolves a WirelessOp after activation, so stale ids cannot survive a
 * restart or an ordinary lifecycle replacement.</p>
 */
public final class WirelessRegistry {
    public static final Codec<WirelessRegistry> CODEC = Entry.CODEC.listOf().xmap(
            WirelessRegistry::fromEntries, WirelessRegistry::entries);

    private final Map<String, Value> values = new HashMap<>();
    private final Map<String, Set<UUID>> subscribersByKey = new HashMap<>();
    private final Map<UUID, Set<String>> keysBySubscriber = new HashMap<>();
    private final Map<String, Set<UUID>> pendingByKey = new HashMap<>();
    private final Map<UUID, Set<String>> keysByPending = new HashMap<>();

    public WirelessRegistry() {}

    /**
     * Creates a stable, public key from the authored secret-text set. Enum
     * declaration order, rather than rune placement order, canonicalizes the
     * sequence; delimiters and a fixed prefix keep it unambiguous.
     */
    public static String keyFor(List<SecretText> secretTexts) {
        if (secretTexts == null || secretTexts.isEmpty()) {
            throw new IllegalArgumentException("Wireless requires at least one secret-text rune");
        }
        return secretTexts.stream()
                .sorted()
                .map(SecretText::resourceName)
                .collect(Collectors.joining(":", "wireless:", ""));
    }

    public Optional<Value> value(String key) {
        return Optional.ofNullable(values.get(key));
    }

    public boolean contains(String key) {
        return values.containsKey(key);
    }

    /**
     * Stores a source value and returns the currently live subscribers only
     * when the value changed.  The caller owns lifecycle recomposition.
     */
    public Set<UUID> publish(String key, Value value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Value previous = values.put(key, value);
        if (value.equals(previous)) return Set.of();
        return subscribers(key);
    }

    /** Removes a value only when it still matches the publisher's snapshot. */
    public Set<UUID> unpublish(String key, Value expected) {
        if (!Objects.equals(values.get(key), expected)) return Set.of();
        values.remove(key);
        return subscribers(key);
    }

    /** Registers an active array as a dependent of a previously published key. */
    public void subscribe(String key, UUID arrayId) {
        if (key == null || arrayId == null || !values.containsKey(key)) return;
        subscribersByKey.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(arrayId);
        keysBySubscriber.computeIfAbsent(arrayId, ignored -> new LinkedHashSet<>()).add(key);
    }

    /** Removes all runtime-only subscriptions belonging to a deactivated array. */
    public void unsubscribe(UUID arrayId) {
        if (arrayId == null) return;
        Set<String> keys = keysBySubscriber.remove(arrayId);
        if (keys == null) return;
        for (String key : keys) {
            Set<UUID> subscribers = subscribersByKey.get(key);
            if (subscribers == null) continue;
            subscribers.remove(arrayId);
            if (subscribers.isEmpty()) subscribersByKey.remove(key);
        }
    }

    /**
     * Records a root which could not compile because this key was unavailable.
     * Pending roots are cancelled when the source changes; they are never
     * compiled automatically.
     */
    public void addPending(String key, UUID rootGlyphId) {
        if (key == null || rootGlyphId == null) return;
        pendingByKey.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(rootGlyphId);
        keysByPending.computeIfAbsent(rootGlyphId, ignored -> new LinkedHashSet<>()).add(key);
    }

    public Set<UUID> pending(String key) {
        Set<UUID> pending = pendingByKey.get(key);
        return pending == null ? Set.of() : Set.copyOf(pending);
    }

    public void cancelPending(UUID rootGlyphId) {
        if (rootGlyphId == null) return;
        Set<String> keys = keysByPending.remove(rootGlyphId);
        if (keys == null) return;
        for (String key : keys) {
            Set<UUID> roots = pendingByKey.get(key);
            if (roots == null) continue;
            roots.remove(rootGlyphId);
            if (roots.isEmpty()) pendingByKey.remove(key);
        }
    }

    private Set<UUID> subscribers(String key) {
        Set<UUID> subscribers = subscribersByKey.get(key);
        return subscribers == null ? Set.of() : Set.copyOf(subscribers);
    }

    private static WirelessRegistry fromEntries(List<Entry> entries) {
        WirelessRegistry registry = new WirelessRegistry();
        for (Entry entry : entries) {
            if (entry.key() != null && entry.value() != null) registry.values.put(entry.key(), entry.value());
        }
        return registry;
    }

    private List<Entry> entries() {
        return values.entrySet().stream()
                .map(entry -> new Entry(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(Entry::key))
                .toList();
    }

    private record Entry(String key, Value value) {
        private static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("key").forGetter(Entry::key),
                Value.CODEC.fieldOf("value").forGetter(Entry::value)
        ).apply(instance, Entry::new));
    }

    /**
     * Persisted structural value for one Wireless key.  Keeping the full
     * glyph snapshot makes change detection independent of transient compiled
     * operators and lets a saved map retain its identity while source chunks
     * are unloaded.
     */
    public record Value(PositionedGlyph rootCircle, List<PositionedGlyph> glyphs) {
        public static final Codec<Value> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                PositionedGlyph.CODEC.fieldOf("root_circle").forGetter(Value::rootCircle),
                PositionedGlyph.CODEC.listOf().fieldOf("glyphs").forGetter(Value::glyphs)
        ).apply(instance, Value::new));

        public Value {
            Objects.requireNonNull(rootCircle, "rootCircle");
            glyphs = List.copyOf(glyphs);
        }

        public static Value fromSource(GroupNode source) {
            Objects.requireNonNull(source, "source");
            return new Value(source.boundary(), ArrayAstBuilder.boundGlyphs(source));
        }

        /**
         * Resolves this persisted value only while its whole authored
         * structure is available in the current level.  This prevents a
         * partially loaded source circle from being injected into a consumer.
         */
        public Optional<GroupNode> loadedSource(ServerLevel level,
                                                com.astune.gyromancy.api.array.MagicArrayManager manager) {
            if (level == null || manager == null) return Optional.empty();
            for (PositionedGlyph glyph : glyphs) {
                if (!level.hasChunkAt(glyph.worldPos())
                        || manager.getGlyph(glyph.glyphUuid()) == null) {
                    return Optional.empty();
                }
            }
            PositionedGlyph liveRoot = manager.getGlyph(rootCircle.glyphUuid());
            if (liveRoot == null || liveRoot.role() != com.astune.gyromancy.api.symbol.SymbolRole.OUTER_CIRCLE
                    || !level.hasChunkAt(liveRoot.worldPos())) return Optional.empty();
            return Optional.of(ArrayAstBuilder.build(liveRoot, manager));
        }

        /** Builds the array frame forwarded with the dynamically resolved source. */
        public com.astune.gyromancy.api.array.ArrayObject runtimeArray(GroupNode source) {
            List<PositionedGlyph> bound = ArrayAstBuilder.boundGlyphs(source);
            return new com.astune.gyromancy.api.array.ArrayObject(
                    rootCircle.glyphUuid(), source.boundary(), bound, 0L, Map.of());
        }
    }
}
