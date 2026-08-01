package com.astune.gyromancy.network;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.canvas.StampCanvasMaterial;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.item.StampItem;
import com.astune.gyromancy.registry.ModDataComponents;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Saves a carving raster directly into the stamp stack that opened the editor. */
public record SubmitStampCarvingPacket(
        InteractionHand hand,
        int baseRevision,
        int scale,
        int[] colors,
        int[] effects
) implements CustomPacketPayload {
    public static final Type<SubmitStampCarvingPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Gyromancy.MODID, "submit_stamp_carving"));

    public static final StreamCodec<ByteBuf, SubmitStampCarvingPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SubmitStampCarvingPacket decode(ByteBuf buffer) {
                    InteractionHand hand = buffer.readBoolean()
                            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                    int revision = buffer.readInt();
                    CanvasPacketCodec.Raster raster = CanvasPacketCodec.decodeRaster(buffer);
                    return new SubmitStampCarvingPacket(
                            hand, revision, raster.scale(),
                            raster.colors(), raster.effects());
                }

                @Override
                public void encode(ByteBuf buffer, SubmitStampCarvingPacket packet) {
                    buffer.writeBoolean(packet.hand == InteractionHand.OFF_HAND);
                    buffer.writeInt(packet.baseRevision);
                    CanvasPacketCodec.encodeRaster(
                            buffer, packet.scale, packet.colors, packet.effects);
                }
            };

    public SubmitStampCarvingPacket {
        colors = colors.clone();
        effects = effects.clone();
    }

    public static void handleServer(SubmitStampCarvingPacket packet,
                                    IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> applyServer(player, packet));
    }

    private static void applyServer(ServerPlayer player,
                                    SubmitStampCarvingPacket packet) {
        ItemStack stack = player.getItemInHand(packet.hand);
        if (!(stack.getItem() instanceof StampItem)) return;

        CanvasDocument current = stack.getOrDefault(
                ModDataComponents.STAMP_CANVAS.get(), CanvasDocument.blank(1, 1));
        int revision = stack.getOrDefault(ModDataComponents.STAMP_REVISION.get(), 0);
        if (revision != packet.baseRevision) {
            sendSnapshot(player, packet.hand, stack, current, revision);
            return;
        }

        CanvasDocument updated;
        try {
            updated = new CanvasDocument(
                    current.physicalWidth(),
                    current.physicalHeight(),
                    packet.scale,
                    packet.colors,
                    packet.effects,
                    List.of(),
                    List.of());
        } catch (IllegalArgumentException ignored) {
            sendSnapshot(player, packet.hand, stack, current, revision);
            return;
        }

        stack.set(ModDataComponents.STAMP_CANVAS.get(), updated);
        stack.set(ModDataComponents.STAMP_REVISION.get(), revision + 1);
        stack.remove(ModDataComponents.STAMP_FACE.get());
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    private static void sendSnapshot(ServerPlayer player,
                                     InteractionHand hand,
                                     ItemStack stack,
                                     CanvasDocument document,
                                     int revision) {
        StampCanvasMaterial material = stack.getOrDefault(
                ModDataComponents.STAMP_MATERIAL.get(), StampCanvasMaterial.DEFAULT);
        PacketDistributor.sendToPlayer(player,
                new StampEditorSnapshotPacket(hand, revision, document, material));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
