package com.astune.gyromancy.recipe;

import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.canvas.CanvasArrayRecord;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.canvas.CanvasGlyph;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarvingStructureMatcherTest {
    private static final ResourceLocation ARROW = id("arrow");
    private static final ResourceLocation CURL = id("curl");
    private static final ResourceLocation SPLIT = id("split");

    @Test
    void matchesTopLevelRunesAndCircleWithoutAnImplicitRoot() {
        UUID rootId = UUID.randomUUID();
        UUID arrowA = UUID.randomUUID();
        UUID arrowB = UUID.randomUUID();
        UUID curl = UUID.randomUUID();

        CanvasGlyph root = glyph(rootId, "circle_outer", SymbolRole.OUTER_CIRCLE);
        CanvasGlyph firstArrow = glyph(arrowA, "arrow", SymbolRole.PARAMETER_RUNE);
        CanvasGlyph secondArrow = glyph(arrowB, "arrow", SymbolRole.PARAMETER_RUNE);
        CanvasGlyph innerCurl = glyph(curl, "curl", SymbolRole.PARAMETER_RUNE);

        CanvasArrayRecord rootArray = array(rootId, List.of(rootId, curl));
        CanvasDocument document = CanvasDocument.blank(1, 1)
                .withCompileCache(
                        List.of(root, firstArrow, secondArrow, innerCurl),
                        List.of(rootArray));

        CarvingRequirement requirement = new CarvingRequirement(
                List.of(ARROW, ARROW),
                List.of(new CarvingRequirement(List.of(CURL), List.of())));

        assertTrue(CarvingStructureMatcher.matches(document, requirement));
        assertFalse(CarvingStructureMatcher.matches(document,
                new CarvingRequirement(List.of(ARROW, SPLIT), List.of())));

        CanvasGlyph extraRune = glyph(UUID.randomUUID(), "split", SymbolRole.PARAMETER_RUNE);
        CanvasDocument withExtraTopLevelRune = document.withCompileCache(
                List.of(root, firstArrow, secondArrow, innerCurl, extraRune),
                List.of(rootArray));
        assertFalse(CarvingStructureMatcher.matches(withExtraTopLevelRune, requirement));

        UUID extraRootId = UUID.randomUUID();
        CanvasGlyph extraRoot = glyph(extraRootId, "circle_outer", SymbolRole.OUTER_CIRCLE);
        CanvasArrayRecord extraRootArray = array(extraRootId, List.of(extraRootId));
        CanvasDocument withExtraTopLevelArray = document.withCompileCache(
                List.of(root, firstArrow, secondArrow, innerCurl, extraRoot),
                List.of(rootArray, extraRootArray));
        assertFalse(CarvingStructureMatcher.matches(withExtraTopLevelArray, requirement));

        CanvasGlyph extraNestedRune = glyph(UUID.randomUUID(), "split", SymbolRole.PARAMETER_RUNE);
        CanvasDocument withExtraNestedRune = document.withCompileCache(
                List.of(root, firstArrow, secondArrow, innerCurl, extraNestedRune),
                List.of(array(rootId, List.of(rootId, curl, extraNestedRune.glyphUuid()))));
        assertFalse(CarvingStructureMatcher.matches(withExtraNestedRune, requirement));

        UUID extraNestedRootId = UUID.randomUUID();
        CanvasGlyph extraNestedRoot = glyph(extraNestedRootId, "circle_outer", SymbolRole.OUTER_CIRCLE);
        CanvasArrayRecord nestedArray = array(extraNestedRootId, List.of(extraNestedRootId));
        CanvasDocument withExtraNestedArray = document.withCompileCache(
                List.of(root, firstArrow, secondArrow, innerCurl, extraNestedRoot),
                List.of(array(rootId, List.of(rootId, curl, extraNestedRootId)), nestedArray));
        assertFalse(CarvingStructureMatcher.matches(withExtraNestedArray, requirement));
    }

    @Test
    void recursiveRequirementCodecReadsTheRecipeShape() {
        var json = JsonParser.parseString("""
                {
                  "runes": ["gyromancy:arrow", "gyromancy:arrow"],
                  "outer_circle": [
                    {"runes": ["gyromancy:curl"]}
                  ]
                }
                """);

        CarvingRequirement decoded = CarvingRequirement.CODEC
                .parse(JsonOps.INSTANCE, json)
                .getOrThrow();

        assertEquals(List.of(ARROW, ARROW), decoded.runes());
        assertEquals(List.of(new CarvingRequirement(List.of(CURL), List.of())),
                decoded.outerCircle());
    }

    @Test
    void matchesOnlyTopLevelRunesWithoutAnImplicitRootCircle() {
        CanvasGlyph firstArrow = glyph(UUID.randomUUID(), "arrow", SymbolRole.PARAMETER_RUNE);
        CanvasGlyph secondArrow = glyph(UUID.randomUUID(), "arrow", SymbolRole.PARAMETER_RUNE);
        CanvasDocument document = CanvasDocument.blank(1, 1)
                .withCompileCache(List.of(firstArrow, secondArrow), List.of());

        assertTrue(CarvingStructureMatcher.matches(document,
                new CarvingRequirement(List.of(ARROW, ARROW), List.of())));

        CanvasGlyph extraArrow = glyph(UUID.randomUUID(), "arrow", SymbolRole.PARAMETER_RUNE);
        assertFalse(CarvingStructureMatcher.matches(
                document.withCompileCache(List.of(firstArrow, secondArrow, extraArrow), List.of()),
                new CarvingRequirement(List.of(ARROW, ARROW), List.of())));
    }

    private static CanvasArrayRecord array(UUID root, List<UUID> bound) {
        return new CanvasArrayRecord(root, bound, CanvasArrayRecord.fingerprint(root, bound));
    }

    private static CanvasGlyph glyph(UUID uuid, String name, SymbolRole role) {
        return new CanvasGlyph(
                uuid, id(name), 1.0F, role,
                0.0, -1.0, 1.0, 1.0,
                0.0, 1.0, 0.0, 1.0,
                new int[0]);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("gyromancy", path);
    }

}
