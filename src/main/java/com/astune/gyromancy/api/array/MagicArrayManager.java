package com.astune.gyromancy.api.array;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/**
 * Tracks all active magic arrays within a single dimension.
 * Stored as an Attachment on {@code Level} via {@code ModAttachments.ARRAY_MANAGER}.
 */
public class MagicArrayManager {

    private final Map<UUID, MagicArrayState> arrays = new HashMap<>();
    private final Map<BlockPos, UUID> positionIndex = new HashMap<>();

    public MagicArrayManager() {}

    /** Registers a newly activated array */
    public void registerArray(MagicArrayState state) {
        arrays.put(state.getArrayId(), state);
        positionIndex.put(state.getCanvasPos(), state.getArrayId());
    }

    /** Unregisters a deactivated array */
    public void unregisterArray(UUID arrayId) {
        MagicArrayState state = arrays.remove(arrayId);
        if (state != null) {
            positionIndex.remove(state.getCanvasPos());
        }
    }

    /** Gets the array at a specific position, if any */
    public Optional<MagicArrayState> getArrayAt(BlockPos pos) {
        UUID id = positionIndex.get(pos);
        if (id == null) return Optional.empty();
        return Optional.ofNullable(arrays.get(id));
    }

    /** Gets an array by its ID */
    public Optional<MagicArrayState> getArray(UUID id) {
        return Optional.ofNullable(arrays.get(id));
    }

    /** Returns all active arrays */
    public Collection<MagicArrayState> getAllArrays() {
        return Collections.unmodifiableCollection(arrays.values());
    }

    /** Ticks all active arrays, removing any that are no longer intact */
    public void tickAll(ServerLevel level) {
        Iterator<Map.Entry<UUID, MagicArrayState>> it = arrays.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, MagicArrayState> entry = it.next();
            MagicArrayState state = entry.getValue();
            if (!state.isCanvasIntact(level)) {
                state.deactivate(level);
                positionIndex.remove(state.getCanvasPos());
                it.remove();
            } else if (state.isActive()) {
                state.tick(level);
            }
        }
    }

    public int getActiveCount() {
        return arrays.size();
    }
}
