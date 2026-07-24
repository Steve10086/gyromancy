package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.runtime.emit.EntityEmitter;
import com.astune.gyromancy.array.runtime.emit.EmitResult;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.EffectAttributes;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.array.runtime.RuntimeHandle;
import com.astune.gyromancy.entity.ball.DryBallEntity;
import com.astune.gyromancy.entity.ball.WaterBallEntity;
import com.astune.gyromancy.symbol.CenterSymbol;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

public final class WaterProjectileOp extends EntityEffectOp {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "water_projectile");
    public static final OpDefinition DEFINITION = new OpDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("water"));
        }

        @Override
        public CompileResult<CompiledOp> compile(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                               List<OpInput> inputs) {
            return WaterProjectileOp.create(boundary, matchedInputs, inputs);
        }
    };

    private WaterProjectileOp(PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs,
                              EffectAttributes attributes) {
        super(ID, ElementType.WATER, boundary, matchedInputs, inputs, attributes);
    }

    public static CompileResult<CompiledOp> create(PositionedGlyph boundary, List<OpInput> matchedInputs,
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
    public RuntimeHandle activate(OpRuntimeContext ctx) {
        ServerLevel level = ctx.level();
        PositionedGlyph center = primaryRune();
        if (center == null) return new RuntimeHandle(Map.of());
        EmitResult result = new EmitResult();
        double liftDirection = CenterSymbol.isFacingDown(boundary()) ? -1.0 : 1.0;
        Vec3 centerPos = CenterSymbol.glyphCenter(level, center);
        Vec3 normal = CenterSymbol.faceNormal(center);
        for (EmitOp.Emission emission : emissions()) {
            float size = Math.max(0.1F, scale() * emission.sizeScale());
            Vec3 pos = centerPos.add(normal.scale(size * 2.0));
            Vec3 acceleration = emission.hasMotion() ? new Vec3(0.0, -0.04 * 0.5, 0.0) : Vec3.ZERO;
            Entity entity;
            if (attributes().inverted()) {
                entity = new DryBallEntity(level, pos, emission.velocity(), emission.motionSum(), liftDirection, size);
            } else {
                WaterBallEntity waterball = create(level, pos, emission.velocity(),
                        emission.motionSum(), liftDirection, acceleration, size);
                waterball.setPayload(payloadFor(this));
                entity = waterball;
            }
            EntityEmitter.INSTANCE.emit(level, ID, entity, result);
        }
        return result.toRuntimeHandle();
    }

    @Override
    public void deactivate(OpRuntimeContext ctx, Map<String, Object> scratchData) {
        ServerLevel level = ctx.level();
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
