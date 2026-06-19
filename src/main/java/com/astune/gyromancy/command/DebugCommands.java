package com.astune.gyromancy.command;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.element.ElementChunkEventHandler;
import com.astune.gyromancy.element.ElementStorageManager;
import com.astune.gyromancy.element.IElementChunkAccessor;
import com.astune.gyromancy.network.SyncDebugElementPacket;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.command.EnumArgument;

import java.util.ArrayList;
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

        // Send to client for debug overlay
        ServerPlayer player = source.getPlayerOrException();
        long[] vals = updated.values();
        long[] derivs = updated.derivatives();
        PacketDistributor.sendToPlayer(player,
                new SyncDebugElementPacket(List.of(pos), vals, derivs));

        boolean isOverridden = ElementStorageManager.INSTANCE.isOverridden(source.getLevel(), pos);
        Gyromancy.LOGGER.info("[Gyromancy] Set {}={} at {} | overridden={} | sent to {}",
                element, value, pos.toShortString(), isOverridden, player.getName().getString());

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

        final int finalCleared = cleared;
        Gyromancy.LOGGER.info("[Gyromancy] Cleared {} chunks — all element overrides reset", finalCleared);
        source.sendSuccess(() -> Component.literal(
                "[Gyromancy] Cleared " + finalCleared + " chunks"
        ), true);
        return finalCleared;
    }
}
