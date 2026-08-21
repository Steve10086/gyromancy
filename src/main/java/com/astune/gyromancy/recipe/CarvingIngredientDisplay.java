package com.astune.gyromancy.recipe;

import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.canvas.CanvasArrayRecord;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.canvas.CanvasGlyph;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Builds the display-only canvas document exposed by a carving ingredient. */
final class CarvingIngredientDisplay {
    private static final ResourceLocation OUTER_CIRCLE =
            ResourceLocation.fromNamespaceAndPath("gyromancy", "circle_outer");

    private CarvingIngredientDisplay() {}

    /** Builds a synthetic glyph forest matching the recipe descriptor exactly. */
    static CanvasDocument documentFor(CarvingRequirement requirement) {
        List<CanvasGlyph> glyphs = new ArrayList<>();
        List<CanvasArrayRecord> arrays = new ArrayList<>();

        for (int index = 0; index < requirement.runes().size(); index++) {
            glyphs.add(displayGlyph(requirement.runes().get(index), SymbolRole.PARAMETER_RUNE,
                    "top/rune/" + index));
        }
        for (int index = 0; index < requirement.outerCircle().size(); index++) {
            appendCircle(requirement.outerCircle().get(index), "top/circle/" + index,
                    glyphs, arrays);
        }

        return CanvasDocument.blank(1, 1).withCompileCache(glyphs, arrays);
    }

    private static List<UUID> appendCircle(
            CarvingRequirement requirement,
            String path,
            List<CanvasGlyph> glyphs,
            List<CanvasArrayRecord> arrays) {
        UUID root = displayId(path + "/root");
        glyphs.add(displayGlyph(OUTER_CIRCLE, SymbolRole.OUTER_CIRCLE,
                path + "/root"));

        List<UUID> bound = new ArrayList<>();
        bound.add(root);
        for (int index = 0; index < requirement.runes().size(); index++) {
            String runePath = path + "/rune/" + index;
            UUID runeId = displayId(runePath);
            glyphs.add(displayGlyph(requirement.runes().get(index),
                    SymbolRole.PARAMETER_RUNE, runePath));
            bound.add(runeId);
        }
        for (int index = 0; index < requirement.outerCircle().size(); index++) {
            bound.addAll(appendCircle(requirement.outerCircle().get(index),
                    path + "/circle/" + index, glyphs, arrays));
        }

        arrays.add(new CanvasArrayRecord(root, bound,
                CanvasArrayRecord.fingerprint(root, bound)));
        return List.copyOf(bound);
    }

    private static CanvasGlyph displayGlyph(
            ResourceLocation symbol,
            SymbolRole role,
            String path) {
        return new CanvasGlyph(
                displayId(path), symbol, 1.0F, role,
                0.0, 1.0, 1.0, 1.0,
                0.0, 1.0, 0.0, 1.0,
                new int[0]);
    }

    private static UUID displayId(String path) {
        return UUID.nameUUIDFromBytes(("gyromancy:recipe-display:" + path)
                .getBytes(StandardCharsets.UTF_8));
    }
}
