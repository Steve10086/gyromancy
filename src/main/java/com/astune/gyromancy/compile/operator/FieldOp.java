package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.element.ManaElements;
import com.astune.gyromancy.api.field.MagicFieldShape;
import com.astune.gyromancy.api.field.ShapeOrientation;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileDiagnostic;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.array.runtime.OpRuntimeFailure;
import com.astune.gyromancy.array.runtime.RuntimeHandle;
import com.astune.gyromancy.array.runtime.emit.EmitResult;
import com.astune.gyromancy.array.runtime.emit.EntityEmitter;
import com.astune.gyromancy.entity.field.MagicFieldEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Common runtime for persistent, stationary magic fields.
 *
 * <p>Unlike {@link ProjectileEntityOp}, a field has no emission loop, launch
 * velocity, acceleration, or projectile-only deferred cleanup. A concrete
 * field op supplies the configured field entity and its payloads.</p>
 */
public abstract class FieldOp extends EntityEffectOp {
    private static final String MOMENTUM_UNSUPPORTED = "field_rejects_momentum";

    protected FieldOp(ResourceLocation id, ElementType element, PositionedGlyph boundary,
                      List<OpInput> matchedInputs, List<OpInput> inputs) {
        super(id, element, boundary, matchedInputs, inputs);
    }

    /** Every field costs 20 points of its own element instead of the default mana. */
    @Override
    public ManaElements getCost() {
        return elementCost(20.0);
    }

    /** Rebuilds the concrete field Op after its local child inputs are materialized. */
    @Override
    public abstract FieldOp copyWithInputs(List<OpInput> inputs);

    @Override
    public final RuntimeHandle activate(OpRuntimeContext context) {
        ServerLevel level = context.level();
        ShapeSelection shapeSelection = selectShape(context);
        if (shapeSelection.requested() && shapeSelection.shape().isEmpty()) {
            return new RuntimeHandle(Map.of());
        }

        MagicFieldEntity field = shapeSelection.shape()
                .map(shape -> create(level, fieldPosition(context), shape, context))
                .orElseGet(() -> create(level, fieldPosition(context), context));
        if (field == null) return new RuntimeHandle(Map.of());
        field.setPos(field.position().add(normalBoundaryOffset(
                field.shape(), field.shapeOrientation(), context.normalFor(boundary()))));

        field.setParentBindingAllowed(context.assignParent());
        if (context.assignParent()) field.setParent(context.parent());
        field.setPayload(payloadFor(context));

        EmitResult result = new EmitResult();
        EntityEmitter.INSTANCE.emit(level, id(), field, result);
        return result.toRuntimeHandle();
    }

    @Override
    public final void deactivate(OpRuntimeContext context, Map<String, Object> scratchData) {
        EmitResult.discardEmittedEntities(context.level(), scratchData);
    }

    /**
     * Creates the stationary field. Shapes, direction, element, and energy
     * are supplied by the concrete field entity/op rather than by momentum.
     */
    protected abstract MagicFieldEntity create(Level level, Vec3 position);

    /**
     * Context-aware creation hook for fields whose immutable configuration is
     * derived from live glyphs (for example, a wind direction assembled from
     * arrow runes). Existing field operators retain the simpler factory.
     */
    protected MagicFieldEntity create(Level level, Vec3 position,
                                      OpRuntimeContext context) {
        return create(level, position);
    }

    /**
     * Creates a field with a shape supplied by a nested {@link ShapeOp}.
     * Existing field operators that construct their own shape keep the
     * two-argument contract; shape-aware operators override this method.
     */
    protected MagicFieldEntity create(Level level, Vec3 position, MagicFieldShape shape) {
        return create(level, position);
    }

    /** Context-aware shape factory; delegates to the legacy shape hook by default. */
    protected MagicFieldEntity create(Level level, Vec3 position, MagicFieldShape shape,
                                      OpRuntimeContext context) {
        return create(level, position, shape);
    }

    /** The default anchor is the live position of this op's boundary glyph. */
    protected Vec3 fieldPosition(OpRuntimeContext context) {
        return context.positionFor(boundary());
    }

    /**
     * Moves a field centre out from the source surface so its boundary on the
     * surface side is flush with that surface. The opposite normal is used
     * for the support query because that is the rear-facing boundary.
     */
    static Vec3 normalBoundaryOffset(MagicFieldShape shape, ShapeOrientation orientation,
                                     Vec3 normal) {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(orientation, "orientation");
        if (normal == null || !Double.isFinite(normal.x) || !Double.isFinite(normal.y)
                || !Double.isFinite(normal.z) || normal.lengthSqr() < 1.0E-12) {
            throw new IllegalArgumentException("Field source normal must be finite and non-zero");
        }
        Vec3 unitNormal = normal.normalize();
        double distance = shape.supportDistance(orientation, unitNormal.scale(-1.0));
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException("Field shape boundary distance must be finite and non-negative");
        }
        return unitNormal.scale(distance);
    }

    protected List<EntityPayload> payloadFor() {
        LinkedHashSet<EntityPayload> payloads = new LinkedHashSet<>(defaultPayload());
        payloads.addAll(conditionalPayload());
        return payload(new ArrayList<>(payloads));
    }

    protected List<EntityPayload> payloadFor(OpRuntimeContext context) {
        LinkedHashSet<EntityPayload> payloads = new LinkedHashSet<>(defaultPayload());
        payloads.addAll(conditionalPayload());
        return payload(new ArrayList<>(payloads), context);
    }

    protected abstract Collection<? extends EntityPayload> conditionalPayload();

    protected abstract Collection<? extends EntityPayload> defaultPayload();

    private ShapeSelection selectShape(OpRuntimeContext context) {
        ShapeOp shapeOp = null;
        for (OpInput input : inputs) {
            if (!(input instanceof OpInput.Op op) || !(op.operator() instanceof ShapeOp candidate)) {
                continue;
            }
            if (shapeOp != null) {
                OpRuntimeFailure.terminate(context, this, OpRuntimeFailure.Kind.RUNTIME_ERROR,
                        "A field may consume only one shape operator");
                return new ShapeSelection(true, Optional.empty());
            }
            shapeOp = candidate;
        }
        return shapeOp == null
                ? new ShapeSelection(false, Optional.empty())
             : new ShapeSelection(true, shapeOp.materializedShape().isPresent()
                     ? shapeOp.materializedShape() : shapeOp.resolveShape(context));
    }

    private record ShapeSelection(boolean requested, Optional<MagicFieldShape> shape) {}

    /**
     * Field operators currently accept every compiled child except momentum:
     * fields are immobile, so neither its spawn velocity nor tick acceleration
     * has a meaningful field interpretation.
     */
    public static boolean supportsChild(CompiledOp child) {
        return !(child instanceof MomentumOp);
    }

    /**
     * Helper for a concrete field op's {@code create(...)} method after its
     * own validation. Its definition may still use {@code CompiledOp.class}
     * as the broad accepted matcher.
     */
    protected static <T extends CompiledOp> CompileResult<T> rejectUnsupportedInputs(
            T compiled, List<OpInput> inputs) {
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Op op && !supportsChild(op.operator())) {
                return new CompileResult.Failure<>(List.of(new CompileDiagnostic(
                        MOMENTUM_UNSUPPORTED,
                        "Magic fields cannot use the momentum operator")));
            }
        }
        return new CompileResult.Success<>(compiled);
    }
}
