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
import com.astune.gyromancy.array.runtime.RuntimeHandle;
import com.astune.gyromancy.entity.ball.DryBallEntity;
import com.astune.gyromancy.entity.ball.WaterBallEntity;
import com.astune.gyromancy.symbol.CenterSymbol;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

public final class WaterProjectileOp extends EntityEffectOp {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "water_projectile");
    public static final ArrayEffectDefinition DEFINITION = new ArrayEffectDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("water"));
        }

        @Override
        public CompileResult<Operator> compile(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                               List<OpInput> inputs) {
            return WaterProjectileOp.create(boundary, matchedInputs, inputs);
        }
    };

    private WaterProjectileOp(PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs,
                              EffectAttributes attributes) {
        super(ID, ElementType.WATER, boundary, matchedInputs, inputs, attributes);
    }

    public static CompileResult<Operator> create(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                                 List<OpInput> inputs) {
        if (!hasMatchedRune(matchedInputs, "water")) {
            return new CompileResult.Failure<>(List.of(
                    new com.astune.gyromancy.array.compile.CompileDiagnostic(
                            "missing_primary_element", "Water operator requires water rune")));
        }
        CompileResult<EffectAttributes> attributes = compileAttributes(inputs, ElementType.WATER);
        if (attributes instanceof CompileResult.Failure<EffectAttributes> failure) {
            return new CompileResult.Failure<>(failure.diagnostics());
        }
        EffectAttributes attrs = ((CompileResult.Success<EffectAttributes>) attributes).value();
        return new CompileResult.Success<>(new WaterProjectileOp(boundary, matchedInputs, inputs, attrs));
    }

    public static WaterBallEntity create(Level level, Vec3 pos, Vec3 velocity, double arrowSizeSum,
                                         double liftDirection, Vec3 acceleration, float size) {
        WaterBallEntity entity = new WaterBallEntity(level, pos, velocity, arrowSizeSum, liftDirection, acceleration, size);
        entity.setPayload(defaultPayload());
        return entity;
    }

    public static List<EntityPayload> defaultPayload() {
        return List.of(
                new WaterBurstOp(),
                new CarryItemsOp()
        );
    }

    private static List<EntityPayload> payloadFor(WaterProjectileOp node) {
        return node.payload(defaultPayload());
    }

    @Override
    public RuntimeHandle activate(ServerLevel level) {
        PositionedGlyph center = primaryRune();
        if (center == null) return new RuntimeHandle(Map.of());
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
        Vec3 pos = CenterSymbol.glyphCenter(level, center).add(CenterSymbol.faceNormal(center).scale(size * 2.0));
        if (attributes().inverted()) {
            DryBallEntity dryball = new DryBallEntity(level, pos, velocity, motionSum, liftDirection, size);
            level.addFreshEntity(dryball);
            return new RuntimeHandle(Map.of(CenterSymbol.DRYBALL_KEY, ArrayObject.EntityRef.of(dryball)));
        }

        Vec3 acceleration = motions.isEmpty() ? Vec3.ZERO : new Vec3(0.0, -0.04 * 0.5, 0.0);
        WaterBallEntity waterball = create(level, pos, velocity, motionSum, liftDirection, acceleration, size);
        waterball.setPayload(payloadFor(this));
        level.addFreshEntity(waterball);
        return new RuntimeHandle(Map.of(CenterSymbol.WATERBALL_KEY, ArrayObject.EntityRef.of(waterball)));
    }

    @Override
    public void deactivate(ServerLevel level, Map<String, Object> scratchData) {
        CenterSymbol.boundEntity(level, scratchData, CenterSymbol.WATERBALL_KEY).ifPresent(entity -> entity.discard());
        CenterSymbol.boundEntity(level, scratchData, CenterSymbol.DRYBALL_KEY).ifPresent(entity -> entity.discard());
    }

    private PositionedGlyph primaryRune() {
        for (OpInput input : matchedInputs()) {
            if (input instanceof OpInput.Rune rune && "water".equals(rune.symbolName())) return rune.glyph();
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
