package com.astune.gyromancy.canvas;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.geometry.SurfaceFrame;
import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.array.MagicArrayManager;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolMatch;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.MagicArrayDetector;
import com.astune.gyromancy.array.compile.ArrayAstBuilder;
import com.astune.gyromancy.array.compile.ArrayNodeCompiler;
import com.astune.gyromancy.array.compile.GroupNode;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.CompiledArray;
import com.astune.gyromancy.array.runtime.ArrayEffectLifecycle;
import com.astune.gyromancy.compile.operator.PersistentOp;
import com.astune.gyromancy.entity.projection.ProjectionCanvasEntity;
import com.astune.gyromancy.registry.ModAttachments;
import com.astune.gyromancy.registry.ModSymbols;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractedGlyph;
import com.astune.gyromancy.symbol.SymbolRecognizer;
import com.astune.gyromancy.util.CanvasScanUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Incremental recognition and compilation boundary for placed canvases. */
public final class CanvasCompileService {
    static final int WAND_MATERIAL_ALPHA = 0x80;

    private CanvasCompileService() {}

    /**
     * Runs the same glyph recognition, hierarchy construction, and array
     * compiler used by placed canvases without registering world runtime state.
     */
    public static CanvasDocument compilePortable(CanvasDocument document) {
        CanvasDocument raster = document.withCompileCache(List.of(), List.of());
        BitSet wholeRaster = new BitSet(raster.resolutionWidth() * raster.resolutionHeight());
        wholeRaster.set(0, raster.resolutionWidth() * raster.resolutionHeight());
        List<CanvasGlyph> glyphs = recognizeChangedComponents(raster, wholeRaster);
        List<CanvasArrayRecord> arrays = compilePortableArrays(raster, glyphs);
        CanvasDocument compiled = raster.withCompileCache(glyphs, arrays);
        return compiled.withRaster(
                compiledStrokeMaterial(raster, glyphs, arrays),
                raster.strokeEffects());
    }

    /**
     * Applies a portable carving edit without compiling any AST. Existing
     * glyph identities are retained unless their pixels intersect the edit
     * region. Array records which depend on an invalidated glyph are dropped;
     * final array/AST compilation is performed when the carving menu closes.
     */
    public static CanvasDocument updatePortableIncremental(
            CanvasDocument before, CanvasDocument submitted) {
        CanvasDocument baseline;
        try {
            baseline = before.resolutionScale() == submitted.resolutionScale()
                    ? before : before.resample(submitted.resolutionScale());
        } catch (IllegalArgumentException exception) {
            return submitted.withCompileCache(List.of(), List.of());
        }

        CanvasEditDiff diff = CanvasEditDiff.between(baseline, submitted);
        if (diff.isEmpty()) {
            return submitted.withCompileCache(baseline.glyphs(), baseline.arrays());
        }

        List<CanvasGlyph> invalidated = baseline.glyphs().stream()
                .filter(diff::touches)
                .toList();
        Set<UUID> invalidatedIds = invalidated.stream()
                .map(CanvasGlyph::glyphUuid).collect(java.util.stream.Collectors.toSet());
        List<CanvasGlyph> retained = baseline.glyphs().stream()
                .filter(glyph -> !invalidatedIds.contains(glyph.glyphUuid()))
                .toList();
        List<CanvasGlyph> recognized = MagicArrayDetector.circlesLast(
                recognizeChangedComponents(submitted, diff.compileRegion()),
                glyph -> glyph.role() == SymbolRole.OUTER_CIRCLE);

        List<CanvasGlyph> nextGlyphs = new ArrayList<>(retained);
        nextGlyphs.addAll(recognized);
        return submitted.withCompileCache(nextGlyphs, List.of());
    }

