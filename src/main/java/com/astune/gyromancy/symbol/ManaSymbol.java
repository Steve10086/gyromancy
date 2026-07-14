package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.symbol.ParameterRune;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.entity.ball.ManaballEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/** Mana center symbol. */
public final class ManaSymbol extends CenterSymbol {
    private static final SkeletonMatcher.SoftThresholds THRESHOLDS =
            new SkeletonMatcher.SoftThresholds(0.80, 0.25, 0.70, 0.9, 0.75);

    public static final ManaSymbol INSTANCE = new ManaSymbol();

    private ManaSymbol() { super("mana", 0, false, 0xFF9d7aa7, THRESHOLDS); }

    @Override
    public SymbolCatalog.CenterEffect centerEffect() { return ManaSymbol::launchManaBall; }

    @Override
    public SymbolCatalog.EndEffect endEffect() { return ManaSymbol::discardManaBall; }

    private static Map<String, Object> launchManaBall(ServerLevel level, BlockPos arrayPos,
                                                      PositionedGlyph circleGlyph,
                                                      PositionedGlyph centerGlyph,
                                                      List<PositionedGlyph> runes) {
        if (runes.stream().anyMatch(rune -> !"arrow".equals(rune.symbolId().getPath()))) return Map.of();

        Vec3 velocity = Vec3.ZERO;
        double arrowSizeSum = 0.0;
        for (PositionedGlyph rune : runes) {
            arrowSizeSum += rune.length();
            if (rune.front().lengthSqr() < 1e-8) continue;
            velocity = velocity.add(rune.front().normalize().scale(rune.length()));
        }

        double area = Math.max(0.0, circleGlyph.length() * circleGlyph.width());
        float size = (float) Math.max(0.1, Math.sqrt(area) * 0.5);
        double speed = velocity.length();
        double lift = (arrowSizeSum - speed) + 0.2 * speed;
        lift *= isFacingDown(circleGlyph) ? -1.0 : 1.0;
        Vec3 initialVelocity = velocity.add(0.0, lift, 0.0);
        Vec3 acceleration = runes.isEmpty() ? Vec3.ZERO : new Vec3(0.0, -0.04 * 0.5, 0.0);
        Vec3 spawnPos = glyphCenter(centerGlyph).add(faceNormal(centerGlyph).scale(size * 2.0));
        ManaballEntity manaball = new ManaballEntity(level, spawnPos, initialVelocity, acceleration, size);
        level.addFreshEntity(manaball);
        return Map.of(MANABALL_KEY, ArrayObject.EntityRef.of(manaball));
    }

    private static void discardManaBall(ServerLevel level, BlockPos arrayPos,
                                        List<ParameterRune> runes,
                                        Map<String, Object> scratchData) {
        discardBoundEntities(level, scratchData, MANABALL_KEY);
    }
}
