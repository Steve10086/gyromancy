package com.astune.gyromancy.element;

import com.astune.gyromancy.Config;
import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.element.event.ElementActivatedEvent;
import com.astune.gyromancy.element.event.ElementChangeEvent;
import com.astune.gyromancy.element.event.ElementCleanedUpEvent;
import com.astune.gyromancy.element.event.ElementEventBus;
import com.astune.gyromancy.element.event.ElementThresholdEvent;
import com.astune.gyromancy.element.event.ThresholdDirection;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Block-accurate element solver over dense 8x8x8 tiles.
 *
 * <p>Each level is advanced from one snapshot: first local decay/recovery,
 * then conservative six-face flux. This avoids BlockPos maps, neighbor object
 * expansion, and chunk-order-dependent double processing.</p>
 */
public final class ElementChunkProcessor {

    private static final int FLUX_DIVISOR = 7;
    private static final ElementType[] ELEMENTS = ElementType.values();
    private static final Direction[] DIRECTIONS = Direction.values();

    private ElementChunkProcessor() {}

    /**
     * Advances every currently active, loaded chunk in a level.
     *
     * @return the number of chunks that contained element tile data
     */
    public static int processLevel(ServerLevel level, Collection<ChunkPos> activeChunkPositions) {
        Long2ObjectOpenHashMap<TileRef> tiles =
                collectTiles(level, activeChunkPositions);
        if (tiles.isEmpty()) return 0;

        Set<LevelChunk> touchedChunks = new LinkedHashSet<>();
        for (TileRef ref : tiles.values()) {
            ref.tile().clearDerivatives();
            ref.tile().clearPendingFlux();
            if (ref.tile().barriersDirty()) refreshBarriers(level, ref);
            decayAndRecover(level, ref);
            touchedChunks.add(ref.chunk());
        }

        if (Config.ENABLE_ELEMENT_DIFFUSION.get()) {
            Long2ObjectOpenHashMap<TileDelta> deltas = computeFlux(level, tiles);
            applyFlux(level, deltas, touchedChunks);
        }

        finishChunks(touchedChunks);
        return touchedChunks.size();
    }

    public static long computeFaceFlow(long excessA, long excessB) {
        return (excessA - excessB) / FLUX_DIVISOR;
    }

