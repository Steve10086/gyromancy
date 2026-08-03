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
    private final Set<UUID> claimedCircleCompilations = new HashSet<>();
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
            mgr.claimedCircleCompilations.add(arr.rootCircleGlyph().glyphUuid());
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
        for (UUID circleId : data.claimedCircleCompilations()) {
            PositionedGlyph circle = mgr.getGlyph(circleId);
            if (circle != null && circle.role() == SymbolRole.OUTER_CIRCLE) {
                mgr.claimedCircleCompilations.add(circleId);
            }
        }
        for (ArrayObject array : data.arrays()) {
            mgr.claimedCircleCompilations.add(array.rootCircleGlyph().glyphUuid());
        }
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
                relations,
                claimedCircleCompilations.stream()
                        .sorted(Comparator.comparing(UUID::toString))
                        .toList());
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
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            int id = nextGlyphId;
            nextGlyphId = nextGlyphId == Integer.MAX_VALUE ? 1 : nextGlyphId + 1;
            if (!glyphIdIndex.containsKey(id)) return id;
        }
        throw new IllegalStateException("No free glyph ids");
    }

    public void registerGlyph(PositionedGlyph glyph) {
        PositionedGlyph previous = glyphIndex.get(glyph.glyphUuid());
        if (previous != null && previous.glyphId() != glyph.glyphId()) {
            glyphIdIndex.remove(previous.glyphId());
        }
        glyphIndex.put(glyph.glyphUuid(), glyph);
        glyphIdIndex.put(glyph.glyphId(), glyph.glyphUuid());
        rebuildHierarchyFromGeometry();
    }

    /**
     * Replaces a glyph's geometry without tearing down an already running
     * array. Used when a canvas changes raster resolution while retaining the
     * exact same normalized strokes.
     */
    public void refreshGlyph(PositionedGlyph glyph) {
        registerGlyph(glyph);
        for (Map.Entry<UUID, ArrayObject> entry : new ArrayList<>(activeArrays.entrySet())) {
            ArrayObject array = entry.getValue();
            boolean changed = array.rootCircleGlyph().glyphUuid().equals(glyph.glyphUuid())
                    || array.boundGlyphs().stream()
                    .anyMatch(bound -> bound.glyphUuid().equals(glyph.glyphUuid()));
            if (!changed) continue;

            PositionedGlyph root = array.rootCircleGlyph().glyphUuid().equals(glyph.glyphUuid())
                    ? glyph : array.rootCircleGlyph();
            List<PositionedGlyph> bound = array.boundGlyphs().stream()
                    .map(existing -> existing.glyphUuid().equals(glyph.glyphUuid()) ? glyph : existing)
                    .toList();
            activeArrays.put(entry.getKey(), new ArrayObject(
                    array.arrayId(), root, bound,
                    array.compilationEffectEndTick(), array.scratchData()));
        }
    }

    public PositionedGlyph restoreGlyph(PositionedGlyph stored) {
        PositionedGlyph existing = getGlyph(stored.glyphUuid());
        if (existing != null) return existing;

        int id = stored.glyphId();
        PositionedGlyph glyph = id > 0 && !glyphIdIndex.containsKey(id)
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
        if (removed != null && removed.role() == SymbolRole.OUTER_CIRCLE) {
            claimedCircleCompilations.remove(removed.glyphUuid());
        }
        if (removed != null) detachGlyph(removed);
        return removed;
    }

    public PositionedGlyph unregisterGlyph(UUID uuid) {
        PositionedGlyph glyph = glyphIndex.remove(uuid);
        if (glyph != null) glyphIdIndex.remove(glyph.glyphId());
        if (glyph != null && glyph.role() == SymbolRole.OUTER_CIRCLE) {
            claimedCircleCompilations.remove(glyph.glyphUuid());
        }
        if (glyph != null) detachGlyph(glyph);
        return glyph;
    }

    /**
     * Claims the single compilation opportunity belonging to this circle
     * glyph's current lifetime.
     */
    public boolean claimCircleCompilation(UUID circleGlyphId) {
        PositionedGlyph glyph = glyphIndex.get(circleGlyphId);
        if (glyph == null || glyph.role() != SymbolRole.OUTER_CIRCLE) return false;
        return claimedCircleCompilations.add(circleGlyphId);
    }

    public boolean hasClaimedCircleCompilation(UUID circleGlyphId) {
        return claimedCircleCompilations.contains(circleGlyphId);
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
        return glyph.bounds().area();
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
        var bounds = glyph.boundsOn(circle.surface());
        double x = bounds.centerU();
        double y = bounds.centerV();
        return x >= circle.minWorldX() && x <= circle.maxWorldX()
                && y >= circle.minWorldY() && y <= circle.maxWorldY();
    }

    private static boolean containsNode(PositionedGlyph circle, PositionedGlyph glyph) {
        if (glyph.role() != SymbolRole.OUTER_CIRCLE) return containsCenter(circle, glyph);
        var bounds = glyph.boundsOn(circle.surface());
        return bounds.minU() >= circle.minWorldX() && bounds.maxU() <= circle.maxWorldX()
                && bounds.minV() >= circle.minWorldY() && bounds.maxV() <= circle.maxWorldY();
    }

    private static boolean sameSurface(PositionedGlyph a, PositionedGlyph b) {
        return a.surface().isCoplanar(b.surface());
    }

    // ═══════════════════ phrase5 array objects ═══════════════════

    public void registerArrayObj(ArrayObject arr) {
        claimedCircleCompilations.add(arr.rootCircleGlyph().glyphUuid());
        ArrayObject previous = activeArrays.put(arr.arrayId(), arr);
        if (previous != null) {
            for (PositionedGlyph pg : previous.allBoundGlyphs()) {
                glyphToArray.remove(pg.glyphUuid(), arr.arrayId());
            }
        }
        for (PositionedGlyph pg : arr.allBoundGlyphs()) {
            glyphToArray.put(pg.glyphUuid(), arr.arrayId());
        }
    }

    public void unregisterArrayObj(UUID arrayId) {
        ArrayObject arr = activeArrays.remove(arrayId);
        if (arr != null) {
            for (PositionedGlyph pg : arr.allBoundGlyphs()) {
                glyphToArray.remove(pg.glyphUuid(), arrayId);
            }
        }
    }

    public ArrayObject getArrayForGlyph(UUID glyphUuid) {
        UUID arrayId = glyphToArray.get(glyphUuid);
        return arrayId != null ? activeArrays.get(arrayId) : null;
    }

    public ArrayObject getArrayObj(UUID arrayId) {
        return activeArrays.get(arrayId);
    }

    public Collection<ArrayObject> getAllArrayObjs() {
        return Collections.unmodifiableCollection(activeArrays.values());
    }

    public List<ArrayObject> getArrayObjsForGlyph(UUID glyphUuid) {
        return activeArrays.values().stream()
                .filter(array -> array.allBoundGlyphs().stream()
                        .anyMatch(glyph -> glyph.glyphUuid().equals(glyphUuid)))
                .toList();
    }

    public List<ArrayObject> getArrayObjsForRoot(UUID rootGlyphUuid) {
        return activeArrays.values().stream()
                .filter(array -> array.rootCircleGlyph().glyphUuid().equals(rootGlyphUuid))
                .toList();
    }

    /**
     * Finds every active array which depends on a glyph supplied by a canvas.
     * This deliberately does not use the single-value glyph index because an
     * interrupted save can preserve more than one runtime for the same glyph.
     */
    public List<ArrayObject> getArrayObjsForCanvas(UUID canvasId) {
        return activeArrays.values().stream()
                .filter(array -> array.allBoundGlyphs().stream()
                        .anyMatch(glyph -> glyph.sourceCanvasId()
                                .filter(canvasId::equals).isPresent()))
                .toList();
    }

    /** Finds registered world glyphs owned by a canvas without consulting its document cache. */
    public List<PositionedGlyph> getGlyphsForCanvas(UUID canvasId) {
        return glyphIndex.values().stream()
                .filter(glyph -> glyph.sourceCanvasId()
                        .filter(canvasId::equals).isPresent())
                .toList();
    }

    public void setArrayScratchData(UUID arrayId, Map<String, Object> data) {
        ArrayObject existing = activeArrays.get(arrayId);
        if (existing != null) {
            activeArrays.put(arrayId, new ArrayObject(
                    existing.arrayId(), existing.rootCircleGlyph(), existing.boundGlyphs(),
                    existing.compilationEffectEndTick(), data));
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
            List<ParentRelation> parentRelations,
            List<UUID> claimedCircleCompilations
    ) {
        private static final Codec<PersistentData> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                                ArrayObject.CODEC.listOf().fieldOf("arrays").forGetter(PersistentData::arrays),
                                PositionedGlyph.CODEC.listOf().fieldOf("glyphs").forGetter(PersistentData::glyphs),
                                ParentRelation.CODEC.listOf().fieldOf("parent_relations")
                                        .forGetter(PersistentData::parentRelations),
                                UUID_CODEC.listOf().optionalFieldOf(
                                                "claimed_circle_compilations", List.of())
                                        .forGetter(PersistentData::claimedCircleCompilations))
                        .apply(instance, PersistentData::new));
    }
}
