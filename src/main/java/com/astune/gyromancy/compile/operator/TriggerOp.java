package com.astune.gyromancy.compile.operator;

public abstract class TriggerOp extends OnEntityTickOp {
    @Override
    public final void onEntityTick(EntityTickContext ctx) {
        if (shouldTrigger(ctx)) trigger(ctx);
    }

    protected abstract boolean shouldTrigger(EntityTickContext ctx);

    protected abstract void trigger(EntityTickContext ctx);
}
