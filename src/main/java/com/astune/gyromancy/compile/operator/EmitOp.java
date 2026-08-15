package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public abstract class EmitOp implements CompiledOp {
    private final ResourceLocation id;
    private final PositionedGlyph boundary;
    private final List<OpInput> matchedInputs;
    private final List<OpInput> inputs;

    protected EmitOp(ResourceLocation id, PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs) {
        this.id = id;
        this.boundary = boundary;
        this.matchedInputs = List.copyOf(matchedInputs);
        this.inputs = List.copyOf(inputs);
    }

    @Override
    public ResourceLocation id() {
        return id;
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

    public abstract List<Emission> emissions();

    /** Allows an emitted effect to resolve refreshed array geometry at activation time. */
    public List<Emission> emissions(OpRuntimeContext context) {
        return emissions();
    }

    public record Emission(Vec3 velocity, double motionSum, float sizeScale, boolean hasMotion) {}
}
