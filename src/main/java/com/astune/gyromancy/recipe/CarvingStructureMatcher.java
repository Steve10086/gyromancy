package com.astune.gyromancy.recipe;

import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.canvas.CanvasArrayRecord;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.canvas.CanvasGlyph;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Reads the persisted canvas hierarchy without invoking any compiler. */
public final class CarvingStructureMatcher {
    private CarvingStructureMatcher() {}

    /**
     * Returns whether the saved glyph forest satisfies the supplied
     * requirement. The top-level requirement has no implicit root circle:
     * unowned glyphs match {@code runes}, while top-level array records match
     * {@code outer_circle}.
     */
    public static boolean matches(CanvasDocument document, CarvingRequirement requirement) {
        if (document == null || requirement == null) {
            return false;
        }

        Map<UUID, CanvasGlyph> glyphs = new HashMap<>();
        for (CanvasGlyph glyph : document.glyphs()) {
            glyphs.put(glyph.glyphUuid(), glyph);
        }

        Map<UUID, CanvasArrayRecord> records = new HashMap<>();
        for (CanvasArrayRecord record : document.arrays()) {
            CanvasGlyph root = glyphs.get(record.rootGlyph());
            if (root != null && root.role() == SymbolRole.OUTER_CIRCLE) {
                records.putIfAbsent(record.rootGlyph(), record);
            }
        }
        if (records.size() != document.arrays().size()
                || document.arrays().stream().anyMatch(record ->
                !record.boundGlyphs().contains(record.rootGlyph())
                        || new HashSet<>(record.boundGlyphs()).size() != record.boundGlyphs().size()
                        || record.boundGlyphs().stream().anyMatch(id -> !glyphs.containsKey(id)))) {
            return false;
        }
        Map<UUID, UUID> parentByRoot = new HashMap<>();
        for (CanvasArrayRecord child : records.values()) {
            UUID childRoot = child.rootGlyph();
            CanvasArrayRecord parent = records.values().stream()
                    .filter(candidate -> !candidate.rootGlyph().equals(childRoot))
                    .filter(candidate -> candidate.boundGlyphs().contains(childRoot))
                    .filter(candidate -> candidate.boundGlyphs().containsAll(child.boundGlyphs()))
                    .min(Comparator.comparingInt(record -> record.boundGlyphs().size()))
                    .orElse(null);
            if (parent != null) {
                parentByRoot.put(childRoot, parent.rootGlyph());
            }
        }

        Map<UUID, List<UUID>> childrenByRoot = new HashMap<>();
        for (Map.Entry<UUID, UUID> entry : parentByRoot.entrySet()) {
            childrenByRoot.computeIfAbsent(entry.getValue(), ignored -> new ArrayList<>())
                    .add(entry.getKey());
        }

        List<StructureNode> roots = new ArrayList<>();
        for (CanvasArrayRecord record : records.values()) {
            if (!parentByRoot.containsKey(record.rootGlyph())) {
                StructureNode root = buildNode(record.rootGlyph(), records, glyphs,
                        childrenByRoot, new HashSet<>());
                if (root != null) roots.add(root);
            }
        }

        Set<UUID> arrayBoundGlyphs = new HashSet<>();
        for (CanvasArrayRecord record : document.arrays()) {
            arrayBoundGlyphs.addAll(record.boundGlyphs());
        }
        List<CanvasGlyph> unownedGlyphs = document.glyphs().stream()
                .filter(glyph -> !arrayBoundGlyphs.contains(glyph.glyphUuid()))
                .toList();
        if (unownedGlyphs.stream().anyMatch(glyph -> glyph.role() == SymbolRole.OUTER_CIRCLE)) {
            return false;
        }
        List<ResourceLocation> unownedRunes = unownedGlyphs.stream()
                .map(CanvasGlyph::symbolId)
                .toList();

        return sameMultiset(unownedRunes, requirement.runes())
                && matchesChildren(roots, requirement.outerCircle(),
                0, new boolean[roots.size()]);
    }

    private static StructureNode buildNode(
            UUID root,
            Map<UUID, CanvasArrayRecord> records,
            Map<UUID, CanvasGlyph> glyphs,
            Map<UUID, List<UUID>> childrenByRoot,
            Set<UUID> visiting) {
        if (!visiting.add(root)) return null;
        CanvasArrayRecord record = records.get(root);
        if (record == null) return null;

        Set<UUID> nestedGlyphs = new HashSet<>();
        List<StructureNode> children = new ArrayList<>();
        for (UUID childRoot : childrenByRoot.getOrDefault(root, List.of())) {
            StructureNode child = buildNode(childRoot, records, glyphs,
                    childrenByRoot, visiting);
            if (child == null) return null;
            children.add(child);
            CanvasArrayRecord childRecord = records.get(childRoot);
            if (childRecord != null) nestedGlyphs.addAll(childRecord.boundGlyphs());
        }
        visiting.remove(root);

        List<ResourceLocation> directRunes = new ArrayList<>();
        for (UUID glyphId : record.boundGlyphs()) {
            if (glyphId.equals(root) || nestedGlyphs.contains(glyphId)) continue;
            CanvasGlyph glyph = glyphs.get(glyphId);
            if (glyph != null && glyph.role() != SymbolRole.OUTER_CIRCLE) {
                directRunes.add(glyph.symbolId());
            }
        }
        return new StructureNode(directRunes, children);
    }

    private static boolean matchesNode(StructureNode actual, CarvingRequirement required) {
        if (!sameMultiset(actual.runes, required.runes())) return false;
        return matchesChildren(actual.children, required.outerCircle(), 0, new boolean[actual.children.size()]);
    }

    private static boolean matchesChildren(
            List<StructureNode> actual,
            List<CarvingRequirement> required,
            int requiredIndex,
            boolean[] used) {
        if (actual.size() != required.size()) return false;
        if (requiredIndex == required.size()) return true;
        CarvingRequirement next = required.get(requiredIndex);
        for (int index = 0; index < actual.size(); index++) {
            if (used[index] || !matchesNode(actual.get(index), next)) continue;
            used[index] = true;
            if (matchesChildren(actual, required, requiredIndex + 1, used)) return true;
            used[index] = false;
        }
        return false;
    }

    private static boolean sameMultiset(
            List<ResourceLocation> actual,
            List<ResourceLocation> required) {
        if (actual.size() != required.size()) return false;
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        for (ResourceLocation rune : actual) {
            counts.merge(rune, 1, Integer::sum);
        }
        for (ResourceLocation rune : required) {
            int remaining = counts.getOrDefault(rune, 0);
            if (remaining == 0) return false;
            counts.put(rune, remaining - 1);
        }
        return true;
    }

    private record StructureNode(
            List<ResourceLocation> runes,
            List<StructureNode> children
    ) {}
}
