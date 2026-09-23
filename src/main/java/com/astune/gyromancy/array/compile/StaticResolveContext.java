package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.array.MagicArrayManager;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Objects;

/** Context used only while static dynamic structures are queried. */
public record StaticResolveContext(
        ServerLevel level,
        MagicArrayManager manager,
        List<OpDefinition> definitions,
        PositionedGlyph targetBoundary,
        List<String> resolutionStack
) {
    public StaticResolveContext {
        definitions = List.copyOf(definitions);
        resolutionStack = List.copyOf(resolutionStack);
    }

    public StaticResolveContext withTargetBoundary(PositionedGlyph boundary) {
        return new StaticResolveContext(level, manager, definitions, boundary, resolutionStack);
    }

    public StaticResolveContext push(String token) {
        Objects.requireNonNull(token, "token");
        if (resolutionStack.contains(token)) return this;
        java.util.ArrayList<String> next = new java.util.ArrayList<>(resolutionStack);
        next.add(token);
        return new StaticResolveContext(level, manager, definitions, targetBoundary, next);
    }

    public boolean contains(String token) {
        return resolutionStack.contains(token);
    }
}
