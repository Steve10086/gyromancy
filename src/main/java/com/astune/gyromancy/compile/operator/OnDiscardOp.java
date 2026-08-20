package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.RegisteredOp;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.symbol.SymbolCatalog;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Attaches entity effects which are emitted when the owning effect is discarded. */
@RegisteredOp
public final class OnDiscardOp implements CompiledOp {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "on_discard");
    private static final ResourceLocation CROSS_SYMBOL =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "cross");

    public static final OpDefinition DEFINITION = new OpDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("cross"));
        }

        @Override
        public List<OpInputMatcher> accepted() {
            return List.of(OpInputMatcher.op(EntityEffectOp.class));
        }

        @Override
        public CompileResult<CompiledOp> compile(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                                  List<OpInput> inputs) {
            return new CompileResult.Success<>(new OnDiscardOp(boundary, matchedInputs, inputs));
        }
    };

    private final PositionedGlyph boundary;
    private final List<OpInput> matchedInputs;
    private final List<OpInput> inputs;
    private final List<EntityEffectOp> effects;

    private OnDiscardOp(PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs) {
        this.boundary = boundary;
        this.matchedInputs = List.copyOf(matchedInputs);
        this.inputs = List.copyOf(inputs);
        List<EntityEffectOp> effects = new ArrayList<>();
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Op op && op.operator() instanceof EntityEffectOp effect) {
                effects.add(effect);
            }
        }
        this.effects = List.copyOf(effects);
    }

    public List<EntityEffectOp> effects() {
        return effects;
    }

    public List<OpInput> matchedInputs() {
        return matchedInputs;
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public PositionedGlyph boundary() {
        return boundary;
    }

    @Override
    public List<OpInput> inputs() {
        return inputs;
    }

    @Override
    public int color() {
        return SymbolCatalog.glyphColorFor(CROSS_SYMBOL);
    }

    @Override
    public void contributeEntityPayloads(List<EntityPayload> payloads, OpRuntimeContext context) {
        payloads.add(new OnDiscardPayload(new OnDiscardContent(effects, null)));
    }
}
