package com.astune.gyromancy.canvas;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.array.MagicArrayManager;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolMatch;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.MagicArrayDetector;
import com.astune.gyromancy.array.runtime.ArrayEffectLifecycle;
import com.astune.gyromancy.registry.ModAttachments;
import com.astune.gyromancy.registry.ModSymbols;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractedGlyph;
import com.astune.gyromancy.symbol.SymbolRecognizer;
import com.astune.gyromancy.util.CanvasScanUtils;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Incremental recognition and compilation boundary for placed canvases. */
public final class CanvasCompileService {
    private CanvasCompileService() {}

    public static boolean applyEditorSubmission(ServerLevel level, CanvasEntity canvas,
                                                int baseRevision, int requestedScale,
                                                int[] colors, int[] effects) {
        if (baseRevision != canvas.revision()) return false;

        CanvasDocument old = canvas.document();
        CanvasDocument baseline;
        try {
            baseline = old.resolutionScale() == requestedScale ? old : old.resample(requestedScale);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (colors.length != baseline.resolutionWidth() * baseline.resolutionHeight()
                || effects.length != colors.length) {
            return false;
        }
        for (int effect : effects) {
            if (effect < 0 || effect > 255) return false;
        }

        CanvasDocument submitted;
        try {
            submitted = baseline.withRaster(colors, effects);
        } catch (IllegalArgumentException exception) {
            return false;
        }

        CanvasEditDiff diff = CanvasEditDiff.between(baseline, submitted);
        boolean resolutionOnly = old.resolutionScale() != requestedScale && diff.isEmpty();
        if (diff.isEmpty()) {
            if (resolutionOnly) {
                canvas.replaceDocument(baseline, true);
                refreshRetainedGlyphGeometry(level, canvas);
                canvas.broadcastSnapshot();
                MagicArrayDetector.syncWorldState(level);
            }
            return true;
        }

        List<CanvasGlyph> invalidated = baseline.glyphs().stream()
                .filter(diff::touches)
                .toList();
        Set<UUID> invalidatedIds = invalidated.stream()
                .map(CanvasGlyph::glyphUuid).collect(java.util.stream.Collectors.toSet());
        List<CanvasGlyph> retained = baseline.glyphs().stream()
                .filter(glyph -> !invalidatedIds.contains(glyph.glyphUuid()))
                .toList();
        List<CanvasGlyph> recognized = recognizeChangedComponents(canvas, submitted, diff);

        List<CanvasGlyph> nextGlyphs = new ArrayList<>(retained);
        nextGlyphs.addAll(recognized);
        CanvasDocument next = submitted.withCompileCache(nextGlyphs, List.of());

        MagicArrayManager manager = level.getData(ModAttachments.ARRAY_MANAGER);
        for (CanvasGlyph glyph : invalidated) {
            PositionedGlyph world = manager.getGlyph(glyph.glyphUuid());
            if (world == null) continue;
            ArrayEffectLifecycle.deactivateForGlyph(level, world);
            manager.unregisterGlyph(world.glyphUuid());
        }

        canvas.replaceDocument(next, true);
        if (baseline.resolutionScale() != next.resolutionScale()) {
            refreshRetainedGlyphGeometry(level, canvas);
        }
        List<PositionedGlyph> recognizedWorld = recognized.stream()
                .map(glyph -> registerGlyph(level, canvas, glyph))
                .toList();
        for (PositionedGlyph world : recognizedWorld) {
            deactivateAncestorArrays(level, manager, world);
        }
        for (PositionedGlyph world : recognizedWorld) {
            if (world.role() == SymbolRole.OUTER_CIRCLE) {
                ArrayEffectLifecycle.compileNew(level, world);
            }
        }

        List<CanvasArrayRecord> arrays =
                canvasArrayRecords(manager, canvas.getUUID());
        canvas.replaceDocument(next.withCompileCache(nextGlyphs, arrays), false);
        canvas.broadcastSnapshot();
        MagicArrayDetector.syncWorldState(level);
        return true;
    }

    public static void onPlaced(ServerLevel level, CanvasEntity canvas) {
        MagicArrayManager manager = level.getData(ModAttachments.ARRAY_MANAGER);
        List<PositionedGlyph> newlyRegistered = new ArrayList<>();
        for (CanvasGlyph glyph : canvas.document().glyphs()) {
            boolean isNew = manager.getGlyph(glyph.glyphUuid()) == null;
            PositionedGlyph world = registerGlyph(level, canvas, glyph);
            if (isNew) newlyRegistered.add(world);
        }

        for (PositionedGlyph world : newlyRegistered) {
            deactivateAncestorArrays(level, manager, world);
        }
        for (PositionedGlyph world : newlyRegistered) {
            if (world.role() == SymbolRole.OUTER_CIRCLE) {
                ArrayEffectLifecycle.compileNew(level, world);
            }
        }
        List<CanvasArrayRecord> arrays =
                canvasArrayRecords(manager, canvas.getUUID());
        canvas.replaceDocument(canvas.document().withCompileCache(
                canvas.document().glyphs(), arrays), false);
        MagicArrayDetector.syncWorldState(level);
    }

    public static void onRemoved(ServerLevel level, CanvasEntity canvas) {
        MagicArrayManager manager = level.getData(ModAttachments.ARRAY_MANAGER);
        UUID canvasId = canvas.getUUID();

        // The runtime records and canvas entity are saved independently. A
        // forced shutdown can preserve an old runtime without its lookup index.
        for (ArrayObject array : manager.getArrayObjsForCanvas(canvasId)) {
            ArrayEffectLifecycle.deactivate(level, array);
        }

        Set<UUID> ownedGlyphs = new LinkedHashSet<>();
        canvas.document().glyphs().stream()
                .map(CanvasGlyph::glyphUuid)
                .forEach(ownedGlyphs::add);
        manager.getGlyphsForCanvas(canvasId).stream()
                .map(PositionedGlyph::glyphUuid)
                .forEach(ownedGlyphs::add);
        for (UUID glyphId : ownedGlyphs) {
            PositionedGlyph world = manager.getGlyph(glyphId);
            if (world == null || world.sourceCanvasId().isEmpty()
                    || !world.sourceCanvasId().get().equals(canvasId)) {
                continue;
            }
            ArrayEffectLifecycle.deactivateForGlyph(level, world);
            manager.unregisterGlyph(world.glyphUuid());
        }
        MagicArrayDetector.syncWorldState(level);
    }

    private static void refreshRetainedGlyphGeometry(ServerLevel level, CanvasEntity canvas) {
        MagicArrayManager manager = level.getData(ModAttachments.ARRAY_MANAGER);
        for (CanvasGlyph local : canvas.document().glyphs()) {
            PositionedGlyph existing = manager.getGlyph(local.glyphUuid());
            int glyphId = existing == null ? manager.nextGlyphId() : existing.glyphId();
            manager.refreshGlyph(canvas.worldGlyph(local, glyphId));
        }
    }

    private static PositionedGlyph registerGlyph(ServerLevel level, CanvasEntity canvas,
                                                 CanvasGlyph glyph) {
        MagicArrayManager manager = level.getData(ModAttachments.ARRAY_MANAGER);
        PositionedGlyph existing = manager.getGlyph(glyph.glyphUuid());
        int glyphId = existing == null ? manager.nextGlyphId() : existing.glyphId();
        PositionedGlyph world = canvas.worldGlyph(glyph, glyphId);
        manager.refreshGlyph(world);
        return world;
    }

    private static List<CanvasGlyph> recognizeChangedComponents(
            CanvasEntity canvas, CanvasDocument document, CanvasEditDiff diff) {
        int width = document.resolutionWidth();
        int height = document.resolutionHeight();
        int[][] matrix = new int[height][width];
        int[] effects = document.rawStrokeEffects();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                matrix[y][x] = effects[y * width + x] > 0 ? 1 : 0;
            }
        }

