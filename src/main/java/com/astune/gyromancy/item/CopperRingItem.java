package com.astune.gyromancy.item;

import com.astune.gyromancy.api.canvas.Carvable;
import com.astune.gyromancy.api.canvas.StampCanvasMaterial;
import com.astune.gyromancy.array.compile.GroupNode;
import com.astune.gyromancy.canvas.CanvasArrayRecord;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.canvas.CanvasGlyph;
import com.astune.gyromancy.registry.ModDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Carvable copper ring used as the rune-bearing intermediate for later recipes.
 *
 * <p>The ring owns a one-block, 64x64 canvas. Recognized runes and the
 * successfully compiled array cache are stored in the same document as the
 * raster, so the item remains self-contained when it is moved between slots or
 * saved to disk.</p>
 */
public final class CopperRingItem extends CarvingMaterials {
    public static final int CANVAS_SIZE = 64;
    public static final int CANVAS_RESOLUTION_SCALE =
            CANVAS_SIZE / CanvasDocument.PIXELS_PER_BLOCK;
    private static final double RING_OUTER_RADIUS = 31.0;
    private static final double RING_INNER_RADIUS = 14.0;

    private static final StampCanvasMaterial CARVING_MATERIAL = new StampCanvasMaterial(
            ResourceLocation.fromNamespaceAndPath(
                    "gyromancy", "textures/carve/copper_ring.png"),
            0xFFB873,
            1);
    private static final boolean[] RING_ALLOWED_PIXELS = createRingMask();

    public CopperRingItem() {
        super(new Properties()
                .stacksTo(1)
                .component(ModDataComponents.CARVING_DOCUMENT.get(), blankCanvas()));
    }

    /** Returns a fresh blank canvas so mutable raster arrays are never shared. */
    public static CanvasDocument blankCanvas() {
        int length = CANVAS_SIZE * CANVAS_SIZE;
        return new CanvasDocument(1, 1, CANVAS_RESOLUTION_SCALE,
                new int[length], new int[length], List.of(), List.of());
    }

    @Override
    public CanvasDocument carvingCanvas(ItemStack stack) {
        return stack.getOrDefault(
                ModDataComponents.CARVING_DOCUMENT.get(), blankCanvas());
    }

    @Override
    public CarvingProperties carvingProperties(ItemStack stack) {
        return new CarvingProperties(
                CARVING_MATERIAL, CANVAS_RESOLUTION_SCALE, RING_ALLOWED_PIXELS);
    }

    /** Returns whether a 64x64 canvas pixel lies on the copper ring surface. */
    public static boolean isCarvingPixel(int x, int y) {
        return x >= 0 && y >= 0 && x < CANVAS_SIZE && y < CANVAS_SIZE
                && RING_ALLOWED_PIXELS[y * CANVAS_SIZE + x];
    }

    private static boolean[] createRingMask() {
        boolean[] mask = new boolean[CANVAS_SIZE * CANVAS_SIZE];
        double center = (CANVAS_SIZE - 1) / 2.0;
        double outerSquared = RING_OUTER_RADIUS * RING_OUTER_RADIUS;
        double innerSquared = RING_INNER_RADIUS * RING_INNER_RADIUS;
        for (int y = 0; y < CANVAS_SIZE; y++) {
            for (int x = 0; x < CANVAS_SIZE; x++) {
                double dx = x - center;
                double dy = y - center;
                double distanceSquared = dx * dx + dy * dy;
                mask[y * CANVAS_SIZE + x] = distanceSquared <= outerSquared
                        && distanceSquared >= innerSquared;
            }
        }
        return mask;
    }

    @Override
    public void setCarvedRunes(ItemStack stack, List<CanvasGlyph> runes) {
        CanvasDocument current = carvingCanvas(stack);
        stack.set(ModDataComponents.CARVING_DOCUMENT.get(),
                current.withCompileCache(runes, current.arrays()));
    }

    @Override
    public void setCompiledAst(ItemStack stack, List<GroupNode> asts) {
        CanvasDocument current = carvingCanvas(stack);
        List<CanvasArrayRecord> arrays = Carvable.arrayRecordsFromAsts(asts);
        stack.set(ModDataComponents.CARVING_DOCUMENT.get(),
                current.withCompileCache(current.glyphs(), arrays));
    }

    @Override
    public void applyCarving(ItemStack stack,
                             CanvasDocument carvingDocument,
                             CanvasDocument compiledDocument,
                             List<GroupNode> asts) {
        List<CanvasArrayRecord> arrays = compiledDocument.arrays().isEmpty()
                ? Carvable.arrayRecordsFromAsts(asts)
                : compiledDocument.arrays();
        stack.set(ModDataComponents.CARVING_DOCUMENT.get(),
                carvingDocument.withCompileCache(compiledDocument.glyphs(), arrays));
    }
}
