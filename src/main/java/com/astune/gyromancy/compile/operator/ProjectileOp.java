package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.EffectAttributes;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputs;
import com.astune.gyromancy.array.runtime.RuntimeHandle;
import com.astune.gyromancy.symbol.SymbolCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class ProjectileOp implements Operator {

    private final ElementType element;
    private final ResourceLocation id;
    private final PositionedGlyph boundary;
    private final List<OpInput> matchedInputs;
    private final List<OpInput> inputs;
    private final EffectAttributes attributes;

    protected ProjectileOp(ResourceLocation id, ElementType element, PositionedGlyph boundary,
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

    public PositionedGlyph boundary() {
        return boundary;
    }

    public ElementType primaryElement() {
        return element;
    }

    public EffectAttributes attributes() {
        return attributes;
    }

    public List<Operator> childOps() {
        List<Operator> children = new ArrayList<>();
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Op op) children.add(op.operator());
        }
        return List.copyOf(children);
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
    public RuntimeHandle activate(ServerLevel level) {
        return new RuntimeHandle(Map.of());
    }

    @Override
    public void deactivate(ServerLevel level, Map<String, Object> scratchData) {
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
