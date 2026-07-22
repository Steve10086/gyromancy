package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.element.ElementType;

import java.util.List;

public final class EffectNode extends ProjectileRuntimeNode {
    private final EffectKind kind;
    private final ElementType primaryElement;
    private final ShapeSpec shape;
    private final TriggerSpec trigger;
    private final DurationSpec duration;
    private final EffectAttributes attributes;
    private final List<OpInput> inputs;
    private final List<CompiledArrayNode> children;

    public EffectNode(EffectKind kind,
                      ElementType primaryElement,
                      ShapeSpec shape,
                      TriggerSpec trigger,
                      DurationSpec duration,
                      EffectAttributes attributes,
                      List<OpInput> inputs,
                      List<CompiledArrayNode> children) {
        this.kind = kind;
        this.primaryElement = primaryElement;
        this.shape = shape;
        this.trigger = trigger;
        this.duration = duration;
        this.attributes = attributes;
        this.inputs = List.copyOf(inputs);
        this.children = List.copyOf(children);
    }

    public EffectKind kind() { return kind; }

    public ElementType primaryElement() { return primaryElement; }

    public ShapeSpec shape() { return shape; }

    public TriggerSpec trigger() { return trigger; }

    public DurationSpec duration() { return duration; }

    public EffectAttributes attributes() { return attributes; }

    public List<OpInput> inputs() { return inputs; }

    public List<CompiledArrayNode> children() { return children; }
}
