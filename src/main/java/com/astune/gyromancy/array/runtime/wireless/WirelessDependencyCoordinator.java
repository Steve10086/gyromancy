package com.astune.gyromancy.array.runtime.wireless;

import com.astune.gyromancy.registry.ModAttachments;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.UUID;

/** Tracks roots waiting for a Wireless key and cancels them on source changes. */
public final class WirelessDependencyCoordinator {
    private WirelessDependencyCoordinator() {}

    public static void registerPending(ServerLevel level, UUID rootGlyphId,
                                       List<String> keys) {
        WirelessRegistry registry = level.getData(ModAttachments.WIRELESS_REGISTRY);
        for (String key : keys) registry.addPending(key, rootGlyphId);
    }

    public static void cancelPending(ServerLevel level, UUID rootGlyphId) {
        level.getData(ModAttachments.WIRELESS_REGISTRY).cancelPending(rootGlyphId);
    }

    /**
     * Drops every root waiting for the key without compiling it. A source
     * change never recompiles its dependents; they must be rebuilt explicitly.
     */
    public static void cancelPending(ServerLevel level, String key) {
        WirelessRegistry registry = level.getData(ModAttachments.WIRELESS_REGISTRY);
        for (UUID rootId : registry.pending(key)) {
            registry.cancelPending(rootId);
        }
    }
}