    /**
     * Builds one portable AST root for every outer circle in a document.
     * Operator compilation is diagnostic only here: a carvable item stores
     * the syntax tree even when its current inputs do not form a runnable op.
     */
    public static List<GroupNode> compilePortableAsts(CanvasDocument document) {
        Gyromancy.LOGGER.info(
                "[PortableCompiler] input raster={}x{} physical={}x{} glyphs={} cachedArrays={}",
                document.resolutionWidth(), document.resolutionHeight(),
                document.physicalWidth(), document.physicalHeight(),
                document.glyphs().size(), document.arrays().size());

        MagicArrayManager manager = new MagicArrayManager();
        java.util.LinkedHashMap<UUID, PositionedGlyph> positioned = new java.util.LinkedHashMap<>();
        int glyphId = 1;
        for (CanvasGlyph glyph : document.glyphs()) {
            Gyromancy.LOGGER.info(
                    "[PortableCompiler] glyph uuid={} symbol={} role={} bounds=({},{})->({},{}) cells={}",
                    glyph.glyphUuid(), glyph.symbolId(), glyph.role(),
                    glyph.minX(), glyph.minY(), glyph.maxX(), glyph.maxY(),
                    glyph.rawCells().length);
            PositionedGlyph world = portableGlyph(document, glyph, glyphId++);
            positioned.put(glyph.glyphUuid(), world);
            manager.registerGlyph(world);
        }

        long outerCircleCount = positioned.values().stream()
                .filter(glyph -> glyph.role() == SymbolRole.OUTER_CIRCLE)
                .count();
        if (outerCircleCount == 0) {
            Gyromancy.LOGGER.warn(
                    "[PortableCompiler] no outer circle in {} cached glyph(s); AST result is empty",
                    positioned.size());
        }

        List<GroupNode> asts = new ArrayList<>();
        positioned.values().stream()
                .filter(glyph -> glyph.role() == SymbolRole.OUTER_CIRCLE)
                .sorted(Comparator.comparingDouble((PositionedGlyph glyph) -> glyph.bounds().area())
                        .thenComparingInt(PositionedGlyph::glyphId))
                .forEach(circle -> {
                    List<PositionedGlyph> directChildren = manager.directChildren(circle);
                    GroupNode ast = ArrayAstBuilder.build(circle, manager, ignored -> true);
                    Gyromancy.LOGGER.info(
                            "[PortableCompiler] circle uuid={} glyphId={} directChildren={} ast={}",
                            circle.glyphUuid(), circle.glyphId(), directChildren.size(),
                            describeAst(ast));
                    CompileResult<CompiledArray> result = ArrayNodeCompiler.compile(
                            ast, manager.opDefinitions());
                    if (!(result instanceof CompileResult.Success<CompiledArray> success)) {
                        if (result instanceof CompileResult.Failure<CompiledArray> failure) {
                            Gyromancy.LOGGER.warn(
                                    "[PortableCompiler] circle uuid={} compile failed: {}",
                                    circle.glyphUuid(), failure.diagnostics());
                        }
                    } else if (!(success.value().root() instanceof PersistentOp)) {
                        Gyromancy.LOGGER.info(
                                "[PortableCompiler] circle uuid={} compiled root {} is a non-runtime AST; retaining it",
                                circle.glyphUuid(), success.value().root().getClass().getName());
                    }
                    // The tree is the carving result. Runtime eligibility is
                    // deliberately not allowed to delete it.
                    asts.add(ast);
                });

        Gyromancy.LOGGER.info("[PortableCompiler] accepted AST roots={}", asts.size());

        return List.copyOf(asts);
    }

    private static String describeAst(GroupNode ast) {
        StringBuilder result = new StringBuilder();
        describeAstNode(ast, result, 0);
        return result.toString();
    }

    private static void describeAstNode(
            com.astune.gyromancy.array.compile.ArrayNode node,
            StringBuilder result,
            int depth) {
        if (depth > 16) {
            result.append("...");
            return;
        }
        if (depth > 0) result.append("/");
        switch (node) {
            case GroupNode group -> {
                result.append("Group(").append(group.boundary().symbolId().getPath()).append(")");
                describeAstNode(group.body(), result, depth + 1);
            }
            case com.astune.gyromancy.array.compile.SequenceNode sequence -> {
                result.append("Sequence[");
                for (com.astune.gyromancy.array.compile.ArrayNode child : sequence.children()) {
                    describeAstNode(child, result, depth + 1);
                    result.append(",");
                }
                result.append("]");
            }
            case com.astune.gyromancy.array.compile.SymbolNode symbol ->
                    result.append("Rune(").append(symbol.glyph().symbolId().getPath()).append(")");
            case com.astune.gyromancy.array.compile.ApplyNode apply -> result.append("Apply");
        }
    }

