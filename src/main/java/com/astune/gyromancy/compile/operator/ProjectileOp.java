package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.ArrayEffectDefinition;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.CompiledArrayNode;
import com.astune.gyromancy.array.compile.DurationSpec;
import com.astune.gyromancy.array.compile.EffectAttributes;
import com.astune.gyromancy.array.compile.EffectKind;
import com.astune.gyromancy.array.compile.EffectNode;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.OpInputs;
import com.astune.gyromancy.array.compile.ShapeSpec;
import com.astune.gyromancy.array.compile.TriggerSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public abstract class ProjectileOp implements ArrayEffectDefinition {

    private final ElementType element;

    public ProjectileOp(ElementType element) {
        this.element = element;
    }


    @Override
    public CompileResult<CompiledArrayNode> compile(PositionedGlyph boundary, List<OpInput> inputs) {
        CompileResult<EffectAttributes> attributes = OpInputs.projectileAttributes(inputs, element);
        if (attributes instanceof CompileResult.Failure<EffectAttributes> failure) {
            return new CompileResult.Failure<>(failure.diagnostics());
        }
        EffectAttributes attrs = ((CompileResult.Success<EffectAttributes>) attributes).value();
        return new CompileResult.Success<>(new EffectNode(
                EffectKind.PROJECTILE,
                element,
                new ShapeSpec(boundary, scaleFor(boundary)),
                TriggerSpec.ON_ACTIVATE,
                DurationSpec.INSTANT,
                attrs,
                inputs,
                opChildren(inputs)));
    }

    private static List<CompiledArrayNode> opChildren(List<OpInput> inputs) {
        List<CompiledArrayNode> children = new ArrayList<>();
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Op op) children.add(op.node());
        }
        return List.copyOf(children);
    }

    private static float scaleFor(PositionedGlyph circle) {
        double area = Math.max(0.0, circle.length() * circle.width());
        return (float)Math.max(0.1F, Math.sqrt(area) * 0.5);
    }
}
