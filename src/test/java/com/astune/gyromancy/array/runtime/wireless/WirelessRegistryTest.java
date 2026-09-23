package com.astune.gyromancy.array.runtime.wireless;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.symbol.SecretText;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WirelessRegistryTest {
    @Test
    void keyUsesSecretTextEnumOrderRatherThanRunePlacementOrder() {
        String expected = "wireless:1:3:12";
        assertEquals(expected, WirelessRegistry.keyFor(List.of(
                SecretText.SECRET_1, SecretText.SECRET_12, SecretText.SECRET_3)));
        assertEquals(expected, WirelessRegistry.keyFor(List.of(
                SecretText.SECRET_3, SecretText.SECRET_1, SecretText.SECRET_12)));
    }

    @Test
    void broadcastsOnlyWhenPublishedValueChanges() {
        WirelessRegistry registry = new WirelessRegistry();
        String key = WirelessRegistry.keyFor(List.of(SecretText.SECRET_1));
        UUID dependent = UUID.fromString("00000000-0000-0000-0000-000000000021");
        WirelessRegistry.Value first = new WirelessRegistry.Value(glyph(1, 2.0), List.of(glyph(1, 2.0)));

        assertTrue(registry.publish(key, first).isEmpty());
        registry.subscribe(key, dependent);
        assertTrue(registry.publish(key, first).isEmpty());

        WirelessRegistry.Value changed = new WirelessRegistry.Value(glyph(1, 3.0), List.of(glyph(1, 3.0)));
        assertEquals(Set.of(dependent), registry.publish(key, changed));

        registry.unsubscribe(dependent);
        assertTrue(registry.publish(key, first).isEmpty());
    }

    @Test
    void persistsValuesButNotRuntimeSubscribers() {
        WirelessRegistry registry = new WirelessRegistry();
        String key = WirelessRegistry.keyFor(List.of(SecretText.SECRET_2));
        WirelessRegistry.Value value = new WirelessRegistry.Value(glyph(2, 2.0), List.of(glyph(2, 2.0)));
        registry.publish(key, value);
        registry.subscribe(key, UUID.fromString("00000000-0000-0000-0000-000000000022"));

        var encoded = WirelessRegistry.CODEC.encodeStart(JsonOps.INSTANCE, registry).getOrThrow();
        WirelessRegistry decoded = WirelessRegistry.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(value, decoded.value(key).orElseThrow());
        assertFalse(decoded.publish(key, new WirelessRegistry.Value(glyph(2, 4.0), List.of(glyph(2, 4.0))))
                .contains(UUID.fromString("00000000-0000-0000-0000-000000000022")));
    }

    @Test
    void tracksPendingRootsSeparatelyFromActiveSubscribers() {
        WirelessRegistry registry = new WirelessRegistry();
        String key = WirelessRegistry.keyFor(List.of(SecretText.SECRET_1));
        UUID root = UUID.fromString("00000000-0000-0000-0000-000000000023");

        registry.addPending(key, root);
        assertEquals(Set.of(root), registry.pending(key));
        registry.cancelPending(root);
        assertTrue(registry.pending(key).isEmpty());
    }

    @Test
    void unpublishOnlyRemovesTheExpectedSourceSnapshot() {
        WirelessRegistry registry = new WirelessRegistry();
        String key = WirelessRegistry.keyFor(List.of(SecretText.SECRET_2));
        WirelessRegistry.Value first = new WirelessRegistry.Value(
                glyph(2, 2.0), List.of(glyph(2, 2.0)));
        WirelessRegistry.Value changed = new WirelessRegistry.Value(
                glyph(2, 3.0), List.of(glyph(2, 3.0)));

        registry.publish(key, first);
        assertTrue(registry.unpublish(key, changed).isEmpty());
        assertTrue(registry.contains(key));
        registry.unpublish(key, first);
        assertFalse(registry.contains(key));
    }

    private static PositionedGlyph glyph(int id, double length) {
        BlockPos pos = new BlockPos(0, 64, 0);
        return new PositionedGlyph(
                UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", id)),
                id,
                ResourceLocation.fromNamespaceAndPath("gyromancy", "circle_outer"),
                0.9F,
                SymbolRole.OUTER_CIRCLE,
                new Vec3(1.0, 0.0, 0.0),
                length,
                1.0,
                pos,
                0.0, 4.0, 0.0, 4.0,
                Set.of(new PixelPos(pos, Direction.NORTH, id, id, 0xFF00AA00)));
    }
}
