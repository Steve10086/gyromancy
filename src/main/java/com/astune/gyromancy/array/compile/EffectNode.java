package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.element.ElementType;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class EffectNode extends ProjectileRuntimeNode {
    private final ResourceLocation activationId;
    private final EffectKind kind;
    private final ElementType primaryElement;
    private final ShapeSpec shape;
    private final TriggerSpec trigger;
    private final DurationSpec duration;
    private final EffectAttributes attributes;
    private final List<OpInput> matchedInputs;
    private final List<OpInput> inputs;
    private final List<CompiledArrayNode> children;

    public EffectNode(ResourceLocation activationId,
                      EffectKind kind,
                      ElementType primaryElement,
                      ShapeSpec shape,
                      TriggerSpec trigger,
                      DurationSpec duration,
                      EffectAttributes attributes,
                      List<OpInput> matchedInputs,
                      List<OpInput> inputs,
                      List<CompiledArrayNode> children) {
        this.activationId = activationId;
        this.kind = kind;
        this.primaryElement = primaryElement;
        this.shape = shape;
        this.trigger = trigger;
        this.duration = duration;
        this.attributes = attributes;
        this.matchedInputs = List.copyOf(matchedInputs);
        this.inputs = List.copyOf(inputs);
        this.children = List.copyOf(children);
    }

    public ResourceLocation activationId() { return activationId; }

    public EffectKind kind() { return kind; }

    public ElementType primaryElement() { return primaryElement; }

    public ShapeSpec shape() { return shape; }

    public TriggerSpec trigger() { return trigger; }

    public DurationSpec duration() { return duration; }

    public EffectAttributes attributes() { return attributes; }

    public List<OpInput> matchedInputs() { return matchedInputs; }

    public List<OpInput> inputs() { return inputs; }

    public List<CompiledArrayNode> children() { return children; }
}
