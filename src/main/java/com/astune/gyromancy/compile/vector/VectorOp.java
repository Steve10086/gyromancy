package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.compile.operator.CompiledOp;
import com.astune.gyromancy.compile.operator.EmitOp;
import com.astune.gyromancy.compile.operator.EntityPayload;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.api.geometry.SurfaceFrame;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * An Op-compatible structure whose only outward behavior is a dynamic vector.
 * Vector consumers pull the value with a VectorContext; the operation does not
 * enter the entity-payload tick lifecycle or mutate an entity directly.
 */
public abstract class VectorOp implements CompiledOp {
    public static final ResourceLocation RUNTIME_ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "vector_runtime");
    private final ResourceLocation id;
    private final PositionedGlyph boundary;
    private final List<OpInput> inputs;
    private final int color;
    protected VectorOp(ResourceLocation id, PositionedGlyph boundary,
                       List<OpInput> inputs, int color) {
        this.id = id;
        this.boundary = boundary;
        this.inputs = List.copyOf(inputs);
        this.color = color;
    }

    public abstract Vec3 provide(VectorContext context);

    protected static Vec3 localVector(VectorContext context, SurfaceFrame frame, Vec3 local) {
        if (local == null) return Vec3.ZERO;
        if (frame == null) return local;
        return frame.axisU().scale(local.x)
                .add(frame.axisV().scale(local.y))
                .add(frame.normal().scale(local.z));
    }

    @Override
    public final ResourceLocation id() {
        return id;
    }

    @Override
    public final PositionedGlyph boundary() {
        return boundary;
    }

    @Override
    public final List<OpInput> inputs() {
        return inputs;
    }

    @Override
    public final int color() {
        return color;
    }

    @Override
    public final EmitOp.Emission modifyEntityEmission(EmitOp.Emission emission,
                                                      com.astune.gyromancy.array.runtime.OpRuntimeContext context) {
        return emission;
    }

    @Override
    public final void contributeEntityPayloads(List<EntityPayload> payloads,
                                               com.astune.gyromancy.array.runtime.OpRuntimeContext context) {}
}
