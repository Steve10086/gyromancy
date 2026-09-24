package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.field.FieldDirection;
import com.astune.gyromancy.api.field.MagicFieldShape;
import com.astune.gyromancy.api.field.RectangularFieldShape;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileDiagnostic;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.RegisteredOp;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.entity.field.MagicFieldEntity;
import com.astune.gyromancy.entity.field.WindFieldEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

/** Creates a stationary wind field whose direction is authored by arrows. */
@RegisteredOp
public final class WindFieldOp extends FieldOp {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            Gyromancy.MODID, "wind_field");
    private static final double DIRECTION_EPSILON = 1.0E-8;

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
            return List.of(
                    OpInputMatcher.rune("arrow"),
                    OpInputMatcher.rune("revert"),
                    OpInputMatcher.op(ShapeOp.class),
                    // Field operators forward every compiled payload except
                    // MomentumOp, which has no stationary-field meaning.
                    OpInputMatcher.op(CompiledOp.class));
        }

        @Override
        public CompileResult<CompiledOp> compile(PositionedGlyph boundary,
                                                   List<OpInput> matchedInputs,
                                                   List<OpInput> inputs) {
            return WindFieldOp.create(boundary, matchedInputs, inputs);
        }
    };

    private final PositionedGlyph boundary;
    private final List<OpInput> matchedInputs;
    private final List<OpInput> inputs;

    private WindFieldOp(PositionedGlyph boundary, List<OpInput> matchedInputs,
                        List<OpInput> inputs) {
        super(ID, ElementType.WIND, boundary, matchedInputs, inputs);
        this.boundary = boundary;
        this.matchedInputs = List.copyOf(matchedInputs);
        this.inputs = List.copyOf(inputs);
    }

    @Override
    public FieldOp copyWithInputs(List<OpInput> inputs) {
        return new WindFieldOp(boundary, matchedInputs, inputs);
    }

    public static CompileResult<CompiledOp> create(PositionedGlyph boundary,
                                                    List<OpInput> matchedInputs,
                                                    List<OpInput> inputs) {
        if (primaryRune(matchedInputs) == null) {
            return new CompileResult.Failure<>(List.of(new CompileDiagnostic(
                    "missing_primary_element", "Wind fields require a wind rune")));
        }
        if (arrowCount(inputs) == 0) {
            return new CompileResult.Failure<>(List.of(new CompileDiagnostic(
                    "missing_wind_direction", "Wind fields require at least one arrow rune")));
        }
        try {
            // Validate the authored, compile-time direction now. Runtime
            // activation repeats the calculation against live glyphs.
            directionFor(boundary, inputs, null);
        } catch (IllegalArgumentException exception) {
            return new CompileResult.Failure<>(List.of(new CompileDiagnostic(
                    "invalid_wind_direction", exception.getMessage())));
        }
        return rejectUnsupportedInputs(new WindFieldOp(boundary, matchedInputs, inputs), inputs);
    }

    @Override
    protected MagicFieldEntity create(Level level, Vec3 position) {
        return new WindFieldEntity(level, position, new RectangularFieldShape(),
                WindFieldEntity.DEFAULT_DIRECTION, WindFieldEntity.DEFAULT_ENERGY);
    }

    @Override
    protected MagicFieldEntity create(Level level, Vec3 position, MagicFieldShape shape) {
        return new WindFieldEntity(level, position, shape,
                WindFieldEntity.DEFAULT_DIRECTION, WindFieldEntity.DEFAULT_ENERGY);
    }

    @Override
    protected MagicFieldEntity create(Level level, Vec3 position, OpRuntimeContext context) {
        return new WindFieldEntity(level, position, new RectangularFieldShape(),
                directionFor(boundary, inputs, context), energyFor(context));
    }

    @Override
    protected MagicFieldEntity create(Level level, Vec3 position, MagicFieldShape shape,
                                      OpRuntimeContext context) {
        return new WindFieldEntity(level, position, shape,
                directionFor(boundary, inputs, context), energyFor(context));
    }

    /** The first wind implementation uses one unit of authored field energy. */
    protected double energyFor(OpRuntimeContext context) {
        return WindFieldEntity.DEFAULT_ENERGY;
    }

    @Override
    protected Collection<? extends EntityPayload> conditionalPayload() {
        return List.of();
    }

    @Override
    protected Collection<? extends EntityPayload> defaultPayload() {
        return List.of(new WindFieldPushOp());
    }

    public PositionedGlyph primaryRune() {
        return primaryRune(matchedInputs);
    }

    public List<OpInput> matchedInputs() {
        return matchedInputs;
    }

    /** Computes the immutable field direction from authored/live arrow runes. */
    public static FieldDirection directionFor(PositionedGlyph boundary,
                                               List<OpInput> inputs,
                                               OpRuntimeContext context) {
        Vec3 arrowVector = Vec3.ZERO;
        double arrowSizeSum = 0.0;
        for (OpInput input : inputs) {
            if (!(input instanceof OpInput.Rune rune)
                    || !"arrow".equals(rune.symbolName())) continue;
            PositionedGlyph glyph = context == null ? rune.glyph() : context.liveGlyph(rune.glyph());
            double size = glyph.length();
            if (!Double.isFinite(size) || size < 0.0) {
                throw new IllegalArgumentException("Wind arrow lengths must be finite and non-negative");
            }
            arrowSizeSum += size;
            Vec3 front = glyph.front();
            if (front != null && front.lengthSqr() >= DIRECTION_EPSILON) {
                arrowVector = arrowVector.add(front.normalize().scale(size));
            }
        }

        Vec3 normal = boundary == null ? new Vec3(0.0, 1.0, 0.0)
                : context == null ? boundary.surface().normal() : context.normalFor(boundary);
        double liftDirection = normal != null && normal.y < -0.999 ? -1.0 : 1.0;
        Vec3 direction = MomentumOp.withUpwardComponent(arrowVector, arrowSizeSum, liftDirection);
        if (hasRevert(inputs)) direction = direction.scale(-1.0);
        if (direction.lengthSqr() < DIRECTION_EPSILON) {
            throw new IllegalArgumentException("Wind arrows must produce a non-zero direction");
        }
        return new FieldDirection(direction, 0.0F);
    }

    private static int arrowCount(List<OpInput> inputs) {
        int count = 0;
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Rune rune && "arrow".equals(rune.symbolName())) count++;
        }
        return count;
    }

    private static boolean hasRevert(List<OpInput> inputs) {
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Rune rune && "revert".equals(rune.symbolName())) return true;
        }
        return false;
    }

    private static PositionedGlyph primaryRune(List<OpInput> inputs) {
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Rune rune && "wind".equals(rune.symbolName())) {
                return rune.glyph();
            }
        }
        return null;
    }
}
