package com.astune.gyromancy.wand;

import com.astune.gyromancy.canvas.CanvasCompileService;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.item.CanvasItem;
import com.astune.gyromancy.registry.ModDataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Builds persistent slot-owned materials and compile caches from canvas inputs. */
public final class WandSlotCompiler {
    private WandSlotCompiler() {}

    public static WandSlotSnapshots refresh(
            WandSlotSnapshots previous, WandContents contents, WandLayout layout) {
        WandSlotSnapshots baseline = previous.withSize(layout.slotCount());
        WandContents inputs = contents.withSize(layout.totalCapacity());
        List<WandSlotSnapshot> next = new ArrayList<>(layout.slotCount());
        int entryIndex = 0;
        for (int slot = 0; slot < layout.slotCount(); slot++) {
            List<ItemStack> slotInputs = new ArrayList<>(layout.slotCapacity(slot));
            for (int entry = 0; entry < layout.slotCapacity(slot); entry++) {
                slotInputs.add(inputs.get(entryIndex++));
            }
            Optional<CanvasDocument> combined = combineSlot(slotInputs);
            next.add(refreshSnapshot(baseline.get(slot), combined));
        }
        return new WandSlotSnapshots(next);
    }

    static WandSlotSnapshot refreshSnapshot(
            WandSlotSnapshot previous, Optional<CanvasDocument> combined) {
        if (combined.isEmpty()) return WandSlotSnapshot.EMPTY;
        long sourceFingerprint = rasterFingerprint(combined.get());
        Optional<CanvasDocument> cached = previous.document();
        if (cached.isPresent() && previous.sourceFingerprint() == sourceFingerprint) {
            return previous;
        }
        return WandSlotSnapshot.of(
                CanvasCompileService.compilePortable(combined.get()), sourceFingerprint);
    }

    static long rasterFingerprint(CanvasDocument document) {
        long value = 0xcbf29ce484222325L;
        value = mix(value, document.physicalWidth());
        value = mix(value, document.physicalHeight());
        value = mix(value, document.resolutionScale());
        for (int color : document.colors()) value = mix(value, color);
        for (int effect : document.strokeEffects()) value = mix(value, effect);
        return value;
    }

    private static long mix(long current, long next) {
        return (current ^ next) * 0x100000001b3L;
    }

    static Optional<CanvasDocument> combineSlot(List<ItemStack> stacks) {
        List<CanvasDocument> documents = stacks.stream()
                .filter(stack -> stack.getItem() instanceof CanvasItem)
                .map(stack -> stack.getOrDefault(
                        ModDataComponents.CANVAS_DOCUMENT.get(), CanvasDocument.blank(1, 1)))
                .toList();
        return combineDocuments(documents);
    }

    static Optional<CanvasDocument> combineDocuments(List<CanvasDocument> documents) {
        if (documents.isEmpty()) return Optional.empty();

        int physicalWidth = documents.stream()
                .mapToInt(CanvasDocument::physicalWidth).max().orElse(1);
        int physicalHeight = documents.stream()
                .mapToInt(CanvasDocument::physicalHeight).max().orElse(1);
        int resolutionScale = 1;
        for (CanvasDocument document : documents) {
            int widthScale = ceilDiv(
                    document.resolutionScale() * physicalWidth,
                    document.physicalWidth());
            int heightScale = ceilDiv(
                    document.resolutionScale() * physicalHeight,
                    document.physicalHeight());
            resolutionScale = Math.max(resolutionScale, Math.max(widthScale, heightScale));
        }
        resolutionScale = Math.min(CanvasDocument.maxScale(), resolutionScale);

        int resolution = CanvasDocument.PIXELS_PER_BLOCK * resolutionScale;
        int[] colors = new int[resolution * resolution];
        int[] effects = new int[resolution * resolution];
        for (int y = 0; y < resolution; y++) {
            double physicalY = ((y + 0.5) / resolution - 0.5) * physicalHeight;
            for (int x = 0; x < resolution; x++) {
                double physicalX = ((x + 0.5) / resolution - 0.5) * physicalWidth;
                int target = y * resolution + x;
                for (CanvasDocument document : documents) {
                    double sourceX = physicalX / document.physicalWidth() + 0.5;
                    double sourceY = physicalY / document.physicalHeight() + 0.5;
                    if (sourceX < 0.0 || sourceX >= 1.0
                            || sourceY < 0.0 || sourceY >= 1.0) {
                        continue;
                    }
                    int sourceWidth = document.resolutionWidth();
                    int sourceHeight = document.resolutionHeight();
                    int pixelX = Math.min(sourceWidth - 1, (int) (sourceX * sourceWidth));
                    int pixelY = Math.min(sourceHeight - 1, (int) (sourceY * sourceHeight));
                    int source = pixelY * sourceWidth + pixelX;
                    int effect = document.strokeEffectAt(pixelX, pixelY);
                    if (effect <= 0) continue;
                    effects[target] = effect;
                    colors[target] = document.colorAt(pixelX, pixelY);
                }
            }
        }
        return Optional.of(new CanvasDocument(
                physicalWidth, physicalHeight, resolutionScale,
                colors, effects, List.of(), List.of()));
    }

    private static int ceilDiv(int numerator, int denominator) {
        return (numerator + denominator - 1) / denominator;
    }
}
