package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Base for center symbols — the function-defining core of a magic array.
 *  Subclasses may override {@link #centerEffect()} and/or {@link #endEffect()}
 *  to provide activation and teardown behavior. */
public abstract class CenterSymbol extends Symbol {
    public static final String FIREBALL_KEY = "fireball";
    public static final String OLD_FIREBALL_KEY = "old_fireball";
    public static final String MANABALL_KEY = "manaball";
    public static final String WATERBALL_KEY = "waterball";
    public static final String ICEBALL_KEY = "iceball";
    public static final String DRYBALL_KEY = "dryball";

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
        return glyph.surface().normal().y < -0.999;
    }

    public static Vec3 glyphCenter(PositionedGlyph glyph) {
        return glyph.center();
    }

    public static Vec3 glyphCenter(ServerLevel level, PositionedGlyph glyph) {
        return glyphCenter(glyph);
    }

    public static Vec3 faceNormal(PositionedGlyph glyph) {
        return glyph.surface().normal();
    }

    protected static LaunchData launchData(ServerLevel level, PositionedGlyph circleGlyph,
                                           PositionedGlyph centerGlyph, List<PositionedGlyph> arrows) {
        Vec3 velocity = Vec3.ZERO;
        double arrowSizeSum = 0.0;
        for (PositionedGlyph arrow : arrows) {
            arrowSizeSum += arrow.length();
            if (arrow.front().lengthSqr() >= 1e-8) {
                velocity = velocity.add(arrow.front().normalize().scale(arrow.length()));
            }
        }

        double area = Math.max(0.0, circleGlyph.length() * circleGlyph.width());
        float size = (float)Math.max(0.1F, Math.sqrt(area) * 0.5);
        double liftDirection = isFacingDown(circleGlyph) ? -1.0 : 1.0;
        Vec3 spawnPos = glyphCenter(level, centerGlyph).add(faceNormal(centerGlyph).scale(size * 2.0));
        return new LaunchData(spawnPos, velocity, arrowSizeSum, liftDirection, size);
    }

    protected record LaunchData(Vec3 position, Vec3 velocity, double arrowSizeSum, double liftDirection, float size) {}

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
