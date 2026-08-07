package com.astune.gyromancy.network;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.item.CompassItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server synchronization for a compass radius changed by client scroll input. */
public record CompassRadiusPacket(int slot, double radius) implements CustomPacketPayload {
    public static final Type<CompassRadiusPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "compass_radius"));

    public static final StreamCodec<ByteBuf, CompassRadiusPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CompassRadiusPacket::slot,
            ByteBufCodecs.DOUBLE, CompassRadiusPacket::radius,
            CompassRadiusPacket::new
    );

    public static void handleServer(CompassRadiusPacket packet, IPayloadContext context) {
        if (!context.flow().isServerbound()) return;
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (packet.slot() != player.getInventory().selected
                    || packet.slot() < 0 || packet.slot() >= player.getInventory().items.size()) {
                return;
            }
            ItemStack stack = player.getInventory().getItem(packet.slot());
            if (stack.getItem() instanceof CompassItem) {
                CompassItem.setRadius(stack, packet.radius());
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
