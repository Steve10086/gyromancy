package com.astune.gyromancy.canvas;

import com.astune.gyromancy.api.canvas.Carvable;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.GroupNode;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanvasPortableCompileTest {
    @Test
    void portableArrayCacheUsesTheNormalCanvasArrayCompiler() {
        CanvasGlyph circle = glyph(
                "circle_outer", SymbolRole.OUTER_CIRCLE,
                0.0, 1.0, 0.0, 1.0);
        CanvasGlyph fire = glyph(
                "fire", SymbolRole.CENTER_SYMBOL,
                0.4, 0.6, 0.4, 0.6);

        List<CanvasArrayRecord> arrays = CanvasCompileService.compilePortableArrays(
                CanvasDocument.blank(1, 1), List.of(circle, fire));

        assertEquals(1, arrays.size());
        assertEquals(circle.glyphUuid(), arrays.getFirst().rootGlyph());
        assertEquals(List.of(circle.glyphUuid(), fire.glyphUuid()),
                arrays.getFirst().boundGlyphs());
    }

    @Test
    void portableAstCompilationAcceptsTheSameCanvasGlyphInput() {
        CanvasGlyph circle = glyph(
                "circle_outer", SymbolRole.OUTER_CIRCLE,
                0.0, 1.0, 0.0, 1.0);
        CanvasGlyph fire = glyph(
                "fire", SymbolRole.CENTER_SYMBOL,
                0.4, 0.6, 0.4, 0.6);
        CanvasDocument document = CanvasDocument.blank(1, 1)
                .withCompileCache(List.of(circle, fire), List.of());

        List<GroupNode> asts = CanvasCompileService.compilePortableAsts(document);

        assertEquals(1, asts.size());
        assertEquals(circle.glyphUuid(), asts.getFirst().boundary().glyphUuid());
    }

    @Test
    void portableAstKeepsACompiledNonRuntimeRootForCarvingData() {
        CanvasGlyph circle = glyph(
                "circle_outer", SymbolRole.OUTER_CIRCLE,
                0.0, 1.0, 0.0, 1.0);
        CanvasGlyph arrow = glyph(
                "arrow", SymbolRole.PARAMETER_RUNE,
                0.4, 0.6, 0.4, 0.6);
        CanvasDocument document = CanvasDocument.blank(1, 1)
                .withCompileCache(List.of(circle, arrow), List.of());

        List<GroupNode> asts = CanvasCompileService.compilePortableAsts(document);

        assertEquals(1, asts.size());
        assertEquals(circle.glyphUuid(), asts.getFirst().boundary().glyphUuid());
    }

    @Test
    void portableAstKeepsAnUncompilableCircleTreeForCarvingData() {
        CanvasGlyph circle = glyph(
                "circle_outer", SymbolRole.OUTER_CIRCLE,
                0.0, 1.0, 0.0, 1.0);
        CanvasDocument document = CanvasDocument.blank(1, 1)
                .withCompileCache(List.of(circle), List.of());

        List<GroupNode> asts = CanvasCompileService.compilePortableAsts(document);
        List<CanvasArrayRecord> records = Carvable.arrayRecordsFromAsts(asts);

        assertEquals(1, asts.size());
        assertEquals(1, records.size());
        assertEquals(List.of(circle.glyphUuid()), records.getFirst().boundGlyphs());
    }

    @Test
    void compiledMaterialIsTransparentAndUsesTheArrayEffectColorAtHalfAlpha() {
        int[] sourceColors = new int[16 * 16];
        int[] sourceEffects = new int[16 * 16];
        sourceColors[17] = 0xFFFF0000;
        sourceColors[18] = 0xFF00FF00;
        sourceColors[19] = 0xFF0000FF;
        sourceEffects[17] = sourceEffects[18] = sourceEffects[19] = 1;
        CanvasDocument source = new CanvasDocument(
                1, 1, 1, sourceColors, sourceEffects, List.of(), List.of());
        CanvasGlyph circle = new CanvasGlyph(
                UUID.randomUUID(),
                ResourceLocation.fromNamespaceAndPath("gyromancy", "circle_outer"),
                1.0F, SymbolRole.OUTER_CIRCLE,
                0.0, -1.0, 1.0, 1.0,
                0.05, 0.20, 0.05, 0.15,
                new int[]{17, 18});
        CanvasArrayRecord array = new CanvasArrayRecord(
                circle.glyphUuid(), List.of(circle.glyphUuid()),
                CanvasArrayRecord.fingerprint(
                        circle.glyphUuid(), List.of(circle.glyphUuid())),
                0xFF336699);

        int[] material = CanvasCompileService.compiledStrokeMaterial(
                source, List.of(circle), List.of(array));

        assertEquals(0x80336699, material[17]);
        assertEquals(0x80336699, material[18]);
        assertEquals(0, material[19], "strokes outside compiled arrays stay invisible");
        assertEquals(0, material[0], "the projection material has no background");
    }

    private static CanvasGlyph glyph(
            String name, SymbolRole role,
            double minX, double maxX, double minY, double maxY) {
        return new CanvasGlyph(
                UUID.randomUUID(),
                ResourceLocation.fromNamespaceAndPath("gyromancy", name),
                1.0F, role,
                0.0, -1.0, 1.0, 1.0,
                minX, maxX, minY, maxY,
                new int[0]);
    }
}
