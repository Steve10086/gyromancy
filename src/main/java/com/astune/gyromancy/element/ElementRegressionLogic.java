package com.astune.gyromancy.element;

import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.api.element.ElementType;

/**
 * Computes element concentration updates using spatial diffusion, decay, and recovery.
 *
 * <h3>Algorithm per tick per overridden position:</h3>
 * <ol>
 *   <li><b>Spatial diffusion</b> — average with surrounding 3×3 grid (self + 8 neighbors).
 *       Non-overridden neighbors contribute their biome-default values.</li>
 *   <li><b>Decay excess</b> — values above default lose 10% of the excess.</li>
 *   <li><b>Recover deficit</b> — values below default (and above 0) gain 10% of the deficit.</li>
 *   <li><b>Cleanup</b> — if all 10 elements satisfy {@code |value - default| < default × 0.1},
 *       the position is removed from tracking.</li>
 * </ol>
 */
public final class ElementRegressionLogic {

    private ElementRegressionLogic() {}

    /** Decay/recovery rate: 10% per tick */
    public static final float RATE = 0.1f;

    /** Cleanup threshold: difference must be less than 10% of the default value */
    public static final float CLEANUP_FACTOR = 0.1f;

    /**
     * Processes one element position through the full algorithm.
     *
     * @param current       the current override values at this position
     * @param neighbors     the 8 neighboring override values (null entries = not overridden)
     * @param biomeDefaults the biome-default concentrations for this position
     * @return a {@link ProcessResult} containing the new concentrations and whether to clean up
     */
    public static ProcessResult processPosition(
            ElementConcentrations current,
            ElementConcentrations[] neighbors,
            ElementConcentrations biomeDefaults) {

        // Step 1: Spatial diffusion
        ElementConcentrations diffused = current.diffuse(neighbors, biomeDefaults);

        // Step 2–3: Decay excess / recover deficit
        ElementConcentrations result = diffused.decayExcessAndRecoverDeficit(biomeDefaults);

        // Step 4: Cleanup check
        boolean shouldCleanup = result.isCloseToDefault(biomeDefaults);

        return new ProcessResult(result, shouldCleanup);
    }

    /**
     * Checks whether a single element type satisfies the cleanup condition:
     * {@code |value - default| < default × CLEANUP_FACTOR}.
     */
    public static boolean isElementCloseToDefault(float value, float defaultVal) {
        return Math.abs(value - defaultVal) < defaultVal * CLEANUP_FACTOR;
    }

    /**
     * Result of processing one position. Contains the new concentrations and cleanup flag.
     */
    public record ProcessResult(ElementConcentrations newConcentrations, boolean shouldCleanup) {}
}
