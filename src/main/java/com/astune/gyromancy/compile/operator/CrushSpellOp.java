package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.effect.InstantEffect;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.element.ManaElements;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileDiagnostic;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.RegisteredOp;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.array.runtime.RuntimeHandle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * One-shot crush spell bound to earth and revert. It resolves the array radius
 * once, positions the sphere along the array normal from authored arrows, then
 * runs a single crush payload and detaches from the array.
 */
@RegisteredOp
public final class CrushSpellOp extends EntityEffectOp {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "crush");
    private static final String FIRED_KEY_PREFIX = "__instant_fired/";
    private static final double DIRECTION_EPSILON = 1.0E-8;

    public static final OpDefinition DEFINITION = new OpDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("earth"), OpInputMatcher.rune("revert"));
        }

        @Override
        public List<OpInputMatcher> accepted() {
            return List.of(
                    OpInputMatcher.rune("arrow"),
                    OpInputMatcher.rune("arrow_up"),
                    // Payload contributions stay on the regular protocol.
                    OpInputMatcher.op(CompiledOp.class));
        }

        @Override
        public CompileResult<CompiledOp> compile(PositionedGlyph boundary,
                                                 List<OpInput> matchedInputs,
                                                 List<OpInput> inputs) {
            return CrushSpellOp.create(boundary, matchedInputs, inputs);
        }
    };

    private final PositionedGlyph boundary;
    private final List<OpInput> matchedInputs;
    private final List<OpInput> inputs;

    private CrushSpellOp(PositionedGlyph boundary, List<OpInput> matchedInputs,
                         List<OpInput> inputs) {
        super(ID, ElementType.EARTH, boundary, matchedInputs, inputs);
        this.boundary = boundary;
        this.matchedInputs = List.copyOf(matchedInputs);
        this.inputs = List.copyOf(inputs);
    }

    public static CompileResult<CompiledOp> create(PositionedGlyph boundary,
                                                   List<OpInput> matchedInputs,
                                                   List<OpInput> inputs) {
        if (primaryRune(matchedInputs) == null) {
            return new CompileResult.Failure<>(List.of(new CompileDiagnostic(
                    "missing_primary_element", "Crush requires an earth rune")));
        }
        if (!hasRune(matchedInputs, "revert")) {
            return new CompileResult.Failure<>(List.of(new CompileDiagnostic(
                    "missing_revert", "Crush requires a revert rune")));
        }
        return new CompileResult.Success<>(new CrushSpellOp(boundary, matchedInputs, inputs));
    }

    @Override
    public EntityEffectOp copyWithInputs(List<OpInput> inputs) {
        return new CrushSpellOp(boundary, matchedInputs, inputs);
    }

    /** Crush is a one-shot effect: activation is its only execution. */
    @Override
    public ManaElements getCost() {
        return elementCost(20.0);
    }

    @Override
    public RuntimeHandle activate(OpRuntimeContext context) {
        String firedKey = FIRED_KEY_PREFIX + boundary.glyphUuid();
        ArrayObject array = context.array();
        if (array != null && Boolean.TRUE.equals(array.scratchData().get(firedKey))) {
            return new RuntimeHandle(Map.of());
        }

        float radius = scale(context);
        Vec3 normal = context.normalFor(boundary);
        double arrowResidual = upwardComponent(context, normal);
        double offset = CrushLogic.centerOffset(arrowResidual, radius);
        Vec3 origin = context.positionFor(boundary);
        Vec3 center = origin.add(normal.scale(offset));

        PositionedGlyph liveBoundary = context.liveGlyph(boundary);
        Gyromancy.LOGGER.debug(
                "[CrushDebug] activation glyph={} size={}x{} radius={} origin={} normal={} arrowResidual={} offset={} center={}",
                boundary.glyphUuid(), liveBoundary.length(), liveBoundary.width(), radius,
                origin, normal, arrowResidual, offset, center);

        List<EntityPayload> payloads = payload(List.of(new CrushOp(radius, center)), context);
        new InstantEffect(context.level(), center, radius, normal, payloads).executeOnce();
        return new RuntimeHandle(Map.of(firedKey, true));
    }

    @Override
    public void deactivate(OpRuntimeContext context, Map<String, Object> scratchData) {
        // The effect already detached and finished; nothing to tear down.
    }

    /**
     * Momentum residual of the authored arrows. {@code arrow} contributes its
     * in-plane front and {@code arrow_up} contributes along the array normal.
     */
    private double upwardComponent(OpRuntimeContext context, Vec3 normal) {
        Vec3 unitNormal = normal.lengthSqr() < DIRECTION_EPSILON
                ? Vec3.ZERO : normal.normalize();
        double sizeSum = 0.0;
        Vec3 sum = Vec3.ZERO;
        for (OpInput input : inputs) {
            if (!(input instanceof OpInput.Rune rune)) continue;
            String name = rune.symbolName();
            if (!"arrow".equals(name) && !"arrow_up".equals(name)) continue;
            PositionedGlyph glyph = context.liveGlyph(rune.glyph());
            sizeSum += glyph.length();
            if ("arrow_up".equals(name)) {
                sum = sum.add(unitNormal.scale(glyph.length()));
                continue;
            }
            Vec3 front = glyph.front();
            if (front != null && front.lengthSqr() >= DIRECTION_EPSILON) {
                sum = sum.add(front.normalize().scale(glyph.length()));
            }
        }
        return CrushLogic.upwardComponent(sizeSum, sum);
    }

    private static PositionedGlyph primaryRune(List<OpInput> inputs) {
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Rune rune && "earth".equals(rune.symbolName())) {
                return rune.glyph();
            }
        }
        return null;
    }

    private static boolean hasRune(List<OpInput> inputs, String name) {
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Rune rune && name.equals(rune.symbolName())) return true;
        }
        return false;
    }
}
