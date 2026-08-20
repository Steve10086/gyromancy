package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.array.runtime.RuntimeHandle;
import com.astune.gyromancy.symbol.SymbolCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public abstract class EntityEffectOp implements CompiledOp, PersistentOp {
    protected static final int ELEMENT_EXCHANGE_INTERVAL = 10;
    private final ElementType element;
    private final ResourceLocation id;
    private final PositionedGlyph boundary;
    private final List<OpInput> matchedInputs;
    protected final List<OpInput> inputs;

    protected EntityEffectOp(ResourceLocation id, ElementType element, PositionedGlyph boundary,
                             List<OpInput> matchedInputs, List<OpInput> inputs) {
        this.id = id;
        this.element = element;
        this.boundary = boundary;
        this.matchedInputs = List.copyOf(matchedInputs);
        this.inputs = List.copyOf(inputs);
    }

    public PositionedGlyph boundary() {
        return boundary;
    }

    public ElementType primaryElement() {
        return element;
    }

    public RuntimeHandle activateAt(OpRuntimeContext context, Vec3 origin) {
        return ((PersistentOp) this).activate(context.at(origin, new Vec3(0, 1, 0))); // worldY
    }

    protected List<EmitOp.Emission> emissions() {
        return emissions(OpRuntimeContext.empty());
    }

    protected List<EmitOp.Emission> emissions(OpRuntimeContext context) {
        List<EmitOp.Emission> emissions = new ArrayList<>();
        boolean hasEmitOp = false;
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Op op && op.operator() instanceof EmitOp emitOp) {
                hasEmitOp = true;
                emissions.addAll(emitOp.emissions(context));
            }
        }
        List<EmitOp.Emission> resolved = hasEmitOp
                ? List.copyOf(emissions) : List.of(defaultEmission(context));
        for (OpInput input : inputs) {
            if (!(input instanceof OpInput.Op op)) continue;
            resolved = resolved.stream()
                    .map(emission -> op.operator().modifyEntityEmission(emission, context))
                    .toList();
        }
        return resolved;
    }

    protected List<EntityPayload> payload(List<? extends EntityPayload> defaults) {
        return payload(defaults, OpRuntimeContext.empty());
    }

    protected List<EntityPayload> payload(List<? extends EntityPayload> defaults,
                                          OpRuntimeContext context) {
        List<EntityPayload> payload = new ArrayList<>(defaults);
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Op op) {
                op.operator().contributeEntityPayloads(payload, context);
            }
        }
        return List.copyOf(payload);
    }

    private EmitOp.Emission defaultEmission(OpRuntimeContext context) {
        Vec3 velocity = Vec3.ZERO;
        double motionSum = 0.0;
        boolean hasMotion = false;
        for (OpInput input : inputs) {
            if (!(input instanceof OpInput.Rune rune) || !"arrow".equals(rune.symbolName())) continue;
            PositionedGlyph glyph = context == null ? rune.glyph() : context.liveGlyph(rune.glyph());
            double speed = glyph.length();
            motionSum += speed;
            hasMotion = true;
            Vec3 direction = glyph.front();
            if (direction.lengthSqr() >= 1e-8) {
                velocity = velocity.add(direction.normalize().scale(speed));
            }
        }
        double speed = velocity.length();
        Vec3 normal = context == null ? boundary.surface().normal() : context.normalFor(boundary);
        velocity = velocity.add(normal.scale(((motionSum - speed) + 0.2 * speed)));

        return new EmitOp.Emission(velocity, motionSum, 1.0F, hasMotion);
    }

    public float scale() {
        return scaleFor(boundary);
    }

    public float scale(OpRuntimeContext context) {
        return scaleFor(context == null ? boundary : context.liveGlyph(boundary));
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
