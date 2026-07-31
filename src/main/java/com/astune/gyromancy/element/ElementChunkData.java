package com.astune.gyromancy.element;

import com.astune.gyromancy.api.element.ElementConcentrations;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Per-chunk runtime container for exact 8x8x8 element tiles.
 *
 * <p>The codec accepts the former BlockPos map format so existing worlds are
 * migrated when their chunks are next saved.</p>
 */
public final class ElementChunkData {

    private static final Codec<ElementTile.CellData> CELL_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("index").forGetter(ElementTile.CellData::index),
                    Codec.INT.listOf().fieldOf("values").forGetter(ElementTile.CellData::values)
            ).apply(instance, ElementTile.CellData::new));

    private static final Codec<TileData> TILE_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("key").forGetter(TileData::key),
                    CELL_CODEC.listOf().fieldOf("cells").forGetter(TileData::cells)
            ).apply(instance, TileData::new));

    private static final Codec<ElementChunkData> TILE_DATA_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    TILE_CODEC.listOf().fieldOf("tiles").forGetter(ElementChunkData::serializedTiles)
            ).apply(instance, ElementChunkData::fromSerializedTiles));

    public static final Codec<ElementChunkData> CODEC =
            Codec.either(TILE_DATA_CODEC, ElementConcentrations.mapCodec())
                    .xmap(
                            either -> either.map(data -> data, ElementChunkData::fromLegacy),
                            data -> Either.left(data)
                    );

    private final Int2ObjectOpenHashMap<ElementTile> tiles = new Int2ObjectOpenHashMap<>();

    public ElementTile getTile(int tileKey) {
        return tiles.get(tileKey);
    }

    public ElementTile getOrCreateTile(int tileKey) {
        ElementTile tile = tiles.get(tileKey);
        if (tile != null) return tile;
        tile = new ElementTile();
        tiles.put(tileKey, tile);
        return tile;
    }

    public ElementTile getTile(BlockPos pos) {
        return tiles.get(tileKey(pos));
    }

    public ElementTile getOrCreateTile(BlockPos pos) {
        return getOrCreateTile(tileKey(pos));
    }

    public Int2ObjectMap<ElementTile> tiles() {
        return tiles;
    }

    public boolean has(BlockPos pos) {
        ElementTile tile = getTile(pos);
        return tile != null && tile.isActive(localIndex(pos));
    }

    public ElementConcentrations get(BlockPos pos) {
        ElementTile tile = getTile(pos);
        int index = localIndex(pos);
        return tile != null && tile.isActive(index) ? tile.concentrations(index) : null;
    }

    public void set(BlockPos pos, ElementConcentrations concentrations) {
        getOrCreateTile(pos).set(localIndex(pos),
                concentrations.values(), concentrations.derivatives());
    }

    public boolean remove(BlockPos pos) {
        int key = tileKey(pos);
        ElementTile tile = tiles.get(key);
        if (tile == null || !tile.isActive(localIndex(pos))) return false;
        tile.remove(localIndex(pos));
        if (tile.isEmpty()) tiles.remove(key);
        return true;
    }

    public void removeEmptyTiles() {
        tiles.int2ObjectEntrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public boolean isEmpty() {
        return tiles.isEmpty();
    }

    public int activeCellCount() {
        int count = 0;
        for (ElementTile tile : tiles.values()) count += tile.activeCount();
        return count;
    }

    /** Iterates active cells for debug/network export, not for the hot solver path. */
    public void forEach(ChunkPos chunkPos,
                        BiConsumer<BlockPos, ElementConcentrations> consumer) {
        for (Int2ObjectMap.Entry<ElementTile> entry : tiles.int2ObjectEntrySet()) {
            int key = entry.getIntKey();
            ElementTile tile = entry.getValue();
            int originX = worldTileX(chunkPos, key) << ElementTile.SHIFT;
            int originY = tileY(key) << ElementTile.SHIFT;
            int originZ = worldTileZ(chunkPos, key) << ElementTile.SHIFT;
            for (int wordIndex = 0; wordIndex < tile.activeWords().length; wordIndex++) {
                long word = tile.activeWords()[wordIndex];
                while (word != 0L) {
                    int bit = Long.numberOfTrailingZeros(word);
                    int index = (wordIndex << 6) + bit;
                    consumer.accept(new BlockPos(
                                    originX + ElementTile.localX(index),
                                    originY + ElementTile.localY(index),
                                    originZ + ElementTile.localZ(index)),
                            tile.concentrations(index));
                    word &= word - 1L;
                }
            }
        }
    }

    public static int tileKey(BlockPos pos) {
        return tileKey((pos.getX() & 15) >> ElementTile.SHIFT,
                pos.getY() >> ElementTile.SHIFT,
                (pos.getZ() & 15) >> ElementTile.SHIFT);
    }

    public static int tileKey(int localTileX, int tileY, int localTileZ) {
        return (tileY << 2) | ((localTileZ & 1) << 1) | (localTileX & 1);
    }

    public static int tileY(int tileKey) {
        return tileKey >> 2;
    }

    public static int localTileX(int tileKey) {
        return tileKey & 1;
    }

    public static int localTileZ(int tileKey) {
        return (tileKey >> 1) & 1;
    }

    public static int localIndex(BlockPos pos) {
        return ElementTile.index(pos.getX(), pos.getY(), pos.getZ());
    }

    public static int worldTileX(ChunkPos chunkPos, int tileKey) {
        return (chunkPos.x << 1) + localTileX(tileKey);
    }

    public static int worldTileZ(ChunkPos chunkPos, int tileKey) {
        return (chunkPos.z << 1) + localTileZ(tileKey);
    }

    private List<TileData> serializedTiles() {
        List<TileData> result = new ArrayList<>(tiles.size());
        for (Int2ObjectMap.Entry<ElementTile> entry : tiles.int2ObjectEntrySet()) {
            if (!entry.getValue().isEmpty()) {
                result.add(new TileData(entry.getIntKey(), entry.getValue().serializedCells()));
            }
        }
        return result;
    }

    private static ElementChunkData fromSerializedTiles(List<TileData> serialized) {
        ElementChunkData data = new ElementChunkData();
        for (TileData tile : serialized) {
            ElementTile decoded = ElementTile.fromSerializedCells(tile.cells());
            if (!decoded.isEmpty()) data.tiles.put(tile.key(), decoded);
        }
        return data;
    }

    private static ElementChunkData fromLegacy(HashMap<BlockPos, ElementConcentrations> legacy) {
        ElementChunkData data = new ElementChunkData();
        for (Map.Entry<BlockPos, ElementConcentrations> entry : legacy.entrySet()) {
            data.set(entry.getKey(), entry.getValue());
        }
        return data;
    }

    private record TileData(int key, List<ElementTile.CellData> cells) {}
}
