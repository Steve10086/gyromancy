package com.astune.gyromancy.element;

import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.element.IElementStorage;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

public final class ElementStorageManager implements IElementStorage {

    public static final ElementStorageManager INSTANCE = new ElementStorageManager();
    private ElementStorageManager() {}

    @Override
    public ElementConcentrations get(Level level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        if (!(chunk instanceof IElementChunkAccessor accessor))
            return ElementBiomeProvider.getDefault(level.getBiome(pos).value());
        ElementChunkData data = accessor.gyromancy$getElementData();
        if (data == null)
            return ElementBiomeProvider.getDefault(level.getBiome(pos).value());
        ElementConcentrations c = data.get(pos);
        return c != null ? c : ElementBiomeProvider.getDefault(level.getBiome(pos).value());
    }

    @Override
    public void set(Level level, BlockPos pos, ElementConcentrations values) {
        LevelChunk chunk = level.getChunkAt(pos);
        if (!(chunk instanceof IElementChunkAccessor accessor)) return;

        ElementChunkData data = accessor.gyromancy$getOrCreateElementData();
        boolean isNew = !data.has(pos);
        data.set(pos, values);
        accessor.gyromancy$markElementDataDirty();
        ElementChunkEventHandler.markActive(chunk);

        if (isNew && level instanceof ServerLevel sl)
            ElementChunkProcessor.onFirstWrite(sl, pos, values);
    }

    @Override
    public boolean remove(Level level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        if (!(chunk instanceof IElementChunkAccessor accessor)) return false;
        ElementChunkData data = accessor.gyromancy$getElementData();
        if (data == null) return false;
        boolean removed = data.remove(pos);
        if (!removed) return false;
        if (data.isEmpty()) {
            accessor.gyromancy$clearElementData();
            ElementChunkEventHandler.markInactive(chunk);
        } else {
            accessor.gyromancy$markElementDataDirty();
        }
        return true;
    }

    @Override
    public boolean isOverridden(Level level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        if (!(chunk instanceof IElementChunkAccessor accessor)) return false;
        ElementChunkData data = accessor.gyromancy$getElementData();
        return data != null && data.has(pos);
    }

    /**
     * Invalidates cached face masks around a changed block. Masks are rebuilt
     * lazily on the next element step, so normal block updates remain cheap.
     */
    public void onBlockChanged(ServerLevel level, BlockPos pos) {
        markBarrierDirty(level, pos);
        for (Direction direction : Direction.values()) {
            markBarrierDirty(level, pos.relative(direction));
        }
    }

