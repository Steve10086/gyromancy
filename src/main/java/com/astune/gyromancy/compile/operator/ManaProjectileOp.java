package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.ParameterRune;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.EffectAttributes;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.RegisteredOp;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.array.runtime.RuntimeHandle;
import com.astune.gyromancy.array.runtime.emit.EmitResult;
import com.astune.gyromancy.array.runtime.emit.EntityEmitter;
import com.astune.gyromancy.entity.ball.ManaballEntity;
import com.astune.gyromancy.symbol.CenterSymbol;
import com.astune.gyromancy.symbol.SymbolCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RegisteredOp
public final class ManaProjectileOp extends EntityEffectOp {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "mana_projectile");
    public static final OpDefinition DEFINITION = new OpDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("mana"));
        }

        @Override
        public List<OpInputMatcher> accepted() {
            return acceptedProjectileInputs();
        }

        @Override
        public CompileResult<CompiledOp> compile(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                               List<OpInput> inputs) {
            return ManaProjectileOp.create(boundary, matchedInputs, inputs);
        }
    };

    private ManaProjectileOp(PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs,
                             EffectAttributes attributes) {
        super(ID, ElementType.MANA, boundary, matchedInputs, inputs, attributes);
    }

    public static CompileResult<CompiledOp> create(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                                 List<OpInput> inputs) {
        CompileResult<EffectAttributes> attributes = compileAttributes(inputs, ElementType.MANA);
        if (attributes instanceof CompileResult.Failure<EffectAttributes> failure) {
            return new CompileResult.Failure<>(failure.diagnostics());
        }
        EffectAttributes attrs = ((CompileResult.Success<EffectAttributes>) attributes).value();
        return new CompileResult.Success<>(new ManaProjectileOp(boundary, matchedInputs, inputs, attrs));
    }

    @Override
    public RuntimeHandle activate(OpRuntimeContext ctx) {
        ServerLevel level = ctx.level();
        PositionedGlyph center = primaryRune();
        if (center == null) return new RuntimeHandle(Map.of());
        if (hasUnsupportedDirectRune(center)) return new RuntimeHandle(Map.of());

        EmitResult result = new EmitResult();
        double liftDirection = CenterSymbol.isFacingDown(boundary()) ? -1.0 : 1.0;
        Vec3 centerPos = CenterSymbol.glyphCenter(level, center);
        Vec3 normal = CenterSymbol.faceNormal(center);
        for (EmitOp.Emission emission : emissions()) {
            float size = Math.max(0.1F, scale() * emission.sizeScale());
            Vec3 pos = centerPos.add(normal.scale(size * 2.0));
            Vec3 acceleration = emission.hasMotion() ? new Vec3(0.0, -0.04 * 0.5, 0.0) : Vec3.ZERO;
            ManaballEntity manaball = new ManaballEntity(level, pos, emission.velocity(),
                    emission.motionSum(), liftDirection, acceleration, size);
            manaball.setPayload(payloadFor(this));
            EntityEmitter.INSTANCE.emit(level, ID, manaball, result);
        }
        return result.toRuntimeHandle();
    }

    @Override
    public void deactivate(OpRuntimeContext ctx, Map<String, Object> scratchData) {
        ServerLevel level = ctx.level();
        PositionedGlyph center = primaryRune();
        if (center == null) return;
        SymbolCatalog.getEndEffect(center.symbolId())
                .execute(level, boundary().worldPos(), toRuneParams(inputs()), scratchData);
    }

    private PositionedGlyph primaryRune() {
        for (OpInput input : matchedInputs()) {
            if (input instanceof OpInput.Rune rune && "mana".equals(rune.symbolName())) return rune.glyph();
        }
        return null;
    }

    private List<PositionedGlyph> legacyRunes(PositionedGlyph center) {
        List<PositionedGlyph> runes = new ArrayList<>();
        for (OpInput input : inputs()) {
            if (!(input instanceof OpInput.Rune rune)) continue;
            if (!rune.glyph().glyphUuid().equals(center.glyphUuid())) runes.add(rune.glyph());
        }
        return List.copyOf(runes);
    }

    private boolean hasUnsupportedDirectRune(PositionedGlyph center) {
        for (PositionedGlyph rune : legacyRunes(center)) {
            if (!"arrow".equals(rune.symbolId().getPath())) return true;
        }
        return false;
    }

    private static List<EntityPayload> payloadFor(ManaProjectileOp node) {
        return node.payload(List.of());
    }

    private static List<ParameterRune> toRuneParams(List<OpInput> inputs) {
        List<ParameterRune> params = new ArrayList<>();
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Rune rune) {
                params.add(new ParameterRune(rune.glyph().symbolId(), rune.glyph().confidence(), ""));
            }
        }
        return List.copyOf(params);
    }

}
