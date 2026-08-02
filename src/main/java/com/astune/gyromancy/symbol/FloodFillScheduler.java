package com.astune.gyromancy.symbol;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractedGlyph;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractionResult;
import com.astune.gyromancy.symbol.FloodFillExtractor.FloodFillState;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;

/**
 * Tick-budgeted scheduler with global seed list and origin-based merging.
 *
 * <p>All active FloodFillStates live in a single {@link #allSeeds} map.
 * When a BFS reaches another state's origin pixel, the states merge.
 * This handles Pigmentum's per-block update packets without spatial heuristics.
 */
@EventBusSubscriber(modid = Gyromancy.MODID)
public final class FloodFillScheduler {

    static final int MAX_BLOCKS_PER_TICK = 20;

    /** Global active states, only accessed from ServerTickEvent.Post (single thread) */
    static final Map<Integer, FloodFillState> allSeeds = new LinkedHashMap<>();
    private static int nextStateId = 1;

    private static final Queue<PendingTask> pendingTasks = new ConcurrentLinkedQueue<>();
    private static PendingTask activeContinuation = null;

    private static final List<BiConsumer<ServerLevel, ExtractedGlyph>> completionCallbacks = new ArrayList<>();
    private static final List<BiConsumer<ServerLevel, List<ExtractedGlyph>>> batchCompletionCallbacks =
            new ArrayList<>();

    static {
        FloodFillExtractor.setAllSeedsRef(allSeeds);
    }

    private FloodFillScheduler() {}

    // ═══════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════

    public static void submitBatch(ServerLevel level, List<PixelPos> seeds) {
        if (seeds.isEmpty()) return;

        int id = nextStateId++;
        FloodFillState state = new FloodFillState(id, seeds.getFirst());
        state.initialSeeds.addAll(seeds);

        allSeeds.put(id, state);
        pendingTasks.add(new PendingTask(level, state));

        Gyromancy.LOGGER.debug("[FloodFillScheduler] Batch #{}: {} seeds", id, seeds.size());
    }

    public static void onGlyphExtracted(BiConsumer<ServerLevel, ExtractedGlyph> callback) {
        completionCallbacks.add(callback);
    }

    /** Runs after every disconnected component in one submitted update has been extracted. */
    public static void onBatchExtracted(
            BiConsumer<ServerLevel, List<ExtractedGlyph>> callback) {
        batchCompletionCallbacks.add(callback);
    }

    static int getPendingCount() {
        return pendingTasks.size() + (activeContinuation != null ? 1 : 0);
    }

    // ═══════════════════════════════════════════════════════════════
    // Tick handler — single-threaded via ServerTickEvent.Post
    // ═══════════════════════════════════════════════════════════════

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        int remainingBudget = MAX_BLOCKS_PER_TICK;

        // 1. Continue in-progress extraction
        if (activeContinuation != null) {
            ExtractionResult result = FloodFillExtractor.continueExtract(
                    activeContinuation.level, activeContinuation.state, remainingBudget);
            activeContinuation = handleResult(activeContinuation.level, result, activeContinuation.state);
            if (activeContinuation != null) return;
        }

        // 2. Process pending batches
        while (!pendingTasks.isEmpty() && remainingBudget > 0) {
            PendingTask task = pendingTasks.poll();
            if (task == null || !allSeeds.containsKey(task.state.stateId)) continue;

            ExtractionResult result = FloodFillExtractor.continueExtract(
                    task.level, task.state, remainingBudget);

            PendingTask next = handleResult(task.level, result, task.state);
            if (next != null) {
                activeContinuation = next;
                break;
            }
        }
    }

    private static PendingTask handleResult(ServerLevel level, ExtractionResult result, FloodFillState state) {
        if (result.glyph() != null) {
            state.completedGlyphs.add(result.glyph());
        }

        if (result.continuation() != null) {
            return new PendingTask(level, result.continuation());
        }

        // Fully done — remove from global list
        allSeeds.remove(state.stateId);
        fireBatchCompletion(level, state.completedGlyphs);
        Gyromancy.LOGGER.debug("[FloodFillScheduler] State #{} completed and removed", state.stateId);
        return null;
    }

    private static void fireCompletion(ServerLevel level, ExtractedGlyph glyph) {
        for (BiConsumer<ServerLevel, ExtractedGlyph> callback : completionCallbacks) {
            try { callback.accept(level, glyph); }
            catch (Exception e) { Gyromancy.LOGGER.error("[FloodFillScheduler] Callback error", e); }
        }
    }

    private static void fireBatchCompletion(ServerLevel level, List<ExtractedGlyph> glyphs) {
        if (glyphs.isEmpty()) return;

        List<ExtractedGlyph> completed = List.copyOf(glyphs);
        for (BiConsumer<ServerLevel, List<ExtractedGlyph>> callback : batchCompletionCallbacks) {
            try { callback.accept(level, completed); }
            catch (Exception e) { Gyromancy.LOGGER.error("[FloodFillScheduler] Batch callback error", e); }
        }
        for (ExtractedGlyph glyph : completed) fireCompletion(level, glyph);
    }

    // ═══════════════════════════════════════════════════════════════
    // Internal types
    // ═══════════════════════════════════════════════════════════════

    private record PendingTask(ServerLevel level, FloodFillState state) {}
}
