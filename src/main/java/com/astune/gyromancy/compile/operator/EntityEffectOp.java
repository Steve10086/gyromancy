package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.EffectAttributes;
import com.astune.gyromancy.array.compile.MotionAttribute;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.OpInputs;
import com.astune.gyromancy.symbol.SymbolCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public abstract class EntityEffectOp implements CompiledOp, PersistentOp {

    private final ElementType element;
    private final ResourceLocation id;
    private final PositionedGlyph boundary;
    private final List<OpInput> matchedInputs;
    private final List<OpInput> inputs;
    private final EffectAttributes attributes;

    protected EntityEffectOp(ResourceLocation id, ElementType element, PositionedGlyph boundary,
                             List<OpInput> matchedInputs, List<OpInput> inputs,
                             EffectAttributes attributes) {
        this.id = id;
        this.element = element;
        this.boundary = boundary;
        this.matchedInputs = List.copyOf(matchedInputs);
        this.inputs = List.copyOf(inputs);
        this.attributes = attributes;
    }

    protected static CompileResult<EffectAttributes> compileAttributes(List<OpInput> inputs, ElementType element) {
        CompileResult<EffectAttributes> attributes = OpInputs.projectileAttributes(inputs, element);
        if (attributes instanceof CompileResult.Failure<EffectAttributes> failure) {
            return new CompileResult.Failure<>(failure.diagnostics());
        }
        return attributes;
    }

    protected static List<OpInputMatcher> acceptedProjectileInputs() {
        return List.of(
                OpInputMatcher.rune("arrow"),
                OpInputMatcher.rune("revert"),
                OpInputMatcher.op(CompiledOp.class));
    }

    public PositionedGlyph boundary() {
        return boundary;
    }

    public ElementType primaryElement() {
        return element;
    }

    public EffectAttributes attributes() {
        return attributes;
    }

    protected List<EmitOp.Emission> emissions() {
        List<EmitOp.Emission> emissions = new ArrayList<>();
        boolean hasEmitOp = false;
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Op op && op.operator() instanceof EmitOp emitOp) {
                hasEmitOp = true;
                emissions.addAll(emitOp.emissions());
            }
        }
        return hasEmitOp ? List.copyOf(emissions) : List.of(defaultEmission());
    }

    protected List<EntityPayload> payload(List<? extends EntityPayload> defaults) {
        List<EntityPayload> payload = new ArrayList<>(defaults);
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Op op) {
                op.operator().contributeEntityPayloads(payload);
            }
        }
        return List.copyOf(payload);
    }

    private EmitOp.Emission defaultEmission() {
        Vec3 velocity = Vec3.ZERO;
        double motionSum = 0.0;
        List<MotionAttribute> motions = attributes.motion();
        for (MotionAttribute motion : motions) {
            motionSum += motion.speed();
            if (motion.direction().lengthSqr() >= 1e-8) {
                velocity = velocity.add(motion.direction().normalize().scale(motion.speed()));
            }
        }
        return new EmitOp.Emission(velocity, motionSum, 1.0F, !motions.isEmpty());
    }

    public float scale() {
        return scaleFor(boundary);
    }

    @Override
    public ResourceLocation id() {
        return id;
    }

    @Override
    public List<OpInput> inputs() {
        return inputs;
    }

    public List<OpInput> matchedInputs() {
        return matchedInputs;
    }

    @Override
    public int color() {
        return SymbolCatalog.glyphColorFor(ResourceLocation.fromNamespaceAndPath("gyromancy",
                ArrayNodeCompilerCompat.symbolName(element)));
    }

    private static float scaleFor(PositionedGlyph circle) {
        double area = Math.max(0.0, circle.length() * circle.width());
        return (float)Math.max(0.1F, Math.sqrt(area) * 0.5);
    }

    private static final class ArrayNodeCompilerCompat {
        private static String symbolName(ElementType element) {
            return switch (element) {
                case FIRE -> "fire";
                case WATER -> "water";
                case MANA -> "mana";
                case WIND -> "wind";
                case EARTH -> "earth";
                case LIGHT -> "light";
                case DARK -> "dark";
                case SPACE -> "space";
                case TIME -> "time";
            };
        }
    }
}
