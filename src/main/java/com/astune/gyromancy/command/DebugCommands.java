package com.astune.gyromancy.command;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.client.ElementDebugRenderer;
import com.astune.gyromancy.element.ElementStorageManager;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.server.command.EnumArgument;

/**
 * Debug commands for the Gyromancy element system.
 * <ul>
 *   <li>{@code /gyromancy debug true|false} — toggle element concentration debug overlay (client-side)</li>
 *   <li>{@code /gyromancy set <element> <value> <pos>} — set element concentration at a position (server-side)</li>
 * </ul>
 */
public final class DebugCommands {

    private DebugCommands() {}

    // ── Server command: /gyromancy set ──

    public static void registerServer(RegisterCommandsEvent event) {
        var node = Commands.literal("gyromancy")
                .then(Commands.literal("set")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("element", EnumArgument.enumArgument(ElementType.class))
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0f, 1f))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(DebugCommands::executeSet)
                                        )
                                )
                        )
                );

        event.getDispatcher().register(node);
    }

    private static int executeSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ElementType element = ctx.getArgument("element", ElementType.class);
        float value = FloatArgumentType.getFloat(ctx, "value");
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");

        ElementConcentrations current = ElementStorageManager.INSTANCE.get(source.getLevel(), pos);
        ElementConcentrations updated = current.withValue(element, Math.clamp(value, 0f, 1f));
        ElementStorageManager.INSTANCE.set(source.getLevel(), pos, updated);

        source.sendSuccess(() -> Component.translatable(
                "commands.gyromancy.set.success",
                element.name(), String.format("%.2f", value), pos.toShortString()
        ), true);

        Gyromancy.LOGGER.info("[Gyromancy] Set {}={} at {}", element.name(), value, pos.toShortString());
        return 1;
    }

    // ── Client command: /gyromancy debug ──

    @EventBusSubscriber(modid = Gyromancy.MODID, value = Dist.CLIENT)
    public static final class ClientCommands {

        private ClientCommands() {}

        @SubscribeEvent
        static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
            var node = Commands.literal("gyromancy")
                    .then(Commands.literal("debug")
                            .then(Commands.argument("state", BoolArgumentType.bool())
                                    .executes(ctx -> {
                                        boolean state = BoolArgumentType.getBool(ctx, "state");
                                        ElementDebugRenderer.setEnabled(state);
                                        ctx.getSource().sendSuccess(
                                                () -> Component.literal("Element debug overlay: " + (state ? "ON" : "OFF")),
                                                false
                                        );
                                        return 1;
                                    })
                            )
                    );

            event.getDispatcher().register(node);
        }
    }
}
