package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.symbol.ParameterRune;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.entity.ball.FireballEntity;
import com.astune.gyromancy.entity.ball.IceBallEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/** Fire-element center symbol. Launches a fireball projectile on array activation. */
public final class FireSymbol extends CenterSymbol {
    public static final FireSymbol INSTANCE = new FireSymbol();
    private FireSymbol() { super("fire", 3, false, 0xFFFF8888); }

    @Override
    public SymbolCatalog.CenterEffect centerEffect() { return FireSymbol::launchFireball; }

    @Override
    public SymbolCatalog.EndEffect endEffect() { return FireSymbol::discardFireballs; }

    // ═══════════════════ Fireball launch effect ═══════════════════

    private static Map<String, Object> launchFireball(ServerLevel level, BlockPos arrayPos,
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
            IceBallEntity iceball = new IceBallEntity(level, launch.position(), launch.velocity(), launch.size());
            level.addFreshEntity(iceball);
            return Map.of(ICEBALL_KEY, ArrayObject.EntityRef.of(iceball));
        }

        Vec3 acceleration = arrows.isEmpty() ? Vec3.ZERO : new Vec3(0.0, -0.04 * 0.5, 0.0);
        FireballEntity fireball = new FireballEntity(level, launch.position(), launch.velocity(), acceleration, launch.size());
        level.addFreshEntity(fireball);
        return Map.of(FIREBALL_KEY, ArrayObject.EntityRef.of(fireball));
    }

    private static void discardFireballs(ServerLevel level, BlockPos arrayPos,
                                         List<ParameterRune> runes,
                                         Map<String, Object> scratchData) {
        discardBoundEntities(level, scratchData, FIREBALL_KEY, OLD_FIREBALL_KEY, ICEBALL_KEY);
    }
}
