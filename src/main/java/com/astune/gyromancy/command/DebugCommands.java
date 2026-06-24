package com.astune.gyromancy.command;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.SymbolTemplate;
import com.astune.gyromancy.element.ElementChunkEventHandler;
import com.astune.gyromancy.element.ElementStorageManager;
import com.astune.gyromancy.element.IElementChunkAccessor;
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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.command.EnumArgument;

import java.util.List;

public final class DebugCommands {

    private DebugCommands() {}

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
            for (ChunkPos cp : ElementChunkEventHandler.getActiveChunkPositions(level.dimension())) {
                LevelChunk chunk = level.getChunk(cp.x, cp.z);
                if (chunk instanceof IElementChunkAccessor a) {
                    a.gyromancy$setElementOverrides(null);
                    ElementChunkEventHandler.markInactive(chunk);
                    cleared++;
                }
            }
        }
        PacketDistributor.sendToAllPlayers(
                new SyncDebugElementPacket(List.of(), new long[0], new long[0]));
        final int c = cleared;
        source.sendSuccess(() -> Component.literal("[Gyromancy] Cleared " + c + " chunks"), true);
        return c;
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
