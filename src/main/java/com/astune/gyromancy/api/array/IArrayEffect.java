package com.astune.gyromancy.api.array;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Map;

/**
 * Core interface for magic array effects. Each center symbol is bound to an
 * IArrayEffect implementation via the {@code ArrayEffectRegistry}.
 *
 * <p>The lifecycle is:
 * <ol>
 *   <li>{@link #onActivate} — called once when the array is first activated</li>
 *   <li>{@link #onTick} — called each server tick while the array is active and intact</li>
 *   <li>{@link #onDeactivate} — called when the array is destroyed or deactivated</li>
 * </ol>
 */
public interface IArrayEffect {

    /**
     * Called once when the magic array is successfully activated.
     *
     * @param state  the runtime state of the activated array
     * @param level  the server world
     * @param params resolved parameters from parameter runes (name → value)
     */
    void onActivate(MagicArrayState state, ServerLevel level, Map<String, Object> params);

    /**
     * Called every server tick while the array is active and intact.
     *
     * @param state the runtime state of the array
     * @param level the server world
     */
    void onTick(MagicArrayState state, ServerLevel level);

    /**
     * Called when the array is deactivated — either destroyed, canvas removed, or manually stopped.
     *
     * @param state the runtime state before deactivation
     * @param level the server world
     */
    void onDeactivate(MagicArrayState state, ServerLevel level);
}
