package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.symbol.ParameterRune;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.entity.ball.DryBallEntity;
import com.astune.gyromancy.entity.ball.WaterBallEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/** Water-element center symbol. */
public final class WaterSymbol extends CenterSymbol {
    public static final WaterSymbol INSTANCE = new WaterSymbol();
    private WaterSymbol() { super("water", 0, false, 0xFF8888FF); }

    @Override
    public SymbolCatalog.CenterEffect centerEffect() { return WaterSymbol::launchWaterEffect; }

    @Override
    public SymbolCatalog.EndEffect endEffect() { return WaterSymbol::discardWaterEffects; }

    private static Map<String, Object> launchWaterEffect(ServerLevel level, BlockPos arrayPos,
                                                         PositionedGlyph circleGlyph,
                                                         PositionedGlyph centerGlyph,
                                                         List<PositionedGlyph> runes) {
        if (runes.stream().anyMatch(rune -> !"arrow".equals(rune.symbolId().getPath())
                && !"revert".equals(rune.symbolId().getPath()))) return Map.of();
        long revertCount = runes.stream().filter(rune -> "revert".equals(rune.symbolId().getPath())).count();
        if (revertCount > 1) return Map.of();

        List<PositionedGlyph> arrows = runes.stream()
                .filter(rune -> "arrow".equals(rune.symbolId().getPath())).toList();
        LaunchData launch = launchData(level, circleGlyph, centerGlyph, arrows);
        if (revertCount == 1) {
            DryBallEntity dryball = new DryBallEntity(level, launch.position(), launch.velocity(),
                    launch.arrowSizeSum(), launch.liftDirection(), launch.size());
            level.addFreshEntity(dryball);
            return Map.of(DRYBALL_KEY, ArrayObject.EntityRef.of(dryball));
        }

        Vec3 acceleration = arrows.isEmpty() ? Vec3.ZERO : new Vec3(0.0, -0.04 * 0.5, 0.0);
        WaterBallEntity waterball = new WaterBallEntity(level, launch.position(), launch.velocity(),
                launch.arrowSizeSum(), launch.liftDirection(), acceleration, launch.size());
        level.addFreshEntity(waterball);
        return Map.of(WATERBALL_KEY, ArrayObject.EntityRef.of(waterball));
    }

    private static void discardWaterEffects(ServerLevel level, BlockPos arrayPos,
                                             List<ParameterRune> runes,
                                             Map<String, Object> scratchData) {
        discardBoundEntities(level, scratchData, WATERBALL_KEY, DRYBALL_KEY);
    }
}
