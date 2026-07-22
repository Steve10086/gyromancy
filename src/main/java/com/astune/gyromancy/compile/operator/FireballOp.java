package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.EffectNode;
import com.astune.gyromancy.array.compile.MotionAttribute;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.OpInputs;
import com.astune.gyromancy.entity.ball.FireballEntity;
import com.astune.gyromancy.symbol.CenterSymbol;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public final class FireballOp extends ProjectileOp {
    public static final FireballOp DEFINITION = new FireballOp();

    public static final String STORED_MANA_KEY = "storedMana";
    public static final String OLD_SPAWNED_KEY = "oldSpawned";
    public static final String LIFETIME_KEY = "lifetime";

    private FireballOp() {
        super(ElementType.FIRE);
    }

    public static FireballEntity create(Level level, Vec3 pos, Vec3 velocity, double arrowSizeSum,
                                        double liftDirection, Vec3 acceleration, float size) {
        FireballEntity entity = new FireballEntity(level, pos, velocity, arrowSizeSum, liftDirection, acceleration, size);
        entity.setPayload(List.of());
        return entity;
    }

    public static List<OnEntityTickOp> defaultPayload() {
        return List.of();
    }

    public static Map<String, Object> activate(ServerLevel level, PositionedGlyph circleGlyph,
                                               PositionedGlyph centerGlyph, EffectNode node) {
        List<MotionAttribute> motions = node.attributes().motion();
        Vec3 velocity = Vec3.ZERO;
        double motionSum = 0.0;
        for (MotionAttribute motion : motions) {
            motionSum += motion.speed();
            if (motion.direction().lengthSqr() >= 1e-8) {
                velocity = velocity.add(motion.direction().normalize().scale(motion.speed()));
            }
        }

        float size = (float)node.shape().scale();
        double liftDirection = CenterSymbol.isFacingDown(circleGlyph) ? -1.0 : 1.0;
        Vec3 pos = CenterSymbol.glyphCenter(level, centerGlyph)
                .add(CenterSymbol.faceNormal(centerGlyph).scale(size * 2.0));
        Vec3 acceleration = motions.isEmpty() ? Vec3.ZERO : new Vec3(0.0, -0.04 * 0.5, 0.0);
        FireballEntity fireball = create(level, pos, velocity, motionSum, liftDirection, acceleration, size);
        fireball.setPayload(payloadFor(node));
        level.addFreshEntity(fireball);
        return Map.of(CenterSymbol.FIREBALL_KEY, ArrayObject.EntityRef.of(fireball));
    }

    public static void deactivate(ServerLevel level, Map<String, Object> scratchData) {
        CenterSymbol.boundEntity(level, scratchData, CenterSymbol.FIREBALL_KEY)
                .ifPresent(entity -> entity.discard());
    }

    private static List<OnEntityTickOp> payloadFor(EffectNode node) {
        List<OnEntityTickOp> payload = new ArrayList<>();
        if (OpInputs.hasRune(node.inputs(), "fix")) {
            payload.add(new SmeltOp());
        } else {
            payload.add(new ExplosionOp());
        }
        return List.copyOf(payload);
    }

    @Override
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "fireball");
    }

    @Override
    public List<OpInputMatcher> match() {
        return List.of(OpInputMatcher.rune("fire"));
    }
}
