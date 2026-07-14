package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Base for center symbols — the function-defining core of a magic array.
 *  Subclasses may override {@link #centerEffect()} and/or {@link #endEffect()}
 *  to provide activation and teardown behavior. */
public abstract class CenterSymbol extends Symbol {
    public static final String FIREBALL_KEY = "fireball";
    public static final String OLD_FIREBALL_KEY = "old_fireball";
    public static final String MANABALL_KEY = "manaball";

    protected CenterSymbol(String name, int featurePoints, boolean allowRotation, int glyphColor) {
        super(name, featurePoints, allowRotation, glyphColor);
    }

    protected CenterSymbol(String name, int featurePoints, boolean allowRotation, int glyphColor, SkeletonMatcher.SoftThresholds thresholds) {
        super(name, featurePoints, allowRotation, thresholds, glyphColor);
    }

    @Override
    public SymbolRole role() { return SymbolRole.CENTER_SYMBOL; }

    /** Activation effect when the array forms. Null means no-op. */
    public SymbolCatalog.CenterEffect centerEffect() { return null; }

    /** Teardown effect when the array breaks. Null means no-op. */
    public SymbolCatalog.EndEffect endEffect() { return null; }

    public static boolean isFacingDown(PositionedGlyph glyph) {
        return glyph.pixels().stream().findAny().map(PixelPos::face).orElse(Direction.UP) == Direction.DOWN;
    }

    public static Vec3 glyphCenter(PositionedGlyph glyph) {
        double a = (glyph.minWorldX() + glyph.maxWorldX()) * 0.5;
        double b = (glyph.minWorldY() + glyph.maxWorldY()) * 0.5;
        PixelPos sample = glyph.pixels().stream().findAny().orElse(null);
        if (sample == null) return Vec3.atCenterOf(glyph.worldPos());

        Direction face = sample.face();
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 plane = Vec3.atCenterOf(sample.pos()).add(normal.scale(0.5));
        return switch (face) {
            case NORTH, SOUTH -> new Vec3(a, b, plane.z);
            case EAST, WEST -> new Vec3(plane.x, b, a);
            case UP, DOWN -> new Vec3(a, plane.y, b);
        };
    }

    public static Vec3 faceNormal(PositionedGlyph glyph) {
        Direction face = glyph.pixels().stream().findAny().map(PixelPos::face).orElse(Direction.UP);
        return Vec3.atLowerCornerOf(face.getNormal());
    }

    public static UUID boundEntityUuid(Map<String, Object> scratchData, String key) {
        Object value = scratchData.get(key);
        if (value instanceof ArrayObject.EntityRef ref) return ref.uuid();
        if (value instanceof UUID uuid) return uuid;
        if (value instanceof String string) {
            try {
                return UUID.fromString(string);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    public static Optional<Entity> boundEntity(ServerLevel level, Map<String, Object> scratchData, String key) {
        Object value = scratchData.get(key);
        if (value instanceof ArrayObject.EntityRef ref) return Optional.ofNullable(ref.resolve(level));

        UUID uuid = boundEntityUuid(scratchData, key);
        return uuid == null ? Optional.empty() : Optional.ofNullable(level.getEntity(uuid));
    }

    protected static void discardBoundEntities(ServerLevel level, Map<String, Object> scratchData, String... keys) {
        for (String key : keys) {
            boundEntity(level, scratchData, key).ifPresent(Entity::discard);
        }
    }
}
