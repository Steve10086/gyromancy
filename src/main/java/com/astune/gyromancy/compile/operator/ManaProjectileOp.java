package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.ParameterRune;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileResult;
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
import com.astune.gyromancy.symbol.SecretTextSymbol;
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
            return List.of(
                    OpInputMatcher.rune("arrow"),
                    OpInputMatcher.rune("revert"),
                    OpInputMatcher.op(CompiledOp.class));
        }

        @Override
        public CompileResult<CompiledOp> compile(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                               List<OpInput> inputs) {
            return ManaProjectileOp.create(boundary, matchedInputs, inputs);
        }
    };

    private ManaProjectileOp(PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs) {
        super(ID, ElementType.MANA, boundary, matchedInputs, inputs);
    }

    public static CompileResult<CompiledOp> create(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                                 List<OpInput> inputs) {
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Rune rune && "revert".equals(rune.symbolName())) {
                return new CompileResult.Failure<>(List.of(
                        new com.astune.gyromancy.array.compile.CompileDiagnostic(
                                "invalid_element_inverse", "Mana has no inverse element")));
            }
        }
        return new CompileResult.Success<>(new ManaProjectileOp(boundary, matchedInputs, inputs));
    }

    @Override
    public RuntimeHandle activate(OpRuntimeContext ctx) {
        ServerLevel level = ctx.level();
        PositionedGlyph center = primaryRune();
        if (center == null) return new RuntimeHandle(Map.of());
        if (hasUnsupportedDirectRune(center)) return new RuntimeHandle(Map.of());

        EmitResult result = new EmitResult();
        Vec3 centerPos = ctx.positionFor(center);
        Vec3 normal = ctx.normalFor(center);
        double liftDirection = normal.y < -0.999 ? -1.0 : 1.0;
        for (EmitOp.Emission emission : emissions(ctx)) {
            float size = Math.max(0.1F, scale(ctx) * emission.sizeScale());
            Vec3 pos = centerPos.add(normal.scale(size * 2.0));
            Vec3 acceleration = Vec3.ZERO;
            ManaballEntity manaball = new ManaballEntity(level, pos, emission.velocity(),
                    emission.motionSum(), liftDirection, acceleration, size);
            manaball.setParentBindingAllowed(ctx.assignParent());
            if (ctx.assignParent()) manaball.setParent(ctx.parent());
            manaball.setPayload(payload(defaultPayload(), ctx));
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

    public static List<EntityPayload> defaultPayload() {
        return List.of(
                new FollowingOp(),
                new ElementOp(ElementType.MANA, 2.0f, 0.0f)
        );
    }

    private static List<ParameterRune> toRuneParams(List<OpInput> inputs) {
        List<ParameterRune> params = new ArrayList<>();
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Rune rune) {
                SecretTextSymbol secretText = SecretTextSymbol.fromId(rune.glyph().symbolId());
                params.add(secretText == null
                        ? new ParameterRune(rune.glyph().symbolId(), rune.glyph().confidence(), "")
                        : secretText.toParameterRune(rune.glyph().confidence()));
            }
        }
        return List.copyOf(params);
    }

}
