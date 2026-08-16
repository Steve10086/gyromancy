package com.astune.gyromancy;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_ELEMENT_TICK = BUILDER
            .comment("Whether to enable element sparing behaviour")
            .define("enableElementTick", true);

    public static final ModConfigSpec.BooleanValue ENABLE_ELEMENT_DIFFUSION = BUILDER
            .comment("Whether elements spread to nearby positions during element ticks")
            .define("enableElementDiffusion", true);

    public static final ModConfigSpec.BooleanValue ENABLE_SYMBOL_MATCH_DEBUG_OUTPUT = BUILDER
            .comment("Whether to save every glyph rasterized by the symbol matching pipeline for debugging")
            .define("enableSymbolMatchDebugOutput", false);

    public static final ModConfigSpec.DoubleValue PAINT_CAMERA_PAN_RANGE = BUILDER
            .comment("The side length, in blocks, of the paint camera panning area.")
            .defineInRange("paintCameraPanRange", 4.0, 0.0, 64.0);

    public static final ModConfigSpec.DoubleValue PAINT_CAMERA_ZOOM_RANGE = BUILDER
            .comment("The maximum extra camera distance, in blocks, used for paint camera zooming.")
            .defineInRange("paintCameraZoomRange", 2.0, 0.0, 16.0);

    static final ModConfigSpec SPEC = BUILDER.build();
}
