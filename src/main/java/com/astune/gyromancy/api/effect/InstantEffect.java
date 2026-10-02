package com.astune.gyromancy.api.effect;

import com.astune.gyromancy.compile.operator.EntityPayload;
import com.astune.gyromancy.compile.operator.EntityTickContext;
import com.astune.gyromancy.compile.operator.PayloadRunner;
import com.astune.gyromancy.util.MagicBallGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A non-entity effect host with a fixed position, radius, and facing.
 *
 * <p>It executes its payload list through the regular payload protocol exactly
 * once, then self-destructs. Entity-tick payloads run a single time, trigger
 * payloads evaluate their condition once, and on-discard payloads run as the
 * host finishes.</p>
 */
public final class InstantEffect implements MagicEffect {
    private final Level level;
    private final Vec3 position;
    private final double radius;
    private final Vec3 facing;
    private final List<EntityPayload> payload;
    private final Map<String, Object> runtimeData = new HashMap<>();
    private boolean alive = true;
    private boolean executed;
    private List<BlockPos> insideCache;

    public InstantEffect(Level level, Vec3 position, double radius, Vec3 facing,
                         List<? extends EntityPayload> payload) {
        this.level = level;
        this.position = position;
        this.radius = radius;
        this.facing = facing == null ? Vec3.ZERO : facing;
        this.payload = List.copyOf(payload);
    }

    /** Runs the shared payload loop once, then finishes the host. */
    public void executeOnce() {
        if (executed) return;
        executed = true;
        EntityTickContext ctx = EntityTickContext.forInstant(
                this, runtimeData, position, bounds(), facing, (float) (radius * 2.0), 0.0);
        PayloadRunner.run(payload, ctx);
        finish();
    }

    /** Marks the effect dead and runs the payload removal hooks exactly once. */
    public void finish() {
        if (!alive) return;
        alive = false;
        for (EntityPayload op : payload) {
            op.onOwnerRemoved(level, this);
        }
    }

    public double radius() {
        return radius;
    }

    public List<? extends EntityPayload> payload() {
        return payload;
    }

    @Override
    public Level level() {
        return level;
    }

    @Override
    public Vec3 position() {
        return position;
    }

    @Override
    public AABB bounds() {
        return new AABB(position.x - radius, position.y - radius, position.z - radius,
                position.x + radius, position.y + radius, position.z + radius);
    }

    @Override
    public boolean isAlive() {
        return alive;
    }

    @Override
    public void discard() {
        finish();
    }

    @Override
    public List<BlockPos> listInside() {
        if (insideCache == null) {
            insideCache = MagicBallGeometry.positionsInSphere(position, radius);
        }
        return insideCache;
    }
}
