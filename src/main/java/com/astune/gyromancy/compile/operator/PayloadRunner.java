package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.effect.MagicEffect;

import java.util.List;

/**
 * The single payload execution loop. Entity-backed hosts call this every tick;
 * effect hosts that run once call it exactly once.
 */
public final class PayloadRunner {
    private PayloadRunner() {}

    public static void run(List<? extends EntityPayload> payload, EntityTickContext ctx) {
        MagicEffect owner = ctx.owner();
        for (EntityPayload op : payload) {
            if (ctx.isClientSide() && !op.ticksOnClient()) continue;
            op.onEntityTick(ctx);
            if (owner == null || !owner.isAlive()) return;
        }
    }
}
