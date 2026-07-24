package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.ArrayEffectDefinition;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.runtime.RuntimeHandle;
import com.astune.gyromancy.symbol.SymbolCatalog;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;

public final class ElementOp extends OnEntityTickOp implements Operator {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "element");
    private static final ResourceLocation ENGAGING_SYMBOL =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "engaging");
    public static final Codec<ElementOp> CODEC = Codec.unit(ElementOp::new);
    public static final ArrayEffectDefinition DEFINITION = new ArrayEffectDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("engaging"));
        }

        @Override
        public CompileResult<Operator> compile(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                               List<OpInput> inputs) {
            return new CompileResult.Success<>(new ElementOp(boundary, matchedInputs, inputs));
        }
    };

    private final PositionedGlyph boundary;
    private final List<OpInput> matchedInputs;
    private final List<OpInput> inputs;

    public ElementOp() {
        this(null, List.of(), List.of());
    }

    private ElementOp(PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs) {
        this.boundary = boundary;
        this.matchedInputs = List.copyOf(matchedInputs);
        this.inputs = List.copyOf(inputs);
    }

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<ElementOp> codec() {
        return CODEC;
    }

    @Override
    public void onEntityTick(EntityTickContext ctx) {
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

    public List<OpInput> matchedInputs() {
        return matchedInputs;
    }

    @Override
    public RuntimeHandle activate(ServerLevel level) {
        return new RuntimeHandle(Map.of());
    }

    @Override
    public void deactivate(ServerLevel level, Map<String, Object> scratchData) {
    }

    @Override
    public int color() {
        return SymbolCatalog.glyphColorFor(ENGAGING_SYMBOL);
    }
}
