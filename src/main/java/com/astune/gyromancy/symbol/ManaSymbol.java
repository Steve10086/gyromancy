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

        LaunchData launch = launchData(level, circleGlyph, centerGlyph, runes);
        Vec3 acceleration = runes.isEmpty() ? Vec3.ZERO : new Vec3(0.0, -0.04 * 0.5, 0.0);
        ManaballEntity manaball = new ManaballEntity(level, launch.position(), launch.velocity(),
                launch.arrowSizeSum(), launch.liftDirection(), acceleration, launch.size());
        level.addFreshEntity(manaball);
        return Map.of(MANABALL_KEY, ArrayObject.EntityRef.of(manaball));
    }

    private static void discardManaBall(ServerLevel level, BlockPos arrayPos,
                                        List<ParameterRune> runes,
                                        Map<String, Object> scratchData) {
        discardBoundEntities(level, scratchData, MANABALL_KEY);
    }
}
