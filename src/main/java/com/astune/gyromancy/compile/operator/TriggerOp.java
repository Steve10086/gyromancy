package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public abstract class TriggerOp extends OnEntityTickOp {
    private static final Map<MinecraftServer, Deque<PendingTrigger>> PENDING_TRIGGERS = new HashMap<>();

    @Override
    public final void onEntityTick(EntityTickContext ctx) {
        if (shouldTrigger(ctx)) deferTrigger(ctx);
    }

    protected abstract boolean shouldTrigger(EntityTickContext ctx);

    protected abstract void trigger(EntityTickContext ctx);

    /**
     * Releases triggers collected during the preceding server tick before entities tick again.
     * The queue is drained before execution so triggers created while releasing are deferred
     * to the following tick as well.
     */
    public static void onServerTick(ServerTickEvent.Pre event) {
        Deque<PendingTrigger> pending;
        synchronized (PENDING_TRIGGERS) {
            pending = PENDING_TRIGGERS.remove(event.getServer());
        }
        if (pending == null) return;

        PendingTrigger next;
        while ((next = pending.pollFirst()) != null) {
            try {
                next.op().trigger(next.context());
            } catch (RuntimeException exception) {
                Gyromancy.LOGGER.error("[TriggerOp] Failed to release deferred trigger", exception);
            }
        }
    }

    private void deferTrigger(EntityTickContext ctx) {
        if (!(ctx.level() instanceof ServerLevel level)) return;

        synchronized (PENDING_TRIGGERS) {
            PENDING_TRIGGERS.computeIfAbsent(level.getServer(), ignored -> new ArrayDeque<>())
                    .addLast(new PendingTrigger(this, ctx));
        }
    }

    private record PendingTrigger(TriggerOp op, EntityTickContext context) {}
}
