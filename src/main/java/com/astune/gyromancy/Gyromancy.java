package com.astune.gyromancy;

import com.astune.gyromancy.command.DebugCommands;
import com.astune.gyromancy.registry.*;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(Gyromancy.MODID)
public class Gyromancy {

    public static final String MODID = "gyromancy";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Gyromancy(IEventBus modEventBus, ModContainer modContainer) {
        // ── FML lifecycle ──
        modEventBus.addListener(this::commonSetup);

        // ── Deferred Registers ──
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModAttachments.ATTACHMENTS.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);

        // ── NeoForge event bus (server lifecycle) ──
        NeoForge.EVENT_BUS.register(this);

        // ── Config ──
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("[Gyromancy] Initializing magical array systems...");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("[Gyromancy] DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }
        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());
        Config.ITEM_STRINGS.get().forEach(item -> LOGGER.info("[Gyromancy] ITEM >> {}", item));
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[Gyromancy] Server starting — element system initializing...");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        DebugCommands.registerServer(event);
    }
}
