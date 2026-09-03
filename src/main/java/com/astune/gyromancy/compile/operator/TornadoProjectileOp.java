package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileDiagnostic;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.RegisteredOp;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.entity.ball.TornadoBallEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Emits a wind-element tornado ball. */
@RegisteredOp
public final class TornadoProjectileOp extends ProjectileEntityOp {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "tornado_projectile");
    public static final OpDefinition DEFINITION = new OpDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("wind"));
        }

        @Override
        public List<OpInputMatcher> accepted() {
            // Direct arrows are deliberately excluded. Motion may still be
            // authored through a nested motion or emission op.
            return List.of(
                    OpInputMatcher.rune("engaging"),
                    OpInputMatcher.rune("fix"),
                    OpInputMatcher.op(CompiledOp.class));
        }

        @Override
        public CompileResult<CompiledOp> compile(PositionedGlyph boundary,
                                                  List<OpInput> matchedInputs,
                                                  List<OpInput> inputs) {
            return TornadoProjectileOp.create(boundary, matchedInputs, inputs);
        }
    };

    private TornadoProjectileOp(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                List<OpInput> inputs) {
        super(ID, ElementType.WIND, boundary, matchedInputs, inputs);
    }

    public static CompileResult<CompiledOp> create(PositionedGlyph boundary,
                                                    List<OpInput> matchedInputs,
                                                    List<OpInput> inputs) {
        if (primaryRune(matchedInputs) == null) {
            return new CompileResult.Failure<>(List.of(new CompileDiagnostic(
                    "missing_primary_element", "Tornado projectile requires wind rune")));
        }
        return new CompileResult.Success<>(new TornadoProjectileOp(boundary, matchedInputs, inputs));
    }

    @Override
    public TornadoBallEntity create(Level level, Vec3 pos, Vec3 velocity,
                                    Vec3 acceleration, float size) {
        return new TornadoBallEntity(level, pos, velocity, acceleration, size);
    }

    @Override
    protected Collection<? extends EntityPayload> conditionalPayload() {
        return List.of();
    }

    @Override
    protected Collection<? extends EntityPayload> defaultPayload() {
        return List.of(new TornadoAttractionOp(true), new TornadoImpactOp());
    }

    @Override
    protected PositionedGlyph primaryRune() {
        return primaryRune(matchedInputs());
    }

    @Override
    protected ResourceLocation getId() {
        return ID;
    }

    @Override
    public void deactivate(OpRuntimeContext ctx, Map<String, Object> scratchData) {
        // OpRuntimeDispatcher discards emitted entities before invoking this hook.
    }

    private static PositionedGlyph primaryRune(List<OpInput> inputs) {
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Rune rune && "wind".equals(rune.symbolName())) return rune.glyph();
        }
        return null;
    }
}
