package com.astune.gyromancy;

import com.astune.gyromancy.command.DebugCommands;
import com.astune.gyromancy.array.MagicArrayDetector;
import com.astune.gyromancy.compile.operator.TriggerOp;
import com.astune.gyromancy.element.ElementChunkEventHandler;
import com.astune.gyromancy.element.ElementTickProcessor;
import com.astune.gyromancy.registry.*;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

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
        ModRecipes.RECIPE_SERIALIZERS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModSymbols.register(modEventBus);

        // ── NeoForge.EVENT_BUS — explicit (no @EventBusSubscriber) ──
        NeoForge.EVENT_BUS.register(this); // picks up @SubscribeEvent instance methods

        // Server tick for element processing
        NeoForge.EVENT_BUS.<ServerTickEvent.Post>addListener(
                e -> ElementTickProcessor.onServerTick(e));
        NeoForge.EVENT_BUS.<ServerTickEvent.Post>addListener(
                e -> MagicArrayDetector.onServerTick(e));
        NeoForge.EVENT_BUS.<ServerTickEvent.Pre>addListener(
                TriggerOp::onServerTick);

        // Chunk lifecycle for element tracking
        NeoForge.EVENT_BUS.<ChunkEvent.Load>addListener(
                e -> {
                    ElementChunkEventHandler.onChunkLoad(e);
                    if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                            && e.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk) {
                        MagicArrayDetector.onChunkLoad(level, chunk);
                    }
                });
        NeoForge.EVENT_BUS.<ChunkEvent.Unload>addListener(
                e -> ElementChunkEventHandler.onChunkUnload(e));

        LOGGER.info("[Gyromancy] Registered on NeoForge.EVENT_BUS: tick, chunk, commands");

        // ── Config ──
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("[Gyromancy] commonSetup — element system ready");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[Gyromancy] Server starting");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        DebugCommands.registerServer(event);
        LOGGER.info("[Gyromancy] Server /gyromancy set registered");
    }
}
