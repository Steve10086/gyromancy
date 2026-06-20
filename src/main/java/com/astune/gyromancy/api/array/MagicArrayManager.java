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
    private final Map<Integer, PositionedGlyph> glyphIndex = new LinkedHashMap<>();

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

    public void registerGlyph(PositionedGlyph glyph) {
        glyphIndex.put(glyph.glyphId(), glyph);
    }

    public PositionedGlyph getGlyph(int id) {
        return glyphIndex.get(id);
    }

    public Collection<PositionedGlyph> getAllGlyphs() {
        return Collections.unmodifiableCollection(glyphIndex.values());
    }

    public Map<Integer, PositionedGlyph> getGlyphIndex() {
        return glyphIndex;
    }
}
