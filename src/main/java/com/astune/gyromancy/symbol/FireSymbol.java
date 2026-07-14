package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.entity.FireballEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    // ═══════════════════ Fireball launch effect ═══════════════════

    private static Map<String, Object> launchFireball(ServerLevel level, BlockPos arrayPos,
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
        level.addFreshEntity(new FireballEntity(level, spawnPos, initialVelocity, acceleration, size));
        return Map.of();
    }
}
