package com.astune.gyromancy.api.array;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpDefinitionRegistry;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/**
 * Tracks all active magic arrays within a single dimension.
 * Stored as an Attachment on {@code Level} via {@code ModAttachments.ARRAY_MANAGER}.
 */
public class MagicArrayManager {
    public static final Codec<MagicArrayManager> CODEC = ArrayObject.CODEC.listOf()
            .xmap(MagicArrayManager::fromPersistentArrays, mgr -> List.copyOf(mgr.activeArrays.values()));

    private final Map<UUID, MagicArrayState> arrays = new HashMap<>();
    private final Map<BlockPos, UUID> positionIndex = new HashMap<>();
    private final Map<UUID, PositionedGlyph> glyphIndex = new LinkedHashMap<>();
    private final Map<Integer, UUID> glyphIdIndex = new HashMap<>();
    private final Map<UUID, UUID> parentCircleByGlyph = new HashMap<>();
    private final Map<UUID, List<PositionedGlyph>> directChildrenByCircle = new HashMap<>();
    private final List<OpDefinition> opDefinitions;
    private int nextGlyphId = 1;

    // ── phrase5 array-object tracking ──
    private final Map<UUID, ArrayObject> activeArrays = new HashMap<>();
    private final Map<UUID, UUID> glyphToArray = new HashMap<>(); // glyphUuid → arrayId

    public MagicArrayManager() {
        this.opDefinitions = null;
    }

    public MagicArrayManager(List<? extends OpDefinition> opDefinitions) {
        this.opDefinitions = List.copyOf(opDefinitions);
    }

