package com.astune.gyromancy.network;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.canvas.Carvable;
import com.astune.gyromancy.canvas.CanvasCompileService;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.rune.RuneCarvingMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Applies one live carving snapshot to the item in the table slot. */
public record SubmitRuneCarvingPacket(
        int containerId,
        int sequence,
        int scale,
        int[] colors,
        int[] effects
) implements CustomPacketPayload {
    public static final Type<SubmitRuneCarvingPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "submit_rune_carving"));

    public static final StreamCodec<ByteBuf, SubmitRuneCarvingPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SubmitRuneCarvingPacket decode(ByteBuf buffer) {
            int containerId = buffer.readInt();
            int sequence = buffer.readInt();
            CanvasPacketCodec.Raster raster = CanvasPacketCodec.decodeRaster(buffer);
            return new SubmitRuneCarvingPacket(containerId, sequence, raster.scale(),
                    raster.colors(), raster.effects());
        }

        @Override
        public void encode(ByteBuf buffer, SubmitRuneCarvingPacket packet) {
            buffer.writeInt(packet.containerId);
            buffer.writeInt(packet.sequence);
            CanvasPacketCodec.encodeRaster(buffer, packet.scale, packet.colors, packet.effects);
        }
    };

    public SubmitRuneCarvingPacket {
        colors = colors.clone();
        effects = effects.clone();
    }

    public static void handleServer(SubmitRuneCarvingPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> applyServer(player, packet));
    }

    private static void applyServer(ServerPlayer player, SubmitRuneCarvingPacket packet) {
        if (!(player.containerMenu instanceof RuneCarvingMenu menu)
                || menu.menuId() != packet.containerId()
                || !menu.stillValid(player)
                || !menu.acceptCarvingSequence(packet.sequence())) {
            return;
        }

        ItemStack stack = menu.carvingStack();
        if (!(stack.getItem() instanceof Carvable carvable)) return;

        Carvable.CarvingProperties properties;
        try {
            properties = carvable.carvingProperties(stack);
        } catch (RuntimeException exception) {
            Gyromancy.LOGGER.warn("Invalid carving properties for {}", stack.getItem(), exception);
            return;
        }
        if (properties.resolutionScale() != packet.scale) return;

        int width = CanvasDocument.PIXELS_PER_BLOCK * packet.scale;
        int expected = width * width;
        if (packet.colors.length != expected || packet.effects.length != expected) return;

        int[] colors = new int[expected];
        int[] effects = new int[expected];
        for (int index = 0; index < expected; index++) {
            int x = index % width;
            int y = index / width;
            int color = packet.colors[index];
            // Carving stores a binary marker for recognition. Tool-specific
            // effect values never cross the item persistence boundary.
            if (properties.allows(x, y) && ((color >>> 24) & 0xFF) != 0) {
                colors[index] = color;
                effects[index] = 1;
            }
        }

        try {
            CanvasDocument currentCanvas = carvable.carvingCanvas(stack);
            int physicalWidth = currentCanvas == null ? 1 : currentCanvas.physicalWidth();
            int physicalHeight = currentCanvas == null ? 1 : currentCanvas.physicalHeight();
            CanvasDocument submitted = new CanvasDocument(
                    physicalWidth, physicalHeight, packet.scale, colors, effects,
                    java.util.List.of(), java.util.List.of());
            CanvasDocument compiled = CanvasCompileService.compilePortable(submitted);
            carvable.applyCarving(stack, submitted, compiled,
                    CanvasCompileService.compilePortableAsts(compiled));
            menu.broadcastChanges();
        } catch (RuntimeException exception) {
            Gyromancy.LOGGER.warn("Rune carving compilation failed for {}",
                    stack.getItem(), exception);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
