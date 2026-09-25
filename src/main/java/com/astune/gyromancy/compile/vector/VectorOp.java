package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.compile.operator.CompiledOp;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.api.geometry.SurfaceFrame;
import com.astune.gyromancy.symbol.SecretTextSymbol;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * An Op-compatible structure whose only outward behavior is a dynamic vector.
 * Vector consumers pull the value with a VectorContext; the operation does not
 * enter the entity-payload tick lifecycle or mutate an entity directly.
 *
 * <p>Every vector op shares the binary secret-text scaler and the revert rune.
 * {@link #provide} applies both around {@link #provideVector}; ops that give
 * the revert rune a meaning of their own override {@link #applyRevert}.</p>
 */
public abstract class VectorOp implements CompiledOp {
    public static final ResourceLocation RUNTIME_ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "vector_runtime");
    private final ResourceLocation id;
    private final PositionedGlyph boundary;
    private final List<OpInput> rawInputs;
    private final int color;
    private final double scale;
    private final boolean reverted;

    protected VectorOp(ResourceLocation id, PositionedGlyph boundary,
                       List<OpInput> inputs, int color) {
        this(id, boundary, inputs, color, 1.0, false);
    }

    protected VectorOp(ResourceLocation id, PositionedGlyph boundary,
                       List<OpInput> inputs, int color, double scale, boolean reverted) {
        this.id = id;
        this.boundary = boundary;
        this.rawInputs = List.copyOf(inputs);
        this.color = color;
        this.scale = Double.isFinite(scale) ? scale : 1.0;
        this.reverted = reverted;
    }

    public final Vec3 provide(VectorContext context) {
        Vec3 vector = provideVector(context);
        if (scale != 1.0) vector = vector.scale(scale);
        return applyRevert(vector);
    }

    protected abstract Vec3 provideVector(VectorContext context);

    /** Applies the shared revert rune; ops that interpret revert themselves override this. */
    protected Vec3 applyRevert(Vec3 vector) {
        return reverted ? vector.scale(-1.0) : vector;
    }

    protected final double scale() {
        return scale;
    }

    protected final boolean reverted() {
        return reverted;
    }

    /** Decodes the binary secret-text scaler: bit0 flags a reciprocal magnitude. */
    static double secretScale(List<OpInput> inputs) {
        int mask = 0;
        for (OpInput input : inputs) {
            if (!(input instanceof OpInput.Rune rune)) continue;
            SecretTextSymbol secretText = SecretTextSymbol.fromId(rune.glyph().symbolId());
            if (secretText == null) continue;
            mask |= 1 << secretText.type().ordinal();
        }
        if (mask == 0) return 1.0;
        int size = mask >> 1;
        if (size == 0) return 1.0;
        return (mask & 1) != 0 ? 1.0 / size : size;
    }

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

    /** The authored inputs this vector was compiled from. */
    protected final List<OpInput> rawInputs() {
        return rawInputs;
    }

    /**
     * Nested vectors composed into this one. They are exposed through
     * {@link #inputs()} as executable children so the final Op tree has a
     * single edge set.
     */
    List<VectorOp> composedVectors() {
        return List.of();
    }

    @Override
    public final List<OpInput> inputs() {
        List<VectorOp> composed = composedVectors();
        if (composed.isEmpty()) return rawInputs;
        List<OpInput> combined = new ArrayList<>(rawInputs.size() + composed.size());
        combined.addAll(rawInputs);
        for (VectorOp vector : composed) {
            boolean present = false;
            for (OpInput input : combined) {
                if (input instanceof OpInput.Op op && op.operator() == vector) {
                    present = true;
                    break;
                }
            }
            if (!present) combined.add(new OpInput.Op(vector));
        }
        return List.copyOf(combined);
    }

    @Override
    public final int color() {
        return color;
    }

}