    static int[] compiledStrokeMaterial(
            CanvasDocument source,
            List<CanvasGlyph> glyphs,
            List<CanvasArrayRecord> arrays) {
        int[] material = new int[source.resolutionWidth() * source.resolutionHeight()];
        java.util.HashMap<UUID, CanvasGlyph> glyphById = new java.util.HashMap<>();
        glyphs.forEach(glyph -> glyphById.put(glyph.glyphUuid(), glyph));
        int width = source.resolutionWidth();
        int height = source.resolutionHeight();
        for (CanvasArrayRecord array : arrays) {
            CanvasGlyph boundary = glyphById.get(array.rootGlyph());
            if (boundary == null) continue;
            int color = WAND_MATERIAL_ALPHA << 24 | array.color() & 0x00FFFFFF;
            int minX = Math.max(0, (int) Math.floor(boundary.minX() * width));
            int maxX = Math.min(width - 1, (int) Math.ceil(boundary.maxX() * width) - 1);
            int minY = Math.max(0, (int) Math.floor(boundary.minY() * height));
            int maxY = Math.min(height - 1, (int) Math.ceil(boundary.maxY() * height) - 1);
            for (int y = minY; y <= maxY; y++) {
                double normalizedY = (y + 0.5) / height;
                if (normalizedY < boundary.minY() || normalizedY > boundary.maxY()) continue;
                for (int x = minX; x <= maxX; x++) {
                    double normalizedX = (x + 0.5) / width;
                    if (normalizedX < boundary.minX() || normalizedX > boundary.maxX()) continue;
                    int cell = y * width + x;
                    if (source.rawStrokeEffects()[cell] > 0) material[cell] = color;
                }
            }
        }
        return material;
    }

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
                // A collapsed canvas intentionally has no world glyphs to
                // refresh. Its cached document is activated only when the
                // editor session ends and it unfurls.
                if (!canvas.isCollapsed()) {
                    refreshRetainedGlyphGeometry(level, canvas);
                }
                canvas.broadcastSnapshot();
                if (!canvas.isCollapsed()) {
                    MagicArrayDetector.syncWorldState(level);
                }
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
        List<CanvasGlyph> recognized = MagicArrayDetector.circlesLast(
                recognizeChangedComponents(submitted, diff.compileRegion()),
                glyph -> glyph.role() == SymbolRole.OUTER_CIRCLE);

        List<CanvasGlyph> nextGlyphs = new ArrayList<>(retained);
        nextGlyphs.addAll(recognized);
        CanvasDocument next = submitted.withCompileCache(nextGlyphs, List.of());

        // Submissions are made when the editor closes. Keep a collapsed
        // canvas entirely document-local until its finish packet unfurls it;
        // onPlaced then registers every glyph and creates arrays exactly once.
        if (canvas.isCollapsed()) {
            canvas.replaceDocument(next, true);
            canvas.broadcastSnapshot();
            return true;
        }

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
        ArrayEffectLifecycle.compileCirclesSmallestFirst(level, recognizedWorld);

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
        List<CanvasGlyph> orderedGlyphs = MagicArrayDetector.circlesLast(
                canvas.document().glyphs(),
                glyph -> glyph.role() == SymbolRole.OUTER_CIRCLE);
        for (CanvasGlyph glyph : orderedGlyphs) {
            boolean isNew = manager.getGlyph(glyph.glyphUuid()) == null;
            PositionedGlyph world = registerGlyph(level, canvas, glyph);
            if (isNew) newlyRegistered.add(world);
        }

