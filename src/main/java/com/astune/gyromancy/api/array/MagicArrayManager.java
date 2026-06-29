package com.astune.gyromancy.api.array;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractedGlyph;
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
    private final Map<UUID, PositionedGlyph> glyphIndex = new LinkedHashMap<>();
    private final Map<Integer, UUID> glyphIdIndex = new HashMap<>();
    private int nextGlyphId = 1;

    public MagicArrayManager() {}

    // ═══════════════════ arrays ═══════════════════

    public void registerArray(MagicArrayState state) {
        arrays.put(state.getArrayId(), state);
        positionIndex.put(state.getCanvasPos(), state.getArrayId());
    }

    public void unregisterArray(UUID arrayId) {
        MagicArrayState state = arrays.remove(arrayId);
        if (state != null) positionIndex.remove(state.getCanvasPos());
    }

    public Optional<MagicArrayState> getArrayAt(BlockPos pos) {
        UUID id = positionIndex.get(pos);
        return id != null ? Optional.ofNullable(arrays.get(id)) : Optional.empty();
    }

    public Optional<MagicArrayState> getArray(UUID id) {
        return Optional.ofNullable(arrays.get(id));
    }

    public Collection<MagicArrayState> getAllArrays() {
        return Collections.unmodifiableCollection(arrays.values());
    }

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

    public int getActiveCount() { return arrays.size(); }

    // ═══════════════════ glyphs ═══════════════════

    public int nextGlyphId() {
        // ponytail: canvas effect layers are byte-backed; widen storage if >255 active glyphs matters.
        for (int i = 0; i < 255; i++) {
            int id = nextGlyphId;
            nextGlyphId = nextGlyphId % 255 + 1;
            if (!glyphIdIndex.containsKey(id)) return id;
        }
        throw new IllegalStateException("No free glyph ids");
    }

    public void registerGlyph(PositionedGlyph glyph) {
        glyphIndex.put(glyph.glyphUuid(), glyph);
        glyphIdIndex.put(glyph.glyphId(), glyph.glyphUuid());
    }

    public PositionedGlyph restoreGlyph(PositionedGlyph stored) {
        PositionedGlyph existing = getGlyph(stored.glyphUuid());
        if (existing != null) return existing;

        int id = stored.glyphId();
        PositionedGlyph glyph = id > 0 && id <= 255 && !glyphIdIndex.containsKey(id)
                ? stored
                : stored.withGlyphId(nextGlyphId());
        registerGlyph(glyph);
        return glyph;
    }

    public PositionedGlyph getGlyph(int id) {
        UUID uuid = glyphIdIndex.get(id);
        return uuid == null ? null : glyphIndex.get(uuid);
    }

    public PositionedGlyph getGlyph(UUID uuid) {
        return glyphIndex.get(uuid);
    }

    public PositionedGlyph unregisterGlyph(int id) {
        UUID uuid = glyphIdIndex.remove(id);
        return uuid == null ? null : glyphIndex.remove(uuid);
    }

    public PositionedGlyph unregisterGlyph(UUID uuid) {
        PositionedGlyph glyph = glyphIndex.remove(uuid);
        if (glyph != null) glyphIdIndex.remove(glyph.glyphId());
        return glyph;
    }

    public Collection<PositionedGlyph> getAllGlyphs() {
        return Collections.unmodifiableCollection(glyphIndex.values());
    }

    public Map<UUID, PositionedGlyph> getGlyphIndex() {
        return glyphIndex;
    }
}
