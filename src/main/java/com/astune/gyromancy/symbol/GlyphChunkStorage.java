package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class GlyphChunkStorage {

    private GlyphChunkStorage() {}

    public static void store(ServerLevel level, PositionedGlyph glyph) {
        for (ChunkPos pos : touchedChunks(glyph)) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) continue;
            var glyphs = new HashMap<>(chunk.getData(ModAttachments.CHUNK_GLYPHS.get()));
            glyphs.put(glyph.glyphUuid(), glyph);
            chunk.setData(ModAttachments.CHUNK_GLYPHS.get(), glyphs);
        }
    }

    public static void load(ServerLevel level, LevelChunk chunk) {
        if (!chunk.hasData(ModAttachments.CHUNK_GLYPHS.get())) return;

        for (PositionedGlyph stored : chunk.getData(ModAttachments.CHUNK_GLYPHS.get()).values()) {
            level.getData(ModAttachments.ARRAY_MANAGER).restoreGlyph(stored);
        }
    }

    public static void remove(ServerLevel level, PositionedGlyph glyph) {
        for (ChunkPos pos : touchedChunks(glyph)) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) continue;
            if (!chunk.hasData(ModAttachments.CHUNK_GLYPHS.get())) continue;

            var glyphs = new HashMap<>(chunk.getData(ModAttachments.CHUNK_GLYPHS.get()));
            glyphs.remove(glyph.glyphUuid());
            if (glyphs.isEmpty()) chunk.removeData(ModAttachments.CHUNK_GLYPHS.get());
            else chunk.setData(ModAttachments.CHUNK_GLYPHS.get(), glyphs);
        }
    }

    public static Set<PositionedGlyph> touching(LevelChunk chunk, BlockPos blockPos) {
        if (!chunk.hasData(ModAttachments.CHUNK_GLYPHS.get())) return Set.of();
        return chunk.getData(ModAttachments.CHUNK_GLYPHS.get()).values().stream()
                .filter(g -> g.pixels().stream().map(PixelPos::pos).anyMatch(blockPos::equals))
                .collect(Collectors.toSet());
    }

    private static Set<ChunkPos> touchedChunks(PositionedGlyph glyph) {
        return glyph.pixels().stream()
                .map(p -> new ChunkPos(p.pos()))
                .collect(Collectors.toSet());
    }
}
