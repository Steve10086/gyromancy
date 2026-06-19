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

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;

/**
 * Tick-budgeted scheduler for cross-block flood fill operations.
 *
 * <p>Flood fills can span many blocks. To avoid lag spikes, this scheduler
 * enforces a per-tick block budget ({@link #MAX_BLOCKS_PER_TICK}) and defers
 * excess work to subsequent ticks via {@link ServerTickEvent.Post}.
 *
 * <p>Usage from Phase 5's MagicArrayDetector:
 * <pre>{@code
 *   FloodFillScheduler.submit(level, seed);
 * }</pre>
 *
 * <p>The scheduler automatically invokes the callback when a glyph extraction completes.
 */
@EventBusSubscriber(modid = Gyromancy.MODID)
public final class FloodFillScheduler {

    /** Maximum distinct blocks to visit per tick */
    static final int MAX_BLOCKS_PER_TICK = 20;

    /** Pending seed pixels waiting to be processed */
    private static final Queue<PendingTask> pendingTasks = new ConcurrentLinkedQueue<>();

    /** Currently active continuation (partial extraction from previous tick) */
    private static PendingTask activeContinuation = null;

    /** Callback invoked when a glyph is fully extracted and recognized */
    private static final List<BiConsumer<ServerLevel, ExtractedGlyph>> completionCallbacks = new ArrayList<>();

    private FloodFillScheduler() {}

    // ═══════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Submits a new flood fill task starting from a seed mana pixel.
     */
    public static void submit(ServerLevel level, PixelPos seed) {
        pendingTasks.add(new PendingTask(level, seed, null));
    }

    /**
     * Registers a callback invoked when a glyph extraction completes.
     * Phase 5's MagicArrayDetector registers here to receive extracted glyphs
     * for recognition and compilation.
     */
    public static void onGlyphExtracted(BiConsumer<ServerLevel, ExtractedGlyph> callback) {
        completionCallbacks.add(callback);
    }

    /**
     * Returns the number of pending tasks (seeds + active continuation).
     */
    public static int getPendingCount() {
        int count = pendingTasks.size();
        if (activeContinuation != null) count++;
        return count;
    }

    // ═══════════════════════════════════════════════════════════════
    // Tick handler
    // ═══════════════════════════════════════════════════════════════

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        int remainingBudget = MAX_BLOCKS_PER_TICK;

        // 1. Continue any in-progress extraction from the previous tick
        if (activeContinuation != null) {
            ExtractionResult result = FloodFillExtractor.continueExtract(
                    activeContinuation.level,
                    activeContinuation.state,
                    remainingBudget
            );

            if (result.glyph() != null) {
                // Extraction completed
                fireCompletion(activeContinuation.level, result.glyph());
                activeContinuation = null;
            } else if (result.continuation() != null) {
                // Budget exhausted — save state for next tick
                activeContinuation = new PendingTask(
                        activeContinuation.level, null, result.continuation()
                );
                return; // no more budget this tick
            } else {
                // Extraction failed
                activeContinuation = null;
            }
        }

        // 2. Process new seed tasks from the queue
        while (!pendingTasks.isEmpty() && remainingBudget > 0) {
            PendingTask task = pendingTasks.poll();
            if (task == null) break;

            ExtractionResult result;
            if (task.state != null) {
                // Resume a continuation
                result = FloodFillExtractor.continueExtract(task.level, task.state, remainingBudget);
            } else {
                // New extraction from seed
                result = FloodFillExtractor.extract(task.level, task.seed, remainingBudget);
            }

            if (result.glyph() != null) {
                fireCompletion(task.level, result.glyph());
            } else if (result.continuation() != null) {
                activeContinuation = new PendingTask(
                        task.level, null, result.continuation()
                );
                break; // budget exhausted
            }
        }
    }

    private static void fireCompletion(ServerLevel level, ExtractedGlyph glyph) {
        for (BiConsumer<ServerLevel, ExtractedGlyph> callback : completionCallbacks) {
            try {
                callback.accept(level, glyph);
            } catch (Exception e) {
                Gyromancy.LOGGER.error("[FloodFillScheduler] Error in completion callback", e);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Internal types
    // ═══════════════════════════════════════════════════════════════

    private record PendingTask(
            ServerLevel level,
            PixelPos seed,
            FloodFillState state
    ) {}
}
