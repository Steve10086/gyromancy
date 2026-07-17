package com.astune.gyromancy.command;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.SymbolTemplate;
import com.astune.gyromancy.element.ElementChunkEventHandler;
import com.astune.gyromancy.element.ElementStorageManager;
import com.astune.gyromancy.element.IElementChunkAccessor;
import com.astune.gyromancy.entity.ball.OldFireballEntity;
import com.astune.gyromancy.network.SyncDebugElementPacket;
import com.astune.gyromancy.registry.GyromancyRegistries;
import com.astune.gyromancy.symbol.SkeletonMatcher;
import com.astune.gyromancy.util.TemplateLoader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.command.EnumArgument;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DebugCommands {

    private DebugCommands() {}
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    public static void registerServer(RegisterCommandsEvent event) {
        var node = Commands.literal("gyromancy")
                .then(Commands.literal("set")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("element", EnumArgument.enumArgument(ElementType.class))
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(DebugCommands::executeSet)
                                        )
                                )
                        )
                )
                .then(Commands.literal("clear")
                        .requires(src -> src.hasPermission(2))
                        .executes(DebugCommands::executeClear)
                )
                .then(Commands.literal("debug_fireball")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("position", Vec3Argument.vec3())
                                .executes(DebugCommands::executeDebugFireball)
                        )
                )
                .then(Commands.literal("test")
                        .then(Commands.literal("match")
                                .executes(DebugCommands::executeTestMatch)
                        )
                );

        event.getDispatcher().register(node);
    }

    private static int executeSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ElementType element = ctx.getArgument("element", ElementType.class);
        long value = IntegerArgumentType.getInteger(ctx, "value");
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");

        ElementConcentrations current = ElementStorageManager.INSTANCE.get(source.getLevel(), pos);
        ElementConcentrations updated = current.withValue(element, value);
        ElementStorageManager.INSTANCE.set(source.getLevel(), pos, updated);

        ServerPlayer player = source.getPlayerOrException();
        long[] vals = updated.values();
        long[] derivs = updated.derivatives();
        PacketDistributor.sendToPlayer(player,
                new SyncDebugElementPacket(List.of(pos), vals, derivs));

        Gyromancy.LOGGER.info("[Gyromancy] Set {}={} at {}",
                element, value, pos.toShortString());
        source.sendSuccess(() -> Component.literal(
                "[Gyromancy] Set " + element.name() + " = " + value + " at " + pos.toShortString()
        ), true);
        return 1;
    }

    private static int executeClear(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        int cleared = 0;
        for (var level : source.getServer().getAllLevels()) {
            cleared += clearLoadedElementOverrides(level);
            cleared += clearSavedElementOverrides(level);
        }
        PacketDistributor.sendToAllPlayers(
                new SyncDebugElementPacket(List.of(), new long[0], new long[0]));
        final int c = cleared;
        source.sendSuccess(() -> Component.literal("[Gyromancy] Cleared " + c + " chunks"), true);
        return c;
    }

    private static int executeDebugFireball(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Vec3 pos = Vec3Argument.getVec3(ctx, "position");
        OldFireballEntity fireball = new OldFireballEntity(source.getLevel(), pos, Vec3.ZERO, Vec3.ZERO, 2.0F);
        fireball.setDebug(true);
        source.getLevel().addFreshEntity(fireball);
        source.sendSuccess(() -> Component.literal("[Gyromancy] Spawned old fireball at " + pos), true);
        return 1;
    }

    private static int clearLoadedElementOverrides(ServerLevel level) {
        int cleared = 0;
        for (ChunkPos cp : ElementChunkEventHandler.getActiveChunkPositions(level.dimension())) {
            LevelChunk chunk = level.getChunk(cp.x, cp.z);
            if (chunk instanceof IElementChunkAccessor a && a.gyromancy$hasElementOverrides()) {
                a.gyromancy$setElementOverrides(null);
                ElementChunkEventHandler.markInactive(chunk);
                cleared++;
            }
        }
        return cleared;
    }

    private static int clearSavedElementOverrides(ServerLevel level) {
        var regionDir = DimensionType.getStorageFolder(
                level.dimension(),
                level.getServer().getWorldPath(LevelResource.ROOT)
        ).resolve("region");
        if (!java.nio.file.Files.isDirectory(regionDir)) return 0;

        int cleared = 0;
        try (ChunkStorage storage = new ChunkStorage(
                new RegionStorageInfo("gyromancy", level.dimension(), "chunk"),
                regionDir,
                level.getServer().getFixerUpper(),
                false
        )) {
            try (var files = java.nio.file.Files.list(regionDir)) {
                for (var path : files.toList()) {
                    Matcher matcher = REGION_FILE.matcher(path.getFileName().toString());
                    if (!matcher.matches()) continue;
                    cleared += clearRegionFile(storage, Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
                }
            }
            storage.flushWorker();
        } catch (IOException e) {
            Gyromancy.LOGGER.error("[Gyromancy] Failed to clear saved element overrides in {}", regionDir, e);
        }
        return cleared;
    }

    private static int clearRegionFile(ChunkStorage storage, int regionX, int regionZ) {
        int cleared = 0;
        for (int dx = 0; dx < 32; dx++) {
            for (int dz = 0; dz < 32; dz++) {
                ChunkPos pos = new ChunkPos(regionX * 32 + dx, regionZ * 32 + dz);
                try {
                    Optional<CompoundTag> tag = storage.read(pos).join();
                    if (tag.isPresent() && removeElementOverrideAttachment(tag.get())) {
                        storage.write(pos, tag.get()).join();
                        cleared++;
                    }
                } catch (RuntimeException e) {
                    Gyromancy.LOGGER.error("[Gyromancy] Failed to clear saved element overrides in chunk {}", pos, e);
                }
            }
        }
        return cleared;
    }

    private static boolean removeElementOverrideAttachment(CompoundTag chunkTag) {
        if (!chunkTag.contains(AttachmentHolder.ATTACHMENTS_NBT_KEY)) return false;

        CompoundTag attachments = chunkTag.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY);
        String key = ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "element_overrides").toString();
        if (!attachments.contains(key)) return false;

        attachments.remove(key);
        if (attachments.isEmpty()) chunkTag.remove(AttachmentHolder.ATTACHMENTS_NBT_KEY);
        return true;
    }

    // ═══════════════════════ /gyromancy test match ═══════════════════════

    private static int executeTestMatch(CommandContext<CommandSourceStack> ctx) {
        String dir = "/assets/gyromancy/textures/symbol/";
        String[] names = {
                "circle_outer", "square", "triangle", "star", "figure_8",
                "fire_symbol", "water_symbol", "earth_symbol", "wind_symbol"
        };

        Gyromancy.LOGGER.info("=== MATCHING TEST ===");
        ctx.getSource().sendSystemMessage(Component.literal("=== /gyromancy test match (see server log) ==="));

        int correct = 0;
        for (String name : names) {
            int[][] testImg = TemplateLoader.load(dir + name + ".png");
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("gyromancy", name);
            SymbolTemplate expected = GyromancyRegistries.SYMBOL.get(id);
            if (expected == null) { Gyromancy.LOGGER.info("  {} : NOT REGISTERED", name); continue; }

            float bestOther = 0f;
            String bestOtherName = "";
            float selfScore = 0f;

            SkeletonMatcher matcher = SkeletonMatcher.getInstance();
            for (SymbolTemplate tpl : GyromancyRegistries.SYMBOL) {
                float conf = matcher.matchOne(testImg, tpl.id());
                if (tpl.id().equals(id)) {
                    selfScore = conf;
                } else if (conf > bestOther) {
                    bestOther = conf;
                    bestOtherName = tpl.id().toString();
                }
            }

            boolean pass = selfScore > bestOther;
            if (pass) correct++;
            Gyromancy.LOGGER.info("  {} : self={} bestOther={} ({}) => {}",
                    name,
                    String.format("%.3f", selfScore),
                    String.format("%.3f", bestOther), bestOtherName,
                    pass ? "PASS" : "FAIL");
        }
        final int c = correct;
        final int total = names.length;
        Gyromancy.LOGGER.info("=== {}/{} PASS ===", c, total);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Test: " + c + "/" + total + " correct (see server log)"), false);
        return c;
    }
}