        List<CanvasGlyph> recognized = new ArrayList<>();
        BitSet compileRegion = diff.compileRegion();
        for (CanvasScanUtils.ConnectedComponent component
                : CanvasScanUtils.extractComponents(matrix, 1)) {
            int[] cells = componentCells(component, width);
            if (Arrays.stream(cells).noneMatch(compileRegion::get)) continue;

            ExtractedGlyph extracted = extractedGlyph(canvas, document, component, cells);
            List<SymbolMatch> matches = SymbolRecognizer.recognize(extracted);
            if (matches.isEmpty()) continue;
            SymbolMatch best = matches.getFirst();
            if (ModSymbols.symbolLayerValueFor(best.symbolId()) <= 0) continue;

            double frontX = switch (canvas.getDirection()) {
                case NORTH, SOUTH -> best.front().x;
                case EAST, WEST -> best.front().z;
                case UP, DOWN -> best.front().x;
            };
            double frontY = switch (canvas.getDirection()) {
                case NORTH, SOUTH, EAST, WEST -> best.front().y;
                case UP, DOWN -> best.front().z;
            };
            recognized.add(new CanvasGlyph(
                    UUID.randomUUID(), best.symbolId(), best.confidence(), best.role(),
                    frontX, frontY, best.length(), best.width(),
                    (double) component.minX() / width,
                    (double) (component.maxX() + 1) / width,
                    (double) component.minY() / height,
                    (double) (component.maxY() + 1) / height,
                    cells));
        }
        return recognized;
    }

    private static int[] componentCells(CanvasScanUtils.ConnectedComponent component,
                                        int canvasWidth) {
        int[][] pixels = component.pixels();
        int[] cells = new int[component.area()];
        int count = 0;
        for (int y = 0; y < pixels.length; y++) {
            for (int x = 0; x < pixels[y].length; x++) {
                if (pixels[y][x] != 0) {
                    cells[count++] = (component.minY() + y) * canvasWidth
                            + component.minX() + x;
                }
            }
        }
        return count == cells.length ? cells : Arrays.copyOf(cells, count);
    }

    private static ExtractedGlyph extractedGlyph(
            CanvasEntity canvas,
            CanvasDocument document,
            CanvasScanUtils.ConnectedComponent component,
            int[] cells) {
        int width = document.resolutionWidth();
        int height = document.resolutionHeight();
        Set<PixelPos> pixels = new LinkedHashSet<>();
        double[] xs = new double[cells.length];
        double[] ys = new double[cells.length];
        for (int i = 0; i < cells.length; i++) {
            int x = cells[i] % width;
            int y = cells[i] / width;
            pixels.add(new PixelPos(canvas.getPos(), canvas.getDirection(),
                    x, y, document.rawColors()[cells[i]]));
            xs[i] = (x + 0.5) / width;
            ys[i] = (y + 0.5) / height;
        }
        return new ExtractedGlyph(
                Set.copyOf(pixels), xs, ys,
                (double) component.minX() / width,
                (double) (component.maxX() + 1) / width,
                (double) component.minY() / height,
                (double) (component.maxY() + 1) / height,
                1);
    }

    private static void deactivateAncestorArrays(
            ServerLevel level,
            MagicArrayManager manager,
            PositionedGlyph glyph) {
        PositionedGlyph parent = manager.parentCircle(glyph);
        while (parent != null) {
            ArrayEffectLifecycle.deactivateForRootGlyph(
                    level, parent.glyphUuid());
            parent = manager.parentCircle(parent);
        }
    }

    private static List<CanvasArrayRecord> canvasArrayRecords(
            MagicArrayManager manager, UUID canvasId) {
        return manager.getAllArrayObjs().stream()
                .filter(array -> array.rootCircleGlyph().sourceCanvasId()
                        .filter(canvasId::equals).isPresent())
                .map(array -> {
                    UUID root = array.rootCircleGlyph().glyphUuid();
                    List<UUID> bound = array.boundGlyphs().stream()
                            .map(PositionedGlyph::glyphUuid)
                            .toList();
                    return new CanvasArrayRecord(
                            root, bound,
                            CanvasArrayRecord.fingerprint(root, bound));
                })
                .toList();
    }
}
