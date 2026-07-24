package com.astune.gyromancy.compile.operator;

public abstract class OnEntityTickOp extends EntityPayload {
    @Override
    public abstract void onEntityTick(EntityTickContext ctx);
}
