package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.ArrayEffectDefinition;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.EffectAttributes;
import com.astune.gyromancy.array.compile.MotionAttribute;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.OpInputs;
import com.astune.gyromancy.array.runtime.RuntimeHandle;
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
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "fireball");
    public static final ArrayEffectDefinition DEFINITION = new ArrayEffectDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("fire"));
        }

        @Override
        public CompileResult<Operator> compile(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                               List<OpInput> inputs) {
            return FireballOp.create(boundary, matchedInputs, inputs);
        }
    };

    public static final String STORED_MANA_KEY = "storedMana";
    public static final String OLD_SPAWNED_KEY = "oldSpawned";
    public static final String LIFETIME_KEY = "lifetime";

    private FireballOp(PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs,
                       EffectAttributes attributes) {
        super(ID, ElementType.FIRE, boundary, matchedInputs, inputs, attributes);
    }

    public static CompileResult<Operator> create(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                                 List<OpInput> inputs) {
        CompileResult<EffectAttributes> attributes = compileAttributes(inputs, ElementType.FIRE);
        if (attributes instanceof CompileResult.Failure<EffectAttributes> failure) {
            return new CompileResult.Failure<>(failure.diagnostics());
        }
        EffectAttributes attrs = ((CompileResult.Success<EffectAttributes>) attributes).value();
        return new CompileResult.Success<>(new FireballOp(boundary, matchedInputs, inputs, attrs));
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

    @Override
    public RuntimeHandle activate(ServerLevel level) {
        PositionedGlyph centerGlyph = primaryRune();
        if (centerGlyph == null) return new RuntimeHandle(Map.of());
        List<MotionAttribute> motions = attributes().motion();
        Vec3 velocity = Vec3.ZERO;
        double motionSum = 0.0;
        for (MotionAttribute motion : motions) {
            motionSum += motion.speed();
            if (motion.direction().lengthSqr() >= 1e-8) {
                velocity = velocity.add(motion.direction().normalize().scale(motion.speed()));
            }
        }

        float size = scale();
        double liftDirection = CenterSymbol.isFacingDown(boundary()) ? -1.0 : 1.0;
        Vec3 pos = CenterSymbol.glyphCenter(level, centerGlyph)
                .add(CenterSymbol.faceNormal(centerGlyph).scale(size * 2.0));
        Vec3 acceleration = motions.isEmpty() ? Vec3.ZERO : new Vec3(0.0, -0.04 * 0.5, 0.0);
        FireballEntity fireball = create(level, pos, velocity, motionSum, liftDirection, acceleration, size);
        fireball.setPayload(payloadFor(this));
        level.addFreshEntity(fireball);
        return new RuntimeHandle(Map.of(CenterSymbol.FIREBALL_KEY, ArrayObject.EntityRef.of(fireball)));
    }

    @Override
    public void deactivate(ServerLevel level, Map<String, Object> scratchData) {
        CenterSymbol.boundEntity(level, scratchData, CenterSymbol.FIREBALL_KEY)
                .ifPresent(entity -> entity.discard());
    }

    private static List<OnEntityTickOp> payloadFor(FireballOp node) {
        List<OnEntityTickOp> payload = new ArrayList<>();
        payload.add(new ExplosionOp());
        if (OpInputs.hasRune(node.inputs(), "fix")) payload.add(new SmeltOp());
        return List.copyOf(payload);
    }

    private PositionedGlyph primaryRune() {
        for (var input : matchedInputs()) {
            if (input instanceof OpInput.Rune rune && "fire".equals(rune.symbolName())) return rune.glyph();
        }
        return null;
    }

    private static boolean hasMatchedRune(List<OpInput> inputs, String symbolName) {
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Rune rune && symbolName.equals(rune.symbolName())) return true;
        }
        return false;
    }
}
