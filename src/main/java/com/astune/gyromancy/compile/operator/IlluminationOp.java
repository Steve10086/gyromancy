package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.RegisteredOp;
import com.astune.gyromancy.entity.ball.IlluminationEntity;
import com.astune.gyromancy.symbol.SymbolCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

/** Star-bound projectile that leaves temporary light blocks around its ball. */
@RegisteredOp
public final class IlluminationOp extends ProjectileEntityOp {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "illumination");

    public static final OpDefinition DEFINITION = new OpDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("star"));
        }

        @Override
        public List<OpInputMatcher> accepted() {
            return List.of(
                    OpInputMatcher.rune("arrow"),
                    OpInputMatcher.rune("engaging"),
                    OpInputMatcher.rune("fix"),
                    OpInputMatcher.rune("revert"),
                    OpInputMatcher.op(CompiledOp.class));
        }

        @Override
        public CompileResult<CompiledOp> compile(PositionedGlyph boundary,
                                                  List<OpInput> matchedInputs,
                                                  List<OpInput> inputs) {
            return new CompileResult.Success<>(new IlluminationOp(boundary, matchedInputs, inputs));
        }
    };

    private IlluminationOp(PositionedGlyph boundary, List<OpInput> matchedInputs,
                           List<OpInput> inputs) {
        super(ID, ElementType.LIGHT, boundary, matchedInputs, inputs);
    }

    @Override
    public IlluminationEntity create(Level level, Vec3 pos, Vec3 velocity,
                                     Vec3 acceleration, float size) {
        return new IlluminationEntity(level, pos, velocity, acceleration, size);
    }

    @Override
    protected List<EntityPayload> payloadFor() {
        return List.of(ElementVolumeOp.stability(ElementType.LIGHT));
    }

    @Override
    Collection<? extends EntityPayload> conditionalPayload() {
        return null;
    }

    @Override
    Collection<? extends EntityPayload> defaultPayload() {
        return null;
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected PositionedGlyph primaryRune() {
        for (OpInput input : matchedInputs()) {
            if (input instanceof OpInput.Rune rune && "star".equals(rune.symbolName())) {
                return rune.glyph();
            }
        }
        return null;
    }

    @Override
    public int color() {
        return SymbolCatalog.glyphColorFor(ResourceLocation.fromNamespaceAndPath(
                Gyromancy.MODID, "star"));
    }

    @Override
    public void deactivate(com.astune.gyromancy.array.runtime.OpRuntimeContext ctx,
                           java.util.Map<String, Object> scratchData) {
        // Emitted entities are discarded by OpRuntimeDispatcher before this hook.
    }
}
