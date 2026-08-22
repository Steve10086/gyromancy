package com.astune.gyromancy.item;

import com.astune.gyromancy.api.canvas.Carvable;
import com.astune.gyromancy.canvas.CanvasArrayRecord;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.canvas.CanvasGlyph;
import com.astune.gyromancy.registry.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public abstract class CarvingMaterials extends Item implements Carvable {
    public CarvingMaterials(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        CanvasDocument document = stack.get(ModDataComponents.CARVING_DOCUMENT.get());
        if (document == null) return;
        if (document.glyphs().isEmpty() && document.arrays().isEmpty()) return;

        Map<UUID, CanvasGlyph> runesById = new LinkedHashMap<>();
        for (CanvasGlyph rune : document.glyphs()) {
            runesById.put(rune.glyphUuid(), rune);
        }

        Map<UUID, CanvasArrayRecord> arraysByRoot = new LinkedHashMap<>();
        for (CanvasArrayRecord array : document.arrays()) {
            if (runesById.containsKey(array.rootGlyph())) {
                arraysByRoot.putIfAbsent(array.rootGlyph(), array);
            }
        }

        Map<UUID, CanvasArrayRecord> parentByRoot = findArrayParents(
                document.arrays(), arraysByRoot);
        Map<UUID, List<CanvasArrayRecord>> childrenByRoot = new LinkedHashMap<>();
        for (CanvasArrayRecord array : document.arrays()) {
            CanvasArrayRecord parent = parentByRoot.get(array.rootGlyph());
            if (parent != null) {
                childrenByRoot.computeIfAbsent(parent.rootGlyph(), ignored -> new java.util.ArrayList<>())
                        .add(array);
            }
        }

        Set<UUID> printedRunes = new HashSet<>();
        for (CanvasArrayRecord array : document.arrays()) {
            if (!arraysByRoot.containsKey(array.rootGlyph())
                    || parentByRoot.containsKey(array.rootGlyph())) {
                continue;
            }
            appendArrayRunes(tooltip, array, runesById, childrenByRoot, printedRunes,
                    0, "◆ ");
        }

        // Keep recognized runes which are not part of a successfully compiled
        // AST visible as top-level entries instead of silently dropping them.
        for (CanvasGlyph rune : document.glyphs()) {
            if (printedRunes.add(rune.glyphUuid())) {
                appendRune(tooltip, rune, 0, "• ");
            }
        }

    }

    private static Map<UUID, CanvasArrayRecord> findArrayParents(
            List<CanvasArrayRecord> arrays,
            Map<UUID, CanvasArrayRecord> arraysByRoot) {
        Map<UUID, CanvasArrayRecord> parents = new LinkedHashMap<>();
        for (CanvasArrayRecord child : arrays) {
            if (!arraysByRoot.containsKey(child.rootGlyph())) continue;

            CanvasArrayRecord parent = null;
            for (CanvasArrayRecord candidate : arrays) {
                if (candidate.rootGlyph().equals(child.rootGlyph())
                        || !candidate.boundGlyphs().contains(child.rootGlyph())) {
                    continue;
                }
                if (parent == null
                        || candidate.boundGlyphs().size() < parent.boundGlyphs().size()) {
                    parent = candidate;
                }
            }
            if (parent != null) {
                parents.put(child.rootGlyph(), parent);
            }
        }
        return parents;
    }

    private static void appendArrayRunes(
            List<Component> tooltip,
            CanvasArrayRecord array,
            Map<UUID, CanvasGlyph> runesById,
            Map<UUID, List<CanvasArrayRecord>> childrenByRoot,
            Set<UUID> printedRunes,
            int depth,
            String marker) {
        CanvasGlyph root = runesById.get(array.rootGlyph());
        if (root == null || !printedRunes.add(root.glyphUuid())) return;
        appendRune(tooltip, root, depth, marker);

        List<CanvasArrayRecord> children = childrenByRoot.getOrDefault(
                array.rootGlyph(), List.of());
        Set<UUID> nestedRunes = new HashSet<>();
        for (CanvasArrayRecord child : children) {
            nestedRunes.addAll(child.boundGlyphs());
        }

        List<UUID> visibleChildren = new java.util.ArrayList<>();
        for (UUID glyphId : array.boundGlyphs()) {
            if (glyphId.equals(array.rootGlyph())) continue;

            CanvasArrayRecord child = children.stream()
                    .filter(candidate -> candidate.rootGlyph().equals(glyphId))
                    .findFirst()
                    .orElse(null);
            if (child != null) {
                visibleChildren.add(glyphId);
                continue;
            }
            if (nestedRunes.contains(glyphId)) continue;

            CanvasGlyph rune = runesById.get(glyphId);
            if (rune != null && !printedRunes.contains(rune.glyphUuid())) {
                visibleChildren.add(glyphId);
            }
        }

        for (int index = 0; index < visibleChildren.size(); index++) {
            UUID glyphId = visibleChildren.get(index);
            String childMarker = index == visibleChildren.size() - 1
                    ? "└─ "
                    : "├─ ";
            CanvasArrayRecord child = children.stream()
                    .filter(candidate -> candidate.rootGlyph().equals(glyphId))
                    .findFirst()
                    .orElse(null);
            if (child != null) {
                appendArrayRunes(tooltip, child, runesById, childrenByRoot,
                        printedRunes, depth + 1, childMarker);
                continue;
            }

            CanvasGlyph rune = runesById.get(glyphId);
            if (rune != null && printedRunes.add(rune.glyphUuid())) {
                appendRune(tooltip, rune, depth + 1, childMarker);
            }
        }
    }

    private static void appendRune(List<Component> tooltip,
                                   CanvasGlyph rune,
                                   int depth,
                                   String marker) {
        ResourceLocation symbolId = rune.symbolId();
        String translationKey = "symbol." + symbolId.getNamespace()
                + "." + symbolId.getPath();
        Style runeStyle = rune.role() == null
                ? CarvingTooltipStyles.UNKNOWN_RUNE
                : CarvingTooltipStyles.forRole(rune.role());
        tooltip.add(Component.literal("  ".repeat(depth) + marker)
                .withStyle(CarvingTooltipStyles.FRAME)
                .append(Component.translatable(translationKey).withStyle(runeStyle)));
    }
}