        for (PositionedGlyph world : newlyRegistered) {
            deactivateAncestorArrays(level, manager, world);
        }
        // Projection canvases use the same runtime path as ordinary canvases:
        // all glyphs are registered first, then every circle is compiled from
        // the smallest geometry outward. The persisted array list is only a
        // compatibility/material cache and is not an activation source.
        ArrayEffectLifecycle.compileCirclesSmallestFirst(
                level, manager.getGlyphsForCanvas(canvas.getUUID()));
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
        ArrayEffectLifecycle.deactivateParentedRootArrays(level);
    }

    /**
     * Moves an existing canvas' world glyphs without recompiling or replacing
     * its active arrays. Projection canvases use this when their free surface
     * follows a caster's view.
     */
    public static void refreshWorldGeometry(ServerLevel level, CanvasEntity canvas) {
        refreshRetainedGlyphGeometry(level, canvas);
        if (canvas instanceof ProjectionCanvasEntity) {
            MagicArrayDetector.syncProjectionGeometry(level, canvas);
        } else {
            MagicArrayDetector.syncWorldState(level);
        }
    }

    private static PositionedGlyph registerGlyph(ServerLevel level, CanvasEntity canvas,
                                                 CanvasGlyph glyph) {
        MagicArrayManager manager = level.getData(ModAttachments.ARRAY_MANAGER);
        PositionedGlyph existing = manager.getGlyph(glyph.glyphUuid());
        int glyphId = existing == null ? manager.nextGlyphId() : existing.glyphId();
        PositionedGlyph world = canvas.worldGlyph(glyph, glyphId);
        manager.refreshGlyph(world);
        ArrayEffectLifecycle.deactivateParentedRootArrays(level);
        return world;
    }

    private static List<CanvasGlyph> recognizeChangedComponents(
            CanvasDocument document, BitSet compileRegion) {
        int width = document.resolutionWidth();
        int height = document.resolutionHeight();
        int[] effects = document.rawStrokeEffects();

        List<CanvasGlyph> recognized = new ArrayList<>();
        for (CanvasScanUtils.ConnectedComponent component
                : CanvasScanUtils.extractComponents(
                        effects, width, height, compileRegion, 1)) {
            int[] cells = componentCells(component, width);

            ExtractedGlyph extracted = extractedGlyph(document, component, cells);
            List<SymbolMatch> matches = SymbolRecognizer.recognize(extracted);
            if (matches.isEmpty()) continue;
            SymbolMatch best = matches.getFirst();
            if (ModSymbols.symbolLayerValueFor(best.symbolId()) <= 0) continue;

            SurfaceFrame legacyFrame = SurfaceFrame.fromBlockFace(
                    BlockPos.ZERO, Direction.NORTH);
            double frontX = best.front().dot(legacyFrame.axisU());
            double frontY = best.front().dot(legacyFrame.axisV());
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

    static ExtractedGlyph extractedGlyph(
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
            pixels.add(new PixelPos(BlockPos.ZERO, Direction.NORTH,
                    x, y, document.rawColors()[cells[i]]));
            xs[i] = (x + 0.5) / width;
            ys[i] = (y + 0.5) / height;
        }
        return new ExtractedGlyph(
                Set.copyOf(pixels), xs, ys,
                // rawGlyphMatrix expects the bounds of the pixel centers,
                // not the outer edges of their cells.
                (component.minX() + 0.5) / width,
                (component.maxX() + 0.5) / width,
                (component.minY() + 0.5) / height,
                (component.maxY() + 0.5) / height,
                1);
    }

    static List<CanvasArrayRecord> compilePortableArrays(
            CanvasDocument document, List<CanvasGlyph> glyphs) {
        MagicArrayManager manager = new MagicArrayManager();
        java.util.LinkedHashMap<UUID, PositionedGlyph> positioned = new java.util.LinkedHashMap<>();
        int glyphId = 1;
        for (CanvasGlyph glyph : glyphs) {
            PositionedGlyph world = portableGlyph(document, glyph, glyphId++);
            positioned.put(glyph.glyphUuid(), world);
            manager.registerGlyph(world);
        }

        List<CanvasArrayRecord> arrays = new ArrayList<>();
        positioned.values().stream()
                .filter(glyph -> glyph.role() == SymbolRole.OUTER_CIRCLE)
                .sorted(Comparator.comparingDouble((PositionedGlyph glyph) -> glyph.bounds().area())
                        .thenComparingInt(PositionedGlyph::glyphId))
                .forEach(circle -> {
                    CompileResult<CompiledArray> result = ArrayNodeCompiler.compile(
                            ArrayAstBuilder.build(circle, manager), manager.opDefinitions());
                    if (!(result instanceof CompileResult.Success<CompiledArray> success)
                            || !(success.value().root() instanceof PersistentOp)) {
                        return;
                    }
                    List<UUID> bound = success.value().boundGlyphs().stream()
                            .map(PositionedGlyph::glyphUuid)
                            .toList();
                    arrays.add(new CanvasArrayRecord(
                            circle.glyphUuid(), bound,
                            CanvasArrayRecord.fingerprint(circle.glyphUuid(), bound),
                            success.value().color()));
                });
        return List.copyOf(arrays);
    }

    private static PositionedGlyph portableGlyph(
            CanvasDocument document, CanvasGlyph glyph, int glyphId) {
        SurfaceFrame surface = new SurfaceFrame(
                Vec3.ZERO,
                new Vec3(1.0, 0.0, 0.0),
                new Vec3(0.0, 1.0, 0.0),
                new Vec3(0.0, 0.0, -1.0));
        double minU = (glyph.minX() - 0.5) * document.physicalWidth();
        double maxU = (glyph.maxX() - 0.5) * document.physicalWidth();
        double minV = (0.5 - glyph.maxY()) * document.physicalHeight();
        double maxV = (0.5 - glyph.minY()) * document.physicalHeight();
        Vec3 physicalFront = surface.axisU()
                .scale(glyph.frontX() * document.physicalWidth())
                .add(surface.axisV().scale(-glyph.frontY() * document.physicalHeight()));
        Vec3 front = physicalFront.lengthSqr() > 1.0E-12
                ? physicalFront.normalize() : Vec3.ZERO;
        double[] extents = portableGlyphExtents(document, glyph, surface, front,
                new SurfaceFrame.SurfaceBounds(minU, maxU, minV, maxV));
        return new PositionedGlyph(
                glyph.glyphUuid(), glyphId, glyph.symbolId(), glyph.confidence(), glyph.role(),
                front, extents[0], extents[1], BlockPos.ZERO,
                minU, maxU, minV, maxV, Set.of(), Optional.empty(), surface);
    }

    private static double[] portableGlyphExtents(
            CanvasDocument document, CanvasGlyph glyph, SurfaceFrame surface,
            Vec3 front, SurfaceFrame.SurfaceBounds bounds) {
        Vec3 right = surface.normal().cross(front);
        if (front.lengthSqr() < 1.0E-12 || right.lengthSqr() < 1.0E-12) {
            return new double[]{0.0, 0.0};
        }
        int rasterWidth = document.resolutionWidth();
        int rasterHeight = document.resolutionHeight();
        int[] cells = glyph.rawCells();
        if (cells.length == 0) {
            return projectedExtents(surface, bounds, front, right);
        }
        double minFront = Double.POSITIVE_INFINITY;
        double maxFront = Double.NEGATIVE_INFINITY;
        double minRight = Double.POSITIVE_INFINITY;
        double maxRight = Double.NEGATIVE_INFINITY;
        for (int cell : cells) {
            if (cell < 0 || cell >= rasterWidth * rasterHeight) continue;
            int x = cell % rasterWidth;
            int y = cell / rasterWidth;
            Vec3 point = surface.world(
                    ((x + 0.5) / rasterWidth - 0.5) * document.physicalWidth(),
                    (0.5 - (y + 0.5) / rasterHeight) * document.physicalHeight());
            double along = point.dot(front);
            double across = point.dot(right);
            minFront = Math.min(minFront, along);
            maxFront = Math.max(maxFront, along);
            minRight = Math.min(minRight, across);
            maxRight = Math.max(maxRight, across);
        }
        return Double.isFinite(minFront)
                ? new double[]{maxFront - minFront, maxRight - minRight}
                : projectedExtents(surface, bounds, front, right);
    }

    private static double[] projectedExtents(
            SurfaceFrame surface, SurfaceFrame.SurfaceBounds bounds,
            Vec3 front, Vec3 right) {
        Vec3[] corners = {
                surface.world(bounds.minU(), bounds.minV()),
                surface.world(bounds.maxU(), bounds.minV()),
                surface.world(bounds.minU(), bounds.maxV()),
                surface.world(bounds.maxU(), bounds.maxV())
        };
        double minFront = Double.POSITIVE_INFINITY;
        double maxFront = Double.NEGATIVE_INFINITY;
        double minRight = Double.POSITIVE_INFINITY;
        double maxRight = Double.NEGATIVE_INFINITY;
        for (Vec3 corner : corners) {
            double along = corner.dot(front);
            double across = corner.dot(right);
            minFront = Math.min(minFront, along);
            maxFront = Math.max(maxFront, along);
            minRight = Math.min(minRight, across);
            maxRight = Math.max(maxRight, across);
        }
        return new double[]{maxFront - minFront, maxRight - minRight};
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
                            CanvasArrayRecord.fingerprint(root, bound),
                            array.scratchData().get("__array_color") instanceof Integer color
                                    ? color : 0xFFFFFFFF);
                })
                .toList();
    }
}
