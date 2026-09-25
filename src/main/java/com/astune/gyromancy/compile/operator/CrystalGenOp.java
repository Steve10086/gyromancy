package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.entity.MagicEntity;
import com.astune.gyromancy.network.CrystalSpawnFxPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Shared behaviour for payloads that grow a matching element crystal inside
 * their effect's active geometry.
 *
 * <p>The payload keeps a transient map of legal candidate positions inside
 * {@link MagicEntity#listInside()}. Every {@link #SCAN_INTERVAL} ticks the map
 * is resynchronised against the effect geometry and the element storage:
 * positions that left the geometry or no longer qualify are dropped, and
 * surviving entries have their residency timer advanced by {@link
 * #SCAN_INTERVAL} (rather than by one). Because the roll runs before the
 * resynchronisation it only ever picks positions that already qualified on the
 * previous scan, so a freshly entered position waits one full interval before
 * it can be chosen.</p>
 *
 * <p>The spawn attempt and its probability growth both happen once per scan
 * interval. A chosen position plays {@code gyromancy:crystal_spawn} immediately
 * and only receives its block {@link #PLACE_DELAY_TICKS} ticks later, which is
 * when the block's own grow-in animation starts. Placed crystals and the
 * current probability are persisted through the payload codec so that resuming
 * a saved world does not restart the growth process; the short pending delay is
 * intentionally transient.</p>
 */
public abstract class CrystalGenOp extends OnEntityTickOp {
    public static final int SCAN_INTERVAL = 10;
    public static final int PLACE_DELAY_TICKS = 10;
    public static final long ELEMENT_THRESHOLD = 8_000L;
    public static final double BASE_PLACE_PROBABILITY = 0.02;
    public static final double PROBABILITY_GROWTH_BASE = 0.25/20;
    public static final double PROBABILITY_GROWTH_PER_UNIT = 0.25/8000.0/20;
    private static final int MAX_TRACKED = 512;

    private final Map<BlockPos, Long> tracked = new HashMap<>();
    private final Random random = new Random();
    private BlockPos currentCrystal;
    private BlockPos pendingCrystal;
    private int pendingCrystalTicks;
    private double placeProbability;
    private long totalLegalConcentration;

    protected CrystalGenOp(BlockPos currentCrystal, double placeProbability) {
        this.currentCrystal = currentCrystal;
        this.placeProbability = placeProbability;
    }

    protected abstract ElementType element();

    protected abstract Block crystalBlock();

    protected BlockPos currentCrystal() {
        return currentCrystal;
    }

    protected double placeProbability() {
        return placeProbability;
    }

    @Override
    public void onEntityTick(EntityTickContext ctx) {
        if (ctx.isClientSide() || !(ctx.owner() instanceof MagicEntity effect)) return;
        Level level = ctx.level();

        if (pendingCrystal != null) {
            if (--pendingCrystalTicks <= 0) {
                placeBlock(level, ctx, pendingCrystal);
                pendingCrystal = null;
            }
            return;
        }

        if (currentCrystal != null && !isCrystalPlaced(level)) {
            currentCrystal = null;
            placeProbability = BASE_PLACE_PROBABILITY;
        }

        if (ctx.tickCount() % SCAN_INTERVAL != 0) return;

        // Roll before the resynchronisation: candidates therefore come from the
        // previous scan, so every legal position was already above the element
        // threshold on the previous tick.
        if (currentCrystal == null && !tracked.isEmpty()
                && random.nextFloat() < (float) placeProbability) {
            BlockPos candidate = randomCandidate();
            if (candidate != null) {
                selectCandidate(level, candidate);
            }
        }

        totalLegalConcentration = updateTracked(tracked, effect.listInside(),
                pos -> ctx.elementStorage().get(level, pos).get(element()),
                pos -> level.isEmptyBlock(pos),
                ELEMENT_THRESHOLD, SCAN_INTERVAL);

        if (currentCrystal != null || pendingCrystal != null || tracked.isEmpty()) {
            placeProbability = BASE_PLACE_PROBABILITY;
        } else {
            placeProbability = nextProbability(placeProbability, totalLegalConcentration);
        }
    }

    /** Marks a position as chosen, plays its spawn effect and starts the delay. */
    private void selectCandidate(Level level, BlockPos candidate) {
        pendingCrystal = candidate.immutable();
        pendingCrystalTicks = PLACE_DELAY_TICKS;
        placeProbability = BASE_PLACE_PROBABILITY;
        if (level instanceof ServerLevel serverLevel) {
            CrystalSpawnFxPacket.broadcast(serverLevel, pendingCrystal, element());
        }
    }

    private void placeBlock(Level level, EntityTickContext ctx, BlockPos candidate) {
        long cell = ctx.elementStorage().get(level, candidate).get(element());
        if (cell >= ELEMENT_THRESHOLD && level.isEmptyBlock(candidate)) {
            level.setBlockAndUpdate(candidate, crystalBlock().defaultBlockState());
            currentCrystal = candidate;
        } else {
            // The position stopped qualifying during the spawn delay.
            tracked.remove(candidate);
        }
    }

    private boolean isCrystalPlaced(Level level) {
        return currentCrystal != null && level.getBlockState(currentCrystal).is(crystalBlock());
    }

    private BlockPos randomCandidate() {
        if (tracked.isEmpty()) return null;
        int index = random.nextInt(tracked.size());
        Iterator<BlockPos> iterator = tracked.keySet().iterator();
        BlockPos selected = null;
        for (int i = 0; i <= index && iterator.hasNext(); i++) selected = iterator.next();
        return selected;
    }

    /**
     * Resynchronises the candidate map against the effect geometry, advancing
     * every surviving entry's residency timer by {@code interval}. Positions
     * that are no longer inside the geometry, are no longer air, or whose
     * element level fell below the threshold are evicted. The returned total is
     * the sum of element levels of all tracked positions.
     */
    static long updateTracked(Map<BlockPos, Long> tracked, Iterable<BlockPos> inside,
                              Function<BlockPos, Long> elementLevel, Predicate<BlockPos> isAir,
                              long threshold, long interval) {
        Set<BlockPos> visited = new HashSet<>();
        long total = 0L;
        for (BlockPos pos : inside) {
            BlockPos key = pos.immutable();
            visited.add(key);
            long level = elementLevel.apply(key);
            if (level >= threshold && isAir.test(key)) {
                tracked.merge(key, interval, Long::sum);
                total += level;
            } else {
                tracked.remove(key);
            }
        }
        // Positions the effect has moved away from stop being candidates.
        tracked.keySet().removeIf(pos -> !visited.contains(pos));
        while (tracked.size() > MAX_TRACKED) {
            Map.Entry<BlockPos, Long> oldest = null;
            long minimum = Long.MAX_VALUE;
            for (Map.Entry<BlockPos, Long> entry : tracked.entrySet()) {
                if (entry.getValue() < minimum) {
                    minimum = entry.getValue();
                    oldest = entry;
                }
            }
            if (oldest == null) break;
            long evictedLevel = elementLevel.apply(oldest.getKey());
            total = Math.max(0L, total - Math.max(0L, evictedLevel));
            tracked.remove(oldest.getKey());
        }
        return total;
    }

    static double nextProbability(double current, long totalConcentration) {
        return current + (PROBABILITY_GROWTH_BASE + totalConcentration * PROBABILITY_GROWTH_PER_UNIT);
    }
}