    private static Long2ObjectOpenHashMap<TileRef> collectTiles(
            ServerLevel level, Collection<ChunkPos> activeChunkPositions) {
        Long2ObjectOpenHashMap<TileRef> result = new Long2ObjectOpenHashMap<>();
        for (ChunkPos chunkPos : List.copyOf(activeChunkPositions)) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            if (!(chunk instanceof IElementChunkAccessor accessor)) continue;
            ElementChunkData data = accessor.gyromancy$getElementData();
            if (data == null) continue;

            for (Int2ObjectMap.Entry<ElementTile> entry
                    : data.tiles().int2ObjectEntrySet()) {
                int key = entry.getIntKey();
                int tileX = ElementChunkData.worldTileX(chunkPos, key);
                int tileY = ElementChunkData.tileY(key);
                int tileZ = ElementChunkData.worldTileZ(chunkPos, key);
                result.put(tilePosKey(tileX, tileY, tileZ),
                        new TileRef(chunk, accessor, data, key, entry.getValue(),
                                tileX, tileY, tileZ));
            }
        }
        return result;
    }

    private static void decayAndRecover(ServerLevel level, TileRef ref) {
        ElementTile tile = ref.tile();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int wordIndex = 0; wordIndex < tile.activeWords().length; wordIndex++) {
            long word = tile.activeWords()[wordIndex];
            while (word != 0L) {
                int bit = Long.numberOfTrailingZeros(word);
                int index = (wordIndex << 6) + bit;
                setWorldPos(pos, ref, index);

                ElementConcentrations defaults =
                        ElementBiomeProvider.getDefault(level.getBiome(pos).value());
                boolean close = true;

                for (ElementType type : ELEMENTS) {
                    int element = type.ordinal();
                    long oldValue = tile.value(element, index);
                    long nextValue = ElementConcentrations.decayValue(
                            oldValue, defaults.values()[element]);
                    tile.setValue(element, index, nextValue);
                    tile.setDerivative(element, index, nextValue - oldValue);
                    if (!ElementConcentrations.isCloseValue(
                            nextValue, defaults.values()[element])) {
                        close = false;
                    }
                    fireEvent(level, pos, type, oldValue, nextValue);
                }

                if (close) {
                    if (ElementEventBus.hasCleanupListeners()) {
                        ElementEventBus.fireCleanup(level,
                                new ElementCleanedUpEvent(level, pos.immutable(),
                                        tile.concentrations(index)));
                    }
                    tile.remove(index);
                }
                word &= word - 1L;
            }
        }
        ref.data().removeEmptyTiles();
    }

    private static Long2ObjectOpenHashMap<TileDelta> computeFlux(
            ServerLevel level, Long2ObjectOpenHashMap<TileRef> tiles) {
        Long2ObjectOpenHashMap<TileDelta> deltas = new Long2ObjectOpenHashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();
        MutableCellRef neighbor = new MutableCellRef();

        for (TileRef ref : tiles.values()) {
            ElementTile tile = ref.tile();
            if (tile.isEmpty()) continue;

            for (int wordIndex = 0; wordIndex < tile.activeWords().length; wordIndex++) {
                long word = tile.activeWords()[wordIndex];
                while (word != 0L) {
                    int bit = Long.numberOfTrailingZeros(word);
                    int index = (wordIndex << 6) + bit;
                    setWorldPos(pos, ref, index);
                    ElementConcentrations defaultA =
                            ElementBiomeProvider.getDefault(level.getBiome(pos).value());

                    for (Direction direction : DIRECTIONS) {
                        neighborPos.setWithOffset(pos, direction);
                        if (!findLoadedCell(level, neighborPos, neighbor)) continue;
                        if (neighbor.active() && !isCanonicalEdge(pos, neighborPos)) continue;
                        if (tile.isBlocked(index, direction)) continue;

                        ElementConcentrations defaultB =
                                ElementBiomeProvider.getDefault(level.getBiome(neighborPos).value());

                        for (ElementType type : ELEMENTS) {
                            int element = type.ordinal();
                            long valueA = tile.value(element, index);
                            long valueB = neighbor.active()
                                    ? neighbor.tile().value(element, neighbor.index())
                                    : defaultB.values()[element];
                            long excessA = Math.max(0L, valueA - defaultA.values()[element]);
                            long excessB = Math.max(0L, valueB - defaultB.values()[element]);
                            long flow = computeFaceFlow(excessA, excessB);
                            if (flow == 0L) continue;

                            deltaFor(deltas, ref).add(element, index, -flow);
                            deltaFor(deltas, neighbor).add(
                                    element, neighbor.index(), flow);
                        }
                    }
                    word &= word - 1L;
                }
            }
        }
        return deltas;
    }

    private static void applyFlux(
            ServerLevel level,
            Long2ObjectOpenHashMap<TileDelta> deltas,
            Set<LevelChunk> touchedChunks) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (TileDelta delta : deltas.values()) {
            ElementChunkData data = delta.accessor().gyromancy$getElementData();
            if (data == null) continue;
            ElementTile tile = delta.tile();
            if (tile.barriersDirty()) {
                refreshBarriers(level, new TileRef(
                        delta.chunk(), delta.accessor(), data, delta.tileKey(), tile,
                        delta.tileX(), delta.tileY(), delta.tileZ()));
            }

            for (int wordIndex = 0; wordIndex < tile.pendingFluxWords().length; wordIndex++) {
                long word = tile.pendingFluxWords()[wordIndex];
                while (word != 0L) {
                    int bit = Long.numberOfTrailingZeros(word);
                    int index = (wordIndex << 6) + bit;
                    setWorldPos(pos, delta.tileX(), delta.tileY(), delta.tileZ(), index);
                    ElementConcentrations defaults =
                            ElementBiomeProvider.getDefault(level.getBiome(pos).value());

                    boolean wasActive = tile.isActive(index);
                    boolean atDefault = true;
                    for (ElementType type : ELEMENTS) {
                        int element = type.ordinal();
                        long current = wasActive
                                ? tile.value(element, index)
                                : defaults.values()[element];
                        long change = tile.pendingFlux(element, index);
                        long nextValue = clamp(current + change);
                        long derivative =
                                (wasActive ? tile.derivative(element, index) : 0L) + change;
                        tile.setValue(element, index, nextValue);
                        tile.setDerivative(element, index, derivative);
                        if (nextValue != defaults.values()[element]) atDefault = false;
                    }

                    // Flux is conservative. Do not apply the broad cleanup
                    // tolerance here or a newly spread edge would disappear
                    // in the same step and destroy mass.
                    if (atDefault) tile.remove(index);
                    word &= word - 1L;
                }
            }
            touchedChunks.add(delta.chunk());
        }
    }

    private static void finishChunks(Set<LevelChunk> chunks) {
        for (LevelChunk chunk : chunks) {
            if (!(chunk instanceof IElementChunkAccessor accessor)) continue;
            ElementChunkData data = accessor.gyromancy$getElementData();
            if (data == null) {
                ElementChunkEventHandler.markInactive(chunk);
                continue;
            }
            data.removeEmptyTiles();
            if (data.isEmpty()) {
                accessor.gyromancy$clearElementData();
                ElementChunkEventHandler.markInactive(chunk);
            } else {
                accessor.gyromancy$markElementDataDirty();
                ElementChunkEventHandler.markActive(chunk);
            }
        }
    }

    private static void refreshBarriers(ServerLevel level, TileRef ref) {
        ElementTile tile = ref.tile();
        tile.clearBlockedFaces();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean[] blockingCells = new boolean[ElementTile.CELL_COUNT];

        // Read each block in the tile once. Only the 6x8x8 boundary faces need
        // an additional world lookup.
        for (int index = 0; index < ElementTile.CELL_COUNT; index++) {
            setWorldPos(pos, ref, index);
            blockingCells[index] = ElementFlowBarrier.isBlockingBlock(level, pos);
        }

        for (int index = 0; index < ElementTile.CELL_COUNT; index++) {
            int x = ElementTile.localX(index);
            int y = ElementTile.localY(index);
            int z = ElementTile.localZ(index);
            setWorldPos(pos, ref, index);
            for (Direction direction : DIRECTIONS) {
                int neighborX = x + direction.getStepX();
                int neighborY = y + direction.getStepY();
                int neighborZ = z + direction.getStepZ();
                boolean neighborBlocks;
                if (neighborX >= 0 && neighborX < ElementTile.EDGE
                        && neighborY >= 0 && neighborY < ElementTile.EDGE
                        && neighborZ >= 0 && neighborZ < ElementTile.EDGE) {
                    neighborBlocks = blockingCells[
                            ElementTile.index(neighborX, neighborY, neighborZ)];
                } else {
                    neighborBlocks = ElementFlowBarrier.isBlockingBlock(
                            level, pos.relative(direction));
                }
                tile.setBlocked(index, direction,
                        blockingCells[index] || neighborBlocks);
            }
        }
        tile.finishBarrierRefresh();
    }

    private static boolean findLoadedCell(
            ServerLevel level, BlockPos pos, MutableCellRef result) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(
                pos.getX() >> 4, pos.getZ() >> 4);
        if (!(chunk instanceof IElementChunkAccessor accessor)) return false;
        ElementChunkData data = accessor.gyromancy$getElementData();
        int tileKey = ElementChunkData.tileKey(pos);
        ElementTile tile = data == null ? null : data.getTile(tileKey);
        int index = ElementChunkData.localIndex(pos);
        result.set(
                chunk, accessor, tileKey, tile,
                pos.getX() >> ElementTile.SHIFT,
                pos.getY() >> ElementTile.SHIFT,
                pos.getZ() >> ElementTile.SHIFT,
                index,
                tile != null && tile.isActive(index));
        return true;
    }

    private static TileDelta deltaFor(
            Long2ObjectOpenHashMap<TileDelta> deltas, TileRef ref) {
        long key = tilePosKey(ref.tileX(), ref.tileY(), ref.tileZ());
        TileDelta delta = deltas.get(key);
        if (delta != null) return delta;
        delta = new TileDelta(
                ref.chunk(), ref.accessor(), ref.tileKey(),
                ref.tile(), ref.tileX(), ref.tileY(), ref.tileZ());
        deltas.put(key, delta);
        return delta;
    }

    private static TileDelta deltaFor(
            Long2ObjectOpenHashMap<TileDelta> deltas, MutableCellRef ref) {
        long key = tilePosKey(ref.tileX(), ref.tileY(), ref.tileZ());
        TileDelta delta = deltas.get(key);
        if (delta != null) return delta;
        if (ref.tile() == null) {
            ElementChunkData data = ref.accessor().gyromancy$getElementData();
            if (data == null) data = ref.accessor().gyromancy$getOrCreateElementData();
            ref.tile = data.getOrCreateTile(ref.tileKey());
            ref.tile.clearPendingFlux();
        }
        delta = new TileDelta(
                ref.chunk(), ref.accessor(), ref.tileKey(),
                ref.tile(), ref.tileX(), ref.tileY(), ref.tileZ());
        deltas.put(key, delta);
        return delta;
    }

    private static boolean isCanonicalEdge(BlockPos a, BlockPos b) {
        if (a.getX() != b.getX()) return a.getX() < b.getX();
        if (a.getY() != b.getY()) return a.getY() < b.getY();
        return a.getZ() < b.getZ();
    }

    private static void setWorldPos(BlockPos.MutableBlockPos pos, TileRef ref, int index) {
        setWorldPos(pos, ref.tileX(), ref.tileY(), ref.tileZ(), index);
    }

    private static void setWorldPos(
            BlockPos.MutableBlockPos pos,
            int tileX, int tileY, int tileZ,
            int index) {
        pos.set(
                (tileX << ElementTile.SHIFT) + ElementTile.localX(index),
                (tileY << ElementTile.SHIFT) + ElementTile.localY(index),
                (tileZ << ElementTile.SHIFT) + ElementTile.localZ(index));
    }

    private static long tilePosKey(int tileX, int tileY, int tileZ) {
        return BlockPos.asLong(tileX, tileY, tileZ);
    }

    private static long clamp(long value) {
        return Math.max(ElementConcentrations.MIN_VALUE,
                Math.min(value, ElementConcentrations.MAX_VALUE));
    }

    private static void fireEvent(
            ServerLevel level,
            BlockPos pos,
            ElementType type,
            long oldValue,
            long nextValue) {
        long delta = nextValue - oldValue;
        if (delta == 0L) return;
        if (ElementEventBus.hasThresholdListeners(type)) {
            ElementEventBus.checkAndFireThreshold(level,
                    new ElementThresholdEvent(
                            level, pos.immutable(), type,
                            oldValue, nextValue, 300L,
                            delta > 0L
                                    ? ThresholdDirection.RISING_ABOVE
                                    : ThresholdDirection.FALLING_BELOW));
        }
        if (ElementEventBus.hasChangeListeners(type)) {
            ElementEventBus.checkAndFireChange(level,
                    new ElementChangeEvent(
                            level, pos.immutable(), type, delta, delta,
                            oldValue, nextValue));
        }
    }

    public static void onFirstWrite(
            ServerLevel level, BlockPos pos, ElementConcentrations values) {
        if (ElementEventBus.hasActivationListeners()) {
            ElementEventBus.fireActivation(level,
                    new ElementActivatedEvent(level, pos, values));
        }
    }

    private record TileRef(
            LevelChunk chunk,
            IElementChunkAccessor accessor,
            ElementChunkData data,
            int tileKey,
            ElementTile tile,
            int tileX,
            int tileY,
            int tileZ) {}

    private static final class MutableCellRef {
        private LevelChunk chunk;
        private IElementChunkAccessor accessor;
        private int tileKey;
        private ElementTile tile;
        private int tileX;
        private int tileY;
        private int tileZ;
        private int index;
        private boolean active;

        void set(
                LevelChunk chunk,
                IElementChunkAccessor accessor,
                int tileKey,
                ElementTile tile,
                int tileX,
                int tileY,
                int tileZ,
                int index,
                boolean active) {
            this.chunk = chunk;
            this.accessor = accessor;
            this.tileKey = tileKey;
            this.tile = tile;
            this.tileX = tileX;
            this.tileY = tileY;
            this.tileZ = tileZ;
            this.index = index;
            this.active = active;
        }

        LevelChunk chunk() { return chunk; }
        IElementChunkAccessor accessor() { return accessor; }
        int tileKey() { return tileKey; }
        ElementTile tile() { return tile; }
        int tileX() { return tileX; }
        int tileY() { return tileY; }
        int tileZ() { return tileZ; }
        int index() { return index; }
        boolean active() { return active; }
    }

    private static final class TileDelta {
        private final LevelChunk chunk;
        private final IElementChunkAccessor accessor;
        private final int tileKey;
        private final ElementTile tile;
        private final int tileX;
        private final int tileY;
        private final int tileZ;

        private TileDelta(
                LevelChunk chunk,
                IElementChunkAccessor accessor,
                int tileKey,
                ElementTile tile,
                int tileX,
                int tileY,
                int tileZ) {
            this.chunk = chunk;
            this.accessor = accessor;
            this.tileKey = tileKey;
            this.tile = tile;
            this.tileX = tileX;
            this.tileY = tileY;
            this.tileZ = tileZ;
        }

        void add(int element, int index, long delta) {
            tile.addPendingFlux(element, index, delta);
        }

        LevelChunk chunk() { return chunk; }
        IElementChunkAccessor accessor() { return accessor; }
        int tileKey() { return tileKey; }
        ElementTile tile() { return tile; }
        int tileX() { return tileX; }
        int tileY() { return tileY; }
        int tileZ() { return tileZ; }
    }
}
