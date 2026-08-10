package com.astune.gyromancy.api.canvas;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.ArrayNodeCompiler;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.CompiledArray;
import com.astune.gyromancy.array.compile.GroupNode;
import com.astune.gyromancy.canvas.CanvasArrayRecord;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.canvas.CanvasGlyph;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Opt-in contract for items which can receive rune engravings.
 *
 * <p>The carving table deliberately does not know how an item stores its
 * result. Implementations provide the surface description and receive the
 * recognized runes and successfully compiled ASTs through the two setter
 * methods below. This keeps item persistence in the item implementation,
 * rather than adding a universal data component with assumptions about every
 * carvable item.
 */
public interface Carvable {

    /**
     * Returns the canvas currently stored by this item. The carving screen
     * uses it as its initial raster when the item is inserted. Items which do
     * not persist a canvas start from a blank matrix by default.
     */
    default CanvasDocument carvingCanvas(ItemStack stack) {
        return CanvasDocument.blank(1, 1);
    }

    /** Returns the material, fixed resolution, and allowed-pixel mask. */
    CarvingProperties carvingProperties(ItemStack stack);

    /** Stores every recognized rune, including runes outside compiled circles. */
    void setCarvedRunes(ItemStack stack, List<CanvasGlyph> runes);

    /** Stores the ASTs of the circles which compiled successfully. */
    void setCompiledAst(ItemStack stack, List<GroupNode> asts);

    /**
     * Applies the complete result in a stable order. Item implementations may
     * override this when their persistent representation is not the rune/AST
     * representation used by the default carving flow.
     */
    default void applyCarving(ItemStack stack,
                              CanvasDocument carvingDocument,
                              CanvasDocument compiledDocument,
                              List<GroupNode> asts) {
        setCarvedRunes(stack, compiledDocument.glyphs());
        setCompiledAst(stack, asts);
    }

    /**
     * Compatibility overload for callers which only have the compiled
     * document. New carving-table code should use the overload which also
     * receives the raw carving canvas.
     */
    default void applyCarving(ItemStack stack,
                              CanvasDocument compiledDocument,
                              List<GroupNode> asts) {
        applyCarving(stack, compiledDocument, compiledDocument, asts);
    }

    /**
     * Converts compiled AST roots into the compact, codec-friendly cache used
     * by the built-in canvas-like items. Custom items may persist the AST
     * directly instead.
     */
    static List<CanvasArrayRecord> arrayRecordsFromAsts(List<GroupNode> asts) {
        List<CanvasArrayRecord> records = new ArrayList<>();
        for (GroupNode ast : asts) {
            CompileResult<CompiledArray> result = ArrayNodeCompiler.compile(ast);
            if (!(result instanceof CompileResult.Success<CompiledArray> success)) continue;
            CompiledArray compiled = success.value();
            List<java.util.UUID> bound = compiled.boundGlyphs().stream()
                    .map(PositionedGlyph::glyphUuid)
                    .toList();
            java.util.UUID root = ast.boundary().glyphUuid();
            records.add(new CanvasArrayRecord(
                    root,
                    bound,
                    CanvasArrayRecord.fingerprint(root, bound),
                    compiled.color()));
        }
        return List.copyOf(records);
    }

    /** Immutable surface description supplied by one carvable item stack. */
    record CarvingProperties(
            StampCanvasMaterial material,
            int resolutionScale,
            boolean[] allowedPixels
    ) {
        public CarvingProperties {
            if (material == null) {
                throw new IllegalArgumentException("Carving material cannot be null");
            }
            if (resolutionScale < 1 || resolutionScale > CanvasDocument.MAX_RESOLUTION_SCALE) {
                throw new IllegalArgumentException("Carving resolution scale is out of range");
            }
            int expected = CanvasDocument.PIXELS_PER_BLOCK * resolutionScale;
            expected *= expected;
            if (allowedPixels == null || allowedPixels.length == 0) {
                allowedPixels = new boolean[expected];
                Arrays.fill(allowedPixels, true);
            } else if (allowedPixels.length != expected) {
                throw new IllegalArgumentException(
                        "Carving mask dimensions do not match the supplied resolution");
            } else {
                allowedPixels = allowedPixels.clone();
            }
        }

        @Override
        public boolean[] allowedPixels() {
            return allowedPixels.clone();
        }

        public boolean allows(int x, int y) {
            int width = CanvasDocument.PIXELS_PER_BLOCK * resolutionScale;
            return x >= 0 && y >= 0 && x < width && y < width
                    && allowedPixels[y * width + x];
        }
    }
}