    private static MagicArrayManager fromPersistentArrays(List<ArrayObject> arrays) {
        MagicArrayManager mgr = new MagicArrayManager();
        for (ArrayObject arr : arrays) {
            mgr.registerArrayObj(arr);
            for (PositionedGlyph glyph : arr.allBoundGlyphs()) {
                mgr.registerGlyph(glyph);
            }
        }
        return mgr;
    }

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
        if (glyph.role() == SymbolRole.OUTER_CIRCLE) claimUnparentedChildren(glyph);
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
        PositionedGlyph removed = uuid == null ? null : glyphIndex.remove(uuid);
        if (removed != null) detachGlyph(removed);
        return removed;
    }

    public PositionedGlyph unregisterGlyph(UUID uuid) {
        PositionedGlyph glyph = glyphIndex.remove(uuid);
        if (glyph != null) glyphIdIndex.remove(glyph.glyphId());
        if (glyph != null) detachGlyph(glyph);
        return glyph;
    }

    public Collection<PositionedGlyph> getAllGlyphs() {
        return Collections.unmodifiableCollection(glyphIndex.values());
    }

    public Map<UUID, PositionedGlyph> getGlyphIndex() {
        return glyphIndex;
    }

    public List<OpDefinition> opDefinitions() {
        return opDefinitions != null ? opDefinitions : OpDefinitionRegistry.definitions();
    }

    @Deprecated(forRemoval = false)
    public List<OpDefinition> effectDefinitions() {
        return opDefinitions();
    }

    public PositionedGlyph parentCircle(PositionedGlyph glyph) {
        UUID parent = parentCircleByGlyph.get(glyph.glyphUuid());
        return parent == null ? null : glyphIndex.get(parent);
    }

    public List<PositionedGlyph> directChildren(PositionedGlyph circle) {
        return directChildrenByCircle.getOrDefault(circle.glyphUuid(), List.of());
    }

    public List<PositionedGlyph> ancestorCircles(PositionedGlyph glyph) {
        List<PositionedGlyph> ancestors = new ArrayList<>();
        PositionedGlyph current = glyph;
        while ((current = parentCircle(current)) != null) {
            ancestors.add(current);
        }
        return ancestors;
    }

    private void claimUnparentedChildren(PositionedGlyph circle) {
        for (PositionedGlyph glyph : glyphIndex.values()) {
            if (glyph.glyphUuid().equals(circle.glyphUuid())) continue;
            if (parentCircleByGlyph.containsKey(glyph.glyphUuid())) continue;
            if (sameSurface(circle, glyph) && containsNode(circle, glyph)) assignParent(glyph, circle);
        }
    }

    private void assignParent(PositionedGlyph glyph, PositionedGlyph parent) {
        parentCircleByGlyph.put(glyph.glyphUuid(), parent.glyphUuid());
        directChildrenByCircle.computeIfAbsent(parent.glyphUuid(), ignored -> new ArrayList<>()).add(glyph);
        directChildrenByCircle.get(parent.glyphUuid()).sort(Comparator.comparingInt(PositionedGlyph::glyphId));
    }

    private void detachGlyph(PositionedGlyph glyph) {
        UUID parent = parentCircleByGlyph.remove(glyph.glyphUuid());
        if (parent != null) {
            List<PositionedGlyph> siblings = directChildrenByCircle.get(parent);
            if (siblings != null) siblings.removeIf(child -> child.glyphUuid().equals(glyph.glyphUuid()));
        }
        if (glyph.role() == SymbolRole.OUTER_CIRCLE) {
            List<PositionedGlyph> children = directChildrenByCircle.remove(glyph.glyphUuid());
            if (children != null) {
                for (PositionedGlyph child : children) parentCircleByGlyph.remove(child.glyphUuid());
            }
        }
    }

    private static boolean containsCenter(PositionedGlyph circle, PositionedGlyph glyph) {
        double x = (glyph.minWorldX() + glyph.maxWorldX()) * 0.5;
        double y = (glyph.minWorldY() + glyph.maxWorldY()) * 0.5;
        return x >= circle.minWorldX() && x <= circle.maxWorldX()
                && y >= circle.minWorldY() && y <= circle.maxWorldY();
    }

    private static boolean containsNode(PositionedGlyph circle, PositionedGlyph glyph) {
        if (glyph.role() != SymbolRole.OUTER_CIRCLE) return containsCenter(circle, glyph);
        return glyph.minWorldX() >= circle.minWorldX() && glyph.maxWorldX() <= circle.maxWorldX()
                && glyph.minWorldY() >= circle.minWorldY() && glyph.maxWorldY() <= circle.maxWorldY();
    }

    private static boolean sameSurface(PositionedGlyph a, PositionedGlyph b) {
        if (a.pixels().isEmpty() || b.pixels().isEmpty()) return true;
        var ap = a.pixels().iterator().next();
        var bp = b.pixels().iterator().next();
        return ap.face() == bp.face() && Double.compare(surfaceCoordinate(ap), surfaceCoordinate(bp)) == 0;
    }

    private static double surfaceCoordinate(com.astune.gyromancy.api.symbol.PixelPos pixel) {
        return switch (pixel.face()) {
            case NORTH -> pixel.pos().getZ();
            case SOUTH -> pixel.pos().getZ() + 1.0;
            case WEST -> pixel.pos().getX();
            case EAST -> pixel.pos().getX() + 1.0;
            case DOWN -> pixel.pos().getY();
            case UP -> pixel.pos().getY() + 1.0;
        };
    }

    // ═══════════════════ phrase5 array objects ═══════════════════

    public void registerArrayObj(ArrayObject arr) {
        activeArrays.put(arr.arrayId(), arr);
        for (PositionedGlyph pg : arr.allBoundGlyphs()) {
            glyphToArray.put(pg.glyphUuid(), arr.arrayId());
        }
    }

    public void unregisterArrayObj(UUID arrayId) {
        ArrayObject arr = activeArrays.remove(arrayId);
        if (arr != null) {
            for (PositionedGlyph pg : arr.allBoundGlyphs()) {
                glyphToArray.remove(pg.glyphUuid());
            }
        }
    }

    public ArrayObject getArrayForGlyph(UUID glyphUuid) {
        UUID arrayId = glyphToArray.get(glyphUuid);
        return arrayId != null ? activeArrays.get(arrayId) : null;
    }

    public Collection<ArrayObject> getAllArrayObjs() {
        return Collections.unmodifiableCollection(activeArrays.values());
    }

    public void setArrayScratchData(UUID arrayId, Map<String, Object> data) {
        ArrayObject existing = activeArrays.get(arrayId);
        if (existing != null) {
            activeArrays.put(arrayId, new ArrayObject(
                    existing.arrayId(), existing.rootCircleGlyph(), existing.boundGlyphs(), data));
        }
    }

    public void setArrayScratchValue(UUID arrayId, String key, Object value) {
        ArrayObject existing = activeArrays.get(arrayId);
        if (existing == null) return;

        Map<String, Object> data = new HashMap<>(existing.scratchData());
        data.put(key, value);
        setArrayScratchData(arrayId, data);
    }
}
