package com.astune.gyromancy.array.runtime.wireless;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.runtime.ArrayEffectLifecycle;
import com.astune.gyromancy.registry.ModAttachments;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.UUID;

/** Coordinates retryable Wireless compilation without embedding lifecycle logic in the compiler. */
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

    public static void retry(ServerLevel level, String key) {
        WirelessRegistry registry = level.getData(ModAttachments.WIRELESS_REGISTRY);
        for (UUID rootId : registry.pending(key)) {
            registry.cancelPending(rootId);
            PositionedGlyph root = level.getData(ModAttachments.ARRAY_MANAGER).getGlyph(rootId);
            if (root != null) ArrayEffectLifecycle.compileNew(level, root);
        }
    }
}
