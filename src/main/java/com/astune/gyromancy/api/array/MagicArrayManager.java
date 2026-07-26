package com.astune.gyromancy.api.array;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpDefinitionRegistry;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/**
 * Tracks all active magic arrays within a single dimension.
 * Stored as an Attachment on {@code Level} via {@code ModAttachments.ARRAY_MANAGER}.
 */
public class MagicArrayManager {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    /**
     * The right side accepts the legacy attachment format, which was just a list
     * of active arrays and therefore could not preserve hierarchy ownership.
     */
    public static final Codec<MagicArrayManager> CODEC = Codec.either(
                    PersistentData.CODEC,
                    ArrayObject.CODEC.listOf())
            .xmap(data -> data.map(MagicArrayManager::fromPersistentData,
                            MagicArrayManager::fromPersistentArrays),
                    manager -> Either.left(manager.toPersistentData()));

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

    private static MagicArrayManager fromPersistentData(PersistentData data) {
        MagicArrayManager mgr = new MagicArrayManager();
        for (PositionedGlyph glyph : data.glyphs()) {
            mgr.registerGlyph(glyph);
        }
        for (ArrayObject array : data.arrays()) {
            mgr.registerArrayObj(array);
            for (PositionedGlyph glyph : array.allBoundGlyphs()) {
                if (mgr.getGlyph(glyph.glyphUuid()) == null) mgr.registerGlyph(glyph);
            }
        }
        mgr.restoreParentRelations(data.parentRelations());
        return mgr;
    }

    private PersistentData toPersistentData() {
        List<ParentRelation> relations = parentCircleByGlyph.entrySet().stream()
                .map(entry -> new ParentRelation(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(relation -> relation.child().toString()))
                .toList();
        return new PersistentData(
                List.copyOf(activeArrays.values()),
                List.copyOf(glyphIndex.values()),
                relations);
    }

    private void restoreParentRelations(List<ParentRelation> relations) {
        parentCircleByGlyph.clear();
        directChildrenByCircle.clear();
        for (ParentRelation relation : relations) {
            PositionedGlyph child = glyphIndex.get(relation.child());
            PositionedGlyph parent = glyphIndex.get(relation.parent());
            if (child == null || parent == null || child == parent
                    || parent.role() != SymbolRole.OUTER_CIRCLE) {
                continue;
            }
            assignParent(child, parent);
        }
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
        rebuildHierarchyFromGeometry();
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

    /**
     * Geometry is only a runtime fallback for newly discovered glyphs and
     * legacy saves. New-format saves replace this result with their explicit
     * parent relations after all glyphs have been restored.
     */
    private void rebuildHierarchyFromGeometry() {
        parentCircleByGlyph.clear();
        directChildrenByCircle.clear();
        glyphIndex.values().stream()
                .filter(glyph -> glyph.role() == SymbolRole.OUTER_CIRCLE)
                .sorted(Comparator.comparingDouble(MagicArrayManager::boundsArea)
                        .thenComparingInt(PositionedGlyph::glyphId))
                .forEach(this::claimUnparentedChildren);
    }

    private static double boundsArea(PositionedGlyph glyph) {
        return Math.max(0.0, glyph.maxWorldX() - glyph.minWorldX())
                * Math.max(0.0, glyph.maxWorldY() - glyph.minWorldY());
    }

    private void assignParent(PositionedGlyph glyph, PositionedGlyph parent) {
        if (glyph.glyphUuid().equals(parent.glyphUuid())
                || wouldCreateParentCycle(glyph.glyphUuid(), parent.glyphUuid())) {
            return;
        }
        UUID previousParent = parentCircleByGlyph.put(glyph.glyphUuid(), parent.glyphUuid());
        if (previousParent != null) {
            List<PositionedGlyph> previousSiblings = directChildrenByCircle.get(previousParent);
            if (previousSiblings != null) {
                previousSiblings.removeIf(child -> child.glyphUuid().equals(glyph.glyphUuid()));
                if (previousSiblings.isEmpty()) directChildrenByCircle.remove(previousParent);
            }
        }
        List<PositionedGlyph> children =
                directChildrenByCircle.computeIfAbsent(parent.glyphUuid(), ignored -> new ArrayList<>());
        if (children.stream().noneMatch(child -> child.glyphUuid().equals(glyph.glyphUuid()))) {
            children.add(glyph);
        }
        directChildrenByCircle.get(parent.glyphUuid()).sort(Comparator.comparingInt(PositionedGlyph::glyphId));
    }

    private boolean wouldCreateParentCycle(UUID child, UUID parent) {
        UUID current = parent;
        Set<UUID> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            if (current.equals(child)) return true;
            current = parentCircleByGlyph.get(current);
        }
        return current != null;
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

    private record ParentRelation(UUID child, UUID parent) {
        private static final Codec<ParentRelation> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                                UUID_CODEC.fieldOf("child").forGetter(ParentRelation::child),
                                UUID_CODEC.fieldOf("parent").forGetter(ParentRelation::parent))
                        .apply(instance, ParentRelation::new));
    }

    private record PersistentData(
            List<ArrayObject> arrays,
            List<PositionedGlyph> glyphs,
            List<ParentRelation> parentRelations
    ) {
        private static final Codec<PersistentData> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                                ArrayObject.CODEC.listOf().fieldOf("arrays").forGetter(PersistentData::arrays),
                                PositionedGlyph.CODEC.listOf().fieldOf("glyphs").forGetter(PersistentData::glyphs),
                                ParentRelation.CODEC.listOf().fieldOf("parent_relations")
                                        .forGetter(PersistentData::parentRelations))
                        .apply(instance, PersistentData::new));
    }
}
