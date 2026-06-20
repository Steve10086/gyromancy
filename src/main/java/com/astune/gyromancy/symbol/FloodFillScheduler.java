package com.astune.gyromancy.symbol;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractedGlyph;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractionResult;
import com.astune.gyromancy.symbol.FloodFillExtractor.FloodFillState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Tick-budgeted scheduler for cross-block flood fill operations.
 *
 * <p>Seeds from one canvas update are submitted as a batch and placed into
 * a single FloodFillState. During BFS, connected seeds merge naturally;
 * disconnected seeds produce separate glyphs from the same state.
 */
@EventBusSubscriber(modid = Gyromancy.MODID)
public final class FloodFillScheduler {

    static final int MAX_BLOCKS_PER_TICK = 20;
    private static final double MERGE_MARGIN = 1.5;

    private static final Queue<PendingTask> pendingTasks = new ConcurrentLinkedQueue<>();
    private static PendingTask activeContinuation = null;

    private static final List<FloodFillState> activeStates = new CopyOnWriteArrayList<>();
    private static final List<BiConsumer<ServerLevel, ExtractedGlyph>> completionCallbacks = new ArrayList<>();

    private FloodFillScheduler() {}

    // ═══════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Submits a batch of seeds from one canvas update for flood fill extraction.
     * All seeds start in the same FloodFillState queue; connected ones merge via BFS.
     */
    public static void submitBatch(ServerLevel level, List<PixelPos> seeds) {
        if (seeds.isEmpty()) return;

        // Try to merge into any existing active state
        PixelPos first = seeds.getFirst();
        boolean merged = false;

        // Try to find a CanvasFace for the first seed to compute world bounds
        var faces = FloodFillExtractor.getFacesAt(level, first.pos(), first.face());
        if (!faces.isEmpty()) {
            Vec3 w3d = FloodFillExtractor.worldFromPixel(first.pos(), faces.getFirst(), first.x(), first.y());
            double[] w2d = FloodFillExtractor.flatten(faces.getFirst().primaryFace(), w3d);
            double wx = w2d[0], wy = w2d[1];
            for (FloodFillState state : activeStates) {
                if (isInBounds(state, wx, wy)) {
                    for (PixelPos s : seeds) {
                        if (!state.visited.contains(s)) state.queue.add(s);
                    }
                    merged = true;
                    Gyromancy.LOGGER.debug("[FloodFillScheduler] Merged {} seeds into active fill ({} visited)",
                            seeds.size(), state.visited.size());
                    break;
                }
            }
        }
        if (merged) return;

        // New batch
        FloodFillState state = new FloodFillState(seeds.getFirst());
        state.initialSeeds.addAll(seeds);
        for (int i = 1; i < seeds.size(); i++) state.queue.add(seeds.get(i));
        pendingTasks.add(new PendingTask(level, null, state));

        Gyromancy.LOGGER.debug("[FloodFillScheduler] Submitted batch: {} seeds", seeds.size());
    }

    public static void onGlyphExtracted(BiConsumer<ServerLevel, ExtractedGlyph> callback) {
        completionCallbacks.add(callback);
    }

    static int getPendingCount() {
        return pendingTasks.size() + (activeContinuation != null ? 1 : 0);
    }

    // ═══════════════════════════════════════════════════════════════
    // Tick handler
    // ═══════════════════════════════════════════════════════════════

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        int remainingBudget = MAX_BLOCKS_PER_TICK;

        // 1. Continue in-progress extraction
        if (activeContinuation != null) {
            ExtractionResult result = FloodFillExtractor.continueExtract(
                    activeContinuation.level, activeContinuation.state, remainingBudget);
            activeContinuation = handleResult(activeContinuation.level, result, activeContinuation);
            if (activeContinuation != null) return; // budget exhausted or more groups
        }

        // 2. Process pending batches
        while (!pendingTasks.isEmpty() && remainingBudget > 0) {
            PendingTask task = pendingTasks.poll();
            if (task == null) break;

            ExtractionResult result;
            if (task.state != null) {
                result = FloodFillExtractor.continueExtract(task.level, task.state, remainingBudget);
            } else {
                result = FloodFillExtractor.extract(task.level, task.seed, remainingBudget);
            }

            PendingTask next = handleResult(task.level, result, task);
            if (next != null) {
                activeContinuation = next;
                break;
            }
        }
    }

    /** Returns non-null if there's more work to continue next tick */
    private static PendingTask handleResult(ServerLevel level, ExtractionResult result, PendingTask task) {
        if (result.glyph() != null) {
            fireCompletion(level, result.glyph());
        }

        if (result.continuation() != null) {
            if (!activeStates.contains(result.continuation())) {
                activeStates.add(result.continuation());
            }
            return new PendingTask(task.level, null, result.continuation());
        }

        // Fully done
        return null;
    }

    private static void fireCompletion(ServerLevel level, ExtractedGlyph glyph) {
        for (BiConsumer<ServerLevel, ExtractedGlyph> callback : completionCallbacks) {
            try { callback.accept(level, glyph); }
            catch (Exception e) { Gyromancy.LOGGER.error("[FloodFillScheduler] Callback error", e); }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Bounds merging
    // ═══════════════════════════════════════════════════════════════

    private static boolean isInBounds(FloodFillState state, double wx, double wy) {
        if (state.minWorldX == Double.MAX_VALUE) return false;
        return wx >= state.minWorldX - MERGE_MARGIN && wx <= state.maxWorldX + MERGE_MARGIN
            && wy >= state.minWorldY - MERGE_MARGIN && wy <= state.maxWorldY + MERGE_MARGIN;
    }

    // ═══════════════════════════════════════════════════════════════
    // Internal types
    // ═══════════════════════════════════════════════════════════════

    private record PendingTask(ServerLevel level, PixelPos seed, FloodFillState state) {}
}
