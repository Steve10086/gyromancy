package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.canvas.StampCanvasMaterial;
import com.astune.gyromancy.api.ink.InkType;
import com.astune.gyromancy.api.ink.PenProperties;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.item.CompassContents;
import com.astune.gyromancy.wand.WandContents;
import com.astune.gyromancy.wand.WandSlotSnapshots;
import com.astune.painter.api.CanvasFace;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * DataComponentType registrations for Gyromancy items.
 * Data components store ink type, pen properties, and other item state.
 */
public final class ModDataComponents {

    private ModDataComponents() {}

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Gyromancy.MODID);

    /** Stores the ink type identifier on ink bottle items */
    public static final Supplier<DataComponentType<ResourceLocation>> INK_TYPE =
            DATA_COMPONENTS.register("ink_type", () ->
                    DataComponentType.<ResourceLocation>builder()
                            .persistent(ResourceLocation.CODEC)
                            .networkSynchronized(ResourceLocation.STREAM_CODEC)
                            .build()
            );

    /** Stores pen properties on pen/brush items */
    public static final Supplier<DataComponentType<PenProperties>> PEN_PROPERTIES =
            DATA_COMPONENTS.register("pen_properties", () ->
                    DataComponentType.<PenProperties>builder()
                            .persistent(PenProperties.CODEC)
                            .networkSynchronized(ByteBufCodecs.fromCodec(PenProperties.CODEC))
                            .build()
            );

    /** Persistent radius selected on compass items. */
    public static final Supplier<DataComponentType<Double>> COMPASS_RADIUS =
            DATA_COMPONENTS.register("compass_radius", () ->
                    DataComponentType.<Double>builder()
                            .persistent(Codec.DOUBLE)
                            .networkSynchronized(ByteBufCodecs.DOUBLE)
                            .build()
            );

    /** The single pen stored by a compass. */
    public static final Supplier<DataComponentType<CompassContents>> COMPASS_PEN =
            DATA_COMPONENTS.register("compass_pen", () ->
                    DataComponentType.<CompassContents>builder()
                            .persistent(CompassContents.CODEC)
                            .networkSynchronized(CompassContents.STREAM_CODEC)
                            .build()
            );

    /** Remaining ink charges on a pen item */
    public static final Supplier<DataComponentType<Integer>> INK_REMAINING =
            DATA_COMPONENTS.register("ink_remaining", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build()
            );

    /** Portable raster, glyph cache, and compiled-array cache for a canvas item. */
    public static final Supplier<DataComponentType<CanvasDocument>> CANVAS_DOCUMENT =
            DATA_COMPONENTS.register("canvas_document", () ->
                    DataComponentType.<CanvasDocument>builder()
                            .persistent(CanvasDocument.CODEC)
                            .networkSynchronized(ByteBufCodecs.fromCodec(CanvasDocument.CODEC))
                            .build()
            );

    /** 64x64 carving canvas and recognized rune cache stored by copper rings. */
    public static final Supplier<DataComponentType<CanvasDocument>> COPPER_RING_DOCUMENT =
            DATA_COMPONENTS.register("copper_ring_document", () ->
                    DataComponentType.<CanvasDocument>builder()
                            .persistent(CanvasDocument.CODEC)
                            .networkSynchronized(ByteBufCodecs.fromCodec(CanvasDocument.CODEC))
                            .build()
            );

    /** Painter canvas face captured by a stamp, including its pixel and effect data. */
    public static final Supplier<DataComponentType<CanvasFace>> STAMP_FACE =
            DATA_COMPONENTS.register("stamp_face", () ->
                    DataComponentType.<CanvasFace>builder()
                            .persistent(CanvasFace.CODEC)
                            .networkSynchronized(CanvasFace.STREAM_CODEC)
                            .build()
            );

    /** Entity-independent canvas stored inside a stamp. */
    public static final Supplier<DataComponentType<CanvasDocument>> STAMP_CANVAS =
            DATA_COMPONENTS.register("stamp_canvas", () ->
                    DataComponentType.<CanvasDocument>builder()
                            .persistent(CanvasDocument.CODEC)
                            .networkSynchronized(ByteBufCodecs.fromCodec(CanvasDocument.CODEC))
                    .build()
            );

    /** Canvas stacks stored by a wand menu. */
    public static final Supplier<DataComponentType<WandContents>> WAND_CONTENTS =
            DATA_COMPONENTS.register("wand_contents", () ->
                    DataComponentType.<WandContents>builder()
                            .persistent(WandContents.CODEC)
                            .networkSynchronized(WandContents.STREAM_CODEC)
                            .build()
            );

    /** Wand-owned combined slot materials and portable compilation caches. */
    public static final Supplier<DataComponentType<WandSlotSnapshots>> WAND_SLOT_SNAPSHOTS =
            DATA_COMPONENTS.register("wand_slot_snapshots", () ->
                    DataComponentType.<WandSlotSnapshots>builder()
                            .persistent(WandSlotSnapshots.CODEC)
                            .networkSynchronized(ByteBufCodecs.fromCodec(WandSlotSnapshots.CODEC))
                            .build()
            );

    /** Externally supplied surface texture and engraving mark properties. */
    public static final Supplier<DataComponentType<StampCanvasMaterial>> STAMP_MATERIAL =
            DATA_COMPONENTS.register("stamp_material", () ->
                    DataComponentType.<StampCanvasMaterial>builder()
                            .persistent(StampCanvasMaterial.CODEC)
                            .networkSynchronized(ByteBufCodecs.fromCodec(StampCanvasMaterial.CODEC))
                            .build()
            );

    /** Optimistic concurrency revision for stamp carving submissions. */
    public static final Supplier<DataComponentType<Integer>> STAMP_REVISION =
            DATA_COMPONENTS.register("stamp_revision", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build()
            );
}
