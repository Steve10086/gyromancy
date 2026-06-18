package com.astune.gyromancy.api.array;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Runtime state for a single activated magic array.
 * Tracks the array's canvas position, active status, tick counter, and effect-specific data.
 */
public class MagicArrayState {

    private final UUID arrayId;
    private final BlockPos canvasPos;
    private final ResourceLocation centerSymbolId;
    private final Map<String, Object> resolvedParams;
    private final IArrayEffect effect;
    private boolean active;
    private long activatedTick;
    private long tickCounter;
    private final Map<String, Object> runtimeData;

    public MagicArrayState(BlockPos canvasPos, ResourceLocation centerSymbolId,
                           Map<String, Object> resolvedParams, IArrayEffect effect) {
        this.arrayId = UUID.randomUUID();
        this.canvasPos = canvasPos;
        this.centerSymbolId = centerSymbolId;
        this.resolvedParams = Map.copyOf(resolvedParams);
        this.effect = effect;
        this.active = false;
        this.tickCounter = 0;
        this.runtimeData = new HashMap<>();
    }

    public UUID getArrayId() { return arrayId; }
    public BlockPos getCanvasPos() { return canvasPos; }
    public ResourceLocation getCenterSymbolId() { return centerSymbolId; }
    public Map<String, Object> getResolvedParams() { return resolvedParams; }
    public IArrayEffect getEffect() { return effect; }
    public boolean isActive() { return active; }
    public long getTickCounter() { return tickCounter; }

    /** Per-tick mutable data storage for effect implementations */
    public Map<String, Object> getRuntimeData() { return runtimeData; }

    /**
     * Activates this array and fires the effect's onActivate hook.
     */
    public void activate(ServerLevel level) {
        this.active = true;
        this.activatedTick = level.getGameTime();
        this.effect.onActivate(this, level, resolvedParams);
    }

    /**
     * Ticks this array if active. Called by MagicArrayManager each tick.
     */
    public void tick(ServerLevel level) {
        if (!active) return;
        tickCounter++;
        effect.onTick(this, level);
    }

    /**
     * Deactivates this array and fires the effect's onDeactivate hook.
     */
    public void deactivate(ServerLevel level) {
        if (!active) return;
        this.active = false;
        effect.onDeactivate(this, level);
    }

    /**
     * Checks whether the canvas block still exists at the array's position.
     */
    public boolean isCanvasIntact(ServerLevel level) {
        // Will be fully implemented in Phase 5 when we have the canvas block type
        return level.isLoaded(canvasPos) && level.getBlockState(canvasPos) != null;
    }
}
