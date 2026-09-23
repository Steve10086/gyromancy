package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.field.CircularFieldShape;
import com.astune.gyromancy.api.field.MagicFieldShape;
import com.astune.gyromancy.api.field.RectangularFieldShape;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.ArrayNode;
import com.astune.gyromancy.array.compile.CompileDiagnostic;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.GroupNode;
import com.astune.gyromancy.array.compile.LocalCompileContext;
import com.astune.gyromancy.array.compile.LocalCompileResult;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.RegisteredOp;
import com.astune.gyromancy.array.compile.SequenceNode;
import com.astune.gyromancy.array.compile.SymbolNode;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.array.runtime.OpRuntimeFailure;
import com.astune.gyromancy.symbol.SecretText;
import com.astune.gyromancy.symbol.SecretTextSymbol;
import com.astune.gyromancy.symbol.SymbolCatalog;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Selects and initialises the immutable shape used by a magic field.
 *
 * <p>The {@code fix + split} form creates a rectangular shape. An empty
 * outer-circle child selects the stretched-sphere shape. Any other raw groups
 * are parsed only at runtime: each group layer supplies one dimension, and can
 * contain at most one nested group for the following dimension.</p>
 */
@RegisteredOp
public final class ShapeOp implements CompiledOp, LocalCompilable {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "field_shape");
    private static final int RECTANGULAR_DIMENSIONS = 3;
    private static final float SIZE_UNIT = 0.25F;
    private static final int MAX_QUARTER_BLOCKS =
            Math.round(RectangularFieldShape.MAX_SIZE / SIZE_UNIT);

    public static final OpDefinition DEFINITION = new OpDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("fix"));
        }

        @Override
        public List<OpInputMatcher> accepted() {
            return List.of(OpInputMatcher.rune("split"), OpInputMatcher.rawGroup());
        }

        @Override
        public CompileResult<CompiledOp> compile(PositionedGlyph boundary,
                                                  List<OpInput> matchedInputs,
                                                  List<OpInput> inputs) {
            return ShapeOp.create(boundary, matchedInputs, inputs);
        }
    };

    private enum Kind {
        RECTANGULAR,
        CIRCULAR
    }

    private final PositionedGlyph boundary;
    private final List<OpInput> matchedInputs;
    private final List<OpInput> inputs;
    private final Kind kind;
    private final Optional<MagicFieldShape> materializedShape;

    private ShapeOp(PositionedGlyph boundary, List<OpInput> matchedInputs,
                     List<OpInput> inputs, Kind kind) {
        this(boundary, matchedInputs, inputs, kind, Optional.empty());
    }

    private ShapeOp(PositionedGlyph boundary, List<OpInput> matchedInputs,
                    List<OpInput> inputs, Kind kind,
                    Optional<MagicFieldShape> materializedShape) {
        this.boundary = boundary;
        this.matchedInputs = List.copyOf(matchedInputs);
        this.inputs = List.copyOf(inputs);
        this.kind = kind;
        this.materializedShape = materializedShape == null
                ? Optional.empty() : materializedShape;
    }

    public static CompileResult<CompiledOp> create(PositionedGlyph boundary,
                                                    List<OpInput> matchedInputs,
                                                    List<OpInput> inputs) {
        int splitCount = 0;
        List<OpInput.RawGroup> rawGroups = new ArrayList<>();
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Rune rune && "split".equals(rune.symbolName())) {
                splitCount++;
            } else if (input instanceof OpInput.RawGroup raw) {
                rawGroups.add(raw);
            } else if (input instanceof OpInput.Op op && op.sourceGroup() != null) {
                rawGroups.add(new OpInput.RawGroup(op.sourceGroup(), List.of()));
            } else if (!(input instanceof OpInput.Rune rune && "fix".equals(rune.symbolName()))) {
                return invalidConfiguration();
            }
        }

        long emptyOuterCircles = rawGroups.stream()
                .filter(ShapeOp::isEmptyOuterCircle)
                .count();
        if (splitCount == 1 && emptyOuterCircles == 0) {
            return new CompileResult.Success<>(new ShapeOp(boundary, matchedInputs, inputs,
                    Kind.RECTANGULAR));
        }
        if (splitCount == 0) {
            // An empty outer-circle child is the circular-shape selector. Any
            // additional raw groups remain available as its size layers.
            if (emptyOuterCircles == 1) {
                return new CompileResult.Success<>(new ShapeOp(boundary, matchedInputs, inputs,
                        Kind.CIRCULAR));
            }
        }
        return invalidConfiguration();
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

    public Optional<MagicFieldShape> materializedShape() {
        return materializedShape;
    }

    @Override
    public LocalCompileResult localCompile(LocalCompileContext context) {
        try {
            return LocalCompileResult.success(new ShapeOp(
                    boundary, matchedInputs, inputs, kind, Optional.of(resolveStaticShape())));
        } catch (IllegalArgumentException exception) {
            return LocalCompileResult.failure("invalid_field_shape", exception.getMessage());
        }
    }

    @Override
    public int color() {
        return SymbolCatalog.glyphColorFor(ResourceLocation.fromNamespaceAndPath(
                Gyromancy.MODID, "fix"));
    }

    /**
     * Resolves this op into its immutable shape. Malformed parameter groups
     * stop the owning array and also return an empty optional.
     */
    public Optional<MagicFieldShape> resolveShape(OpRuntimeContext context) {
        if (materializedShape.isPresent()) return materializedShape;
        try {
            List<Float> dimensions = new ArrayList<>();
            boolean circularSelectorFound = kind != Kind.CIRCULAR;
            for (OpInput input : inputs) {
                GroupNode parameterGroup = parameterGroup(input, context);
                if (parameterGroup == null) continue;
                // The empty outer circle is only the circular selector; it
                // carries no dimension bits of its own.
                if (kind == Kind.CIRCULAR && isEmptyOuterCircle(parameterGroup)) {
                    circularSelectorFound = true;
                    continue;
                }
                decodeLayers(parameterGroup, dimensions);
            }
            if (kind == Kind.CIRCULAR && !circularSelectorFound) {
                throw new ShapeParameterException(
                        "A circular field shape requires an empty outer-circle selector");
            }
            if (dimensions.size() > RECTANGULAR_DIMENSIONS) {
                throw new ShapeParameterException("Rectangular fields allow at most "
                        + RECTANGULAR_DIMENSIONS + " nested size layers");
            }
            while (dimensions.size() < RECTANGULAR_DIMENSIONS) {
                dimensions.add(RectangularFieldShape.DEFAULT_SIZE);
            }
            if (kind == Kind.CIRCULAR) {
                return Optional.of(new CircularFieldShape(
                        dimensions.get(0), dimensions.get(1), dimensions.get(2)));
            }
            return Optional.of(new RectangularFieldShape(
                    dimensions.get(0), dimensions.get(1), dimensions.get(2)));
        } catch (IllegalArgumentException exception) {
            OpRuntimeFailure.terminate(context, this, OpRuntimeFailure.Kind.RUNTIME_ERROR,
                    exception.getMessage());
            return Optional.empty();
        }
    }

    private MagicFieldShape resolveStaticShape() {
        List<Float> dimensions = new ArrayList<>();
        boolean circularSelectorFound = kind != Kind.CIRCULAR;
        for (OpInput input : inputs) {
            GroupNode parameterGroup = staticParameterGroup(input);
            if (parameterGroup == null) continue;
            if (kind == Kind.CIRCULAR && isEmptyOuterCircle(parameterGroup)) {
                circularSelectorFound = true;
                continue;
            }
            decodeLayers(parameterGroup, dimensions);
        }
        if (kind == Kind.CIRCULAR && !circularSelectorFound) {
            throw new ShapeParameterException(
                    "A circular field shape requires an empty outer-circle selector");
        }
        if (dimensions.size() > RECTANGULAR_DIMENSIONS) {
            throw new ShapeParameterException("Rectangular fields allow at most "
                    + RECTANGULAR_DIMENSIONS + " nested size layers");
        }
        while (dimensions.size() < RECTANGULAR_DIMENSIONS) {
            dimensions.add(RectangularFieldShape.DEFAULT_SIZE);
        }
        if (kind == Kind.CIRCULAR) {
            return new CircularFieldShape(dimensions.get(0), dimensions.get(1), dimensions.get(2));
        }
        return new RectangularFieldShape(dimensions.get(0), dimensions.get(1), dimensions.get(2));
    }

    private static GroupNode staticParameterGroup(OpInput input) {
        if (input instanceof OpInput.RawGroup raw) return raw.group();
        if (input instanceof OpInput.Op op) {
            if (op.sourceGroup() == null) {
                throw new ShapeParameterException(
                        "A field shape child has no statically available source group");
            }
            return op.sourceGroup();
        }
        return null;
    }

    /**
     * Retains ordinary raw groups, while deferred inputs follow the same
     * runtime forwarding path as every other dynamic parent parameter. This
     * keeps ShapeOp independent of concrete resolvable operator types.
     */
    private GroupNode parameterGroup(OpInput input, OpRuntimeContext context) {
        if (input instanceof OpInput.RawGroup raw) return raw.group();
        if (input instanceof OpInput.Op op && op.sourceGroup() != null) {
            return op.sourceGroup();
        }
        if (!(input instanceof OpInput.Op)) return null;
        throw new ShapeParameterException(
                "A field shape child has no statically available source group");
    }

    private static void decodeLayers(GroupNode group, List<Float> dimensions) {
        if (dimensions.size() >= RECTANGULAR_DIMENSIONS) {
            throw new ShapeParameterException("Rectangular fields allow at most "
                    + RECTANGULAR_DIMENSIONS + " nested size layers");
        }
        if (!(group.body() instanceof SequenceNode sequence)) {
            throw new ShapeParameterException("A field size layer must contain a rune sequence");
        }

        List<SecretText> bits = new ArrayList<>();
        GroupNode nested = null;
        for (ArrayNode child : sequence.children()) {
            if (child instanceof SymbolNode symbol) {
                SecretTextSymbol secret = SecretTextSymbol.fromId(symbol.glyph().symbolId());
                if (secret == null) {
                    throw new ShapeParameterException("Field size layers may contain only secret-text runes");
                }
                bits.add(secret.type());
            } else if (child instanceof GroupNode nestedGroup) {
                if (nested != null) {
                    throw new ShapeParameterException("A field size layer may contain only one nested layer");
                }
                nested = nestedGroup;
            } else {
                throw new ShapeParameterException("Field size layers may contain only secret-text runes");
            }
        }
        if (bits.isEmpty()) {
            throw new ShapeParameterException("Each field size layer requires at least one secret-text rune");
        }

        dimensions.add(decodeBinary(bits));
        if (nested != null) decodeLayers(nested, dimensions);
    }

    /**
     * Every secret-text enum entry owns one binary position: {@code SECRET_1}
     * is the least-significant quarter-block bit, {@code SECRET_2} the next,
     * and so on. Presence sets that bit; absence leaves it clear. This makes
     * {@code SECRET_1..SECRET_4} the ordinary {@code 1111b} value.
     */
    static float decodeBinary(List<SecretText> bits) {
        if (bits.isEmpty()) throw new ShapeParameterException("A field size must contain a binary digit");
        EnumSet<SecretText> present = EnumSet.noneOf(SecretText.class);
        int quarterBlocks = 0;
        for (SecretText bit : bits) {
            if (bit == null) throw new ShapeParameterException("A field size contains an invalid secret-text rune");
            if (!present.add(bit)) continue;
            quarterBlocks |= 1 << bit.ordinal();
            if (quarterBlocks >= MAX_QUARTER_BLOCKS) return RectangularFieldShape.MAX_SIZE;
        }
        return quarterBlocks * SIZE_UNIT;
    }

    private static boolean isEmptyOuterCircle(OpInput.RawGroup raw) {
        return isEmptyOuterCircle(raw.group());
    }

    private static boolean isEmptyOuterCircle(GroupNode group) {
        return isOuterCircle(group)
                && group.body() instanceof SequenceNode sequence
                && sequence.children().isEmpty();
    }

    private static boolean isOuterCircle(OpInput.RawGroup raw) {
        return isOuterCircle(raw.group());
    }

    private static boolean isOuterCircle(GroupNode group) {
        return group != null
                && group.boundary() != null
                && group.boundary().role() == SymbolRole.OUTER_CIRCLE;
    }

    private static CompileResult<CompiledOp> invalidConfiguration() {
        return new CompileResult.Failure<>(List.of(new CompileDiagnostic(
                "invalid_field_shape",
                "Field shape requires one split or one empty outer-circle selector")));
    }

    private static final class ShapeParameterException extends IllegalArgumentException {
        private ShapeParameterException(String message) {
            super(message);
        }
    }
}