    /** Invalidates boundary caches when neighboring chunk block states appear. */
    public void onChunkLoaded(ServerLevel level, LevelChunk loadedChunk) {
        ChunkPos loadedPos = loadedChunk.getPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(
                        loadedPos.x + dx, loadedPos.z + dz);
                if (!(chunk instanceof IElementChunkAccessor accessor)) continue;
                ElementChunkData data = accessor.gyromancy$getElementData();
                if (data == null) continue;
                for (ElementTile tile : data.tiles().values()) tile.markBarriersDirty();
            }
        }
    }

    private void markBarrierDirty(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (!(chunk instanceof IElementChunkAccessor accessor)) return;
        ElementChunkData data = accessor.gyromancy$getElementData();
        if (data == null) return;
        ElementTile tile = data.getTile(pos);
        if (tile != null) tile.markBarriersDirty();
    }

    /** Sums non-negative values without repeated chunk attachment lookups. */
    public long sumPositive(Level level, Iterable<BlockPos> positions, ElementType type) {
        try (Batch batch = new Batch(level)) {
            long sum = 0L;
            for (BlockPos pos : positions) {
                sum += Math.max(0L, batch.get(pos).get(type));
            }
            return sum;
        }
    }

    /** Sums signed values without repeated chunk attachment lookups. */
    public long sum(Level level, Iterable<BlockPos> positions, ElementType type) {
        try (Batch batch = new Batch(level)) {
            long sum = 0L;
            for (BlockPos pos : positions) sum += batch.get(pos).get(type);
            return sum;
        }
    }

    /** Consumes up to {@code amount} from the supplied positions. */
    public long consume(
            Level level, Iterable<BlockPos> positions, ElementType type, long amount) {
        if (amount <= 0L) return 0L;
        try (Batch batch = new Batch(level)) {
            long consumed = 0L;
            for (BlockPos pos : positions) {
                if (consumed >= amount) break;
                ElementConcentrations current = batch.get(pos);
                long available = Math.max(0L, current.get(type));
                long taken = Math.min(available, amount - consumed);
                if (taken == 0L) continue;
                batch.set(pos, current.withValue(type, current.get(type) - taken));
                consumed += taken;
            }
            return consumed;
        }
    }

    /** Drains every positive value of one element and returns the total. */
    public long drainPositive(
            Level level, Iterable<BlockPos> positions, ElementType type) {
        try (Batch batch = new Batch(level)) {
            long drained = 0L;
            for (BlockPos pos : positions) {
                ElementConcentrations current = batch.get(pos);
                long value = Math.max(0L, current.get(type));
                if (value == 0L) continue;
                drained += value;
                batch.set(pos, current.withValue(type, 0L));
            }
            return drained;
        }
    }

    /** Drains signed values, matching the historical ElementOp behavior. */
    public long drainAll(
            Level level, Iterable<BlockPos> positions, ElementType type) {
        try (Batch batch = new Batch(level)) {
            long drained = 0L;
            for (BlockPos pos : positions) {
                ElementConcentrations current = batch.get(pos);
                long value = current.get(type);
                if (value == 0L) continue;
                drained += value;
                batch.set(pos, current.withValue(type, 0L));
            }
            return drained;
        }
    }

    /** Adds the same amount to one element at every supplied position. */
    public void addToEach(
            Level level, Iterable<BlockPos> positions, ElementType type, long amount) {
        if (amount == 0L) return;
        try (Batch batch = new Batch(level)) {
            for (BlockPos pos : positions) {
                ElementConcentrations current = batch.get(pos);
                batch.set(pos, current.withValue(type, current.get(type) + amount));
            }
        }
    }

    /**
     * Applies the legacy release transform {@code (current + addition) * factor}
     * in one chunk-batched pass.
     */
    public void addAndScaleEach(
            Level level,
            Iterable<BlockPos> positions,
            ElementType type,
            float addition,
            float factor) {
        try (Batch batch = new Batch(level)) {
            for (BlockPos pos : positions) {
                ElementConcentrations current = batch.get(pos);
                long value = (long) ((current.get(type) + addition) * factor);
                batch.set(pos, current.withValue(type, value));
            }
        }
    }

    /**
     * Applies the paired element/mana reduction used by elemental balls.
     */
    public void reduceWithMana(
            Level level,
            Iterable<BlockPos> positions,
            ElementType type,
            long manaCost) {
        if (manaCost <= 0L) return;
        try (Batch batch = new Batch(level)) {
            for (BlockPos pos : positions) {
                ElementConcentrations current = batch.get(pos);
                long mana = Math.max(0L, current.get(ElementType.MANA));
                long element = Math.max(0L, current.get(type));
                long removed = Math.min(element, mana / manaCost);
                if (removed == 0L) continue;
                batch.set(pos, current
                        .withValue(type, element - removed)
                        .withValue(ElementType.MANA, mana - removed * manaCost));
            }
        }
    }

    /**
     * Groups a bulk operation's cells by chunk and marks each changed
     * attachment dirty only once.
     */
    private static final class Batch implements AutoCloseable {
        private final Level level;
        private final Long2ObjectOpenHashMap<ChunkAccess> chunks =
                new Long2ObjectOpenHashMap<>();

        private Batch(Level level) {
            this.level = level;
        }

        ElementConcentrations get(BlockPos pos) {
            ChunkAccess access = access(pos);
            if (access == null || access.data == null) {
                return ElementBiomeProvider.getDefault(level.getBiome(pos).value());
            }
            ElementConcentrations stored = access.data.get(pos);
            return stored != null
                    ? stored
                    : ElementBiomeProvider.getDefault(level.getBiome(pos).value());
        }

        void set(BlockPos pos, ElementConcentrations concentrations) {
            ChunkAccess access = access(pos);
            if (access == null) return;
            if (access.data == null) {
                access.data = access.accessor.gyromancy$getOrCreateElementData();
            }
            boolean isNew = !access.data.has(pos);
            access.data.set(pos, concentrations);
            access.dirty = true;
            if (isNew && level instanceof ServerLevel serverLevel) {
                ElementChunkProcessor.onFirstWrite(serverLevel, pos, concentrations);
            }
        }

        private ChunkAccess access(BlockPos pos) {
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            long key = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
            ChunkAccess cached = chunks.get(key);
            if (cached != null) return cached;
            LevelChunk chunk = level.getChunkAt(pos);
            if (!(chunk instanceof IElementChunkAccessor accessor)) return null;
            ChunkAccess created =
                    new ChunkAccess(chunk, accessor, accessor.gyromancy$getElementData());
            chunks.put(key, created);
            return created;
        }

        @Override
        public void close() {
            for (ChunkAccess access : chunks.values()) {
                if (access == null || !access.dirty) continue;
                access.accessor.gyromancy$markElementDataDirty();
                ElementChunkEventHandler.markActive(access.chunk);
            }
        }
    }

    private static final class ChunkAccess {
        private final LevelChunk chunk;
        private final IElementChunkAccessor accessor;
        private ElementChunkData data;
        private boolean dirty;

        private ChunkAccess(
                LevelChunk chunk,
                IElementChunkAccessor accessor,
                ElementChunkData data) {
            this.chunk = chunk;
            this.accessor = accessor;
            this.data = data;
        }
    }
}
