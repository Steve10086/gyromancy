package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.entity.MagicEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Shared behaviour for payloads that grow a matching element crystal inside
 * their effect's active geometry.
 *
 * <p>The payload keeps a transient map of legal candidate positions inside
 * {@link MagicEntity#listInside()}. Every {@link #SCAN_INTERVAL} ticks the map
 * is resynchronised against the effect geometry and the element storage;
 * existing entries have their residency timer advanced by {@link
 * #SCAN_INTERVAL} (rather than by one). While no crystal is currently placed,
 * every tick the payload rolls {@link #BASE_PLACE_PROBABILITY}; each failed
 * roll grows the probability from the cached concentration sum. A successful
 * placement resets the probability, and an existing crystal keeps the payload
 * in a non-accumulating wait state until that crystal disappears. Placed
 * crystals and the current probability are persisted through the payload
 * codec so that resuming a saved world does not restart the growth process.</p>
 */
public abstract class CrystalGenOp extends OnEntityTickOp {
    public static final int SCAN_INTERVAL = 10;
    public static final long ELEMENT_THRESHOLD = 8_000L;
    public static final double BASE_PLACE_PROBABILITY = 0.02;
    public static final double PROBABILITY_GROWTH_BASE = 0.25/20;
    public static final double PROBABILITY_GROWTH_PER_UNIT = 0.25/8000.0/20;
    private static final int MAX_TRACKED = 512;

    private final Map<BlockPos, Long> tracked = new HashMap<>();
    private final Random random = new Random();
    private BlockPos currentCrystal;
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

        if (currentCrystal != null && !isCrystalPlaced(level)) {
            currentCrystal = null;
            placeProbability = BASE_PLACE_PROBABILITY;
        }

        if (ctx.tickCount() % SCAN_INTERVAL == 0) {
            totalLegalConcentration = updateTracked(tracked, effect.listInside(),
                    pos -> ctx.elementStorage().get(level, pos).get(element()),
                    pos -> level.isEmptyBlock(pos),
                    ELEMENT_THRESHOLD, SCAN_INTERVAL);
        }

        if (currentCrystal != null) {
            placeProbability = BASE_PLACE_PROBABILITY;
            return;
        }

        if (tracked.isEmpty()) {
            placeProbability = BASE_PLACE_PROBABILITY;
            return;
        }

        if (random.nextFloat() < (float) placeProbability) {
            placeBlock(level, ctx);
        } else {
            placeProbability = nextProbability(placeProbability, totalLegalConcentration);
        }
    }

    private void placeBlock(Level level, EntityTickContext ctx) {
        BlockPos candidate = randomCandidate();
        if (candidate == null) {
            placeProbability = BASE_PLACE_PROBABILITY;
            return;
        }
        long cell = ctx.elementStorage().get(level, candidate).get(element());
        if (cell >= ELEMENT_THRESHOLD && level.isEmptyBlock(candidate)) {
            level.setBlockAndUpdate(candidate, crystalBlock().defaultBlockState());
            currentCrystal = candidate;
            placeProbability = BASE_PLACE_PROBABILITY;
        } else {
            tracked.remove(candidate);
            totalLegalConcentration = Math.max(0L, totalLegalConcentration - Math.max(0L, cell));
            placeProbability = nextProbability(placeProbability, totalLegalConcentration);
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
     * whose element level or air state no longer qualifies are evicted. The
     * returned total is the sum of element levels of all tracked positions.
     */
    static long updateTracked(Map<BlockPos, Long> tracked, Iterable<BlockPos> inside,
                              Function<BlockPos, Long> elementLevel, Predicate<BlockPos> isAir,
                              long threshold, long interval) {
        long total = 0L;
        for (BlockPos pos : inside) {
            BlockPos key = pos.immutable();
            long level = elementLevel.apply(key);
            if (level >= threshold && isAir.test(key)) {
                tracked.merge(key, interval, Long::sum);
                total += level;
            } else {
                tracked.remove(key);
            }
        }
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
