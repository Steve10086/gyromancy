package com.astune.gyromancy.network;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.client.effect.CrystalSpawnEffects;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Plays the one-shot crystal spawn effect at a position that was just chosen
 * for a crystal. The block itself only appears {@code PLACE_DELAY_TICKS} later.
 */
public record CrystalSpawnFxPacket(BlockPos pos, int elementIndex)
        implements CustomPacketPayload {
    public static final Type<CrystalSpawnFxPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "crystal_spawn_fx"));

    public static final StreamCodec<ByteBuf, CrystalSpawnFxPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CrystalSpawnFxPacket decode(ByteBuf buf) {
            return new CrystalSpawnFxPacket(BlockPos.of(buf.readLong()), buf.readUnsignedByte());
        }

        @Override
        public void encode(ByteBuf buf, CrystalSpawnFxPacket packet) {
            buf.writeLong(packet.pos().asLong());
            buf.writeByte(packet.elementIndex());
        }
    };

    /** Sends the spawn notice to every player tracking the position's chunk. */
    public static void broadcast(ServerLevel level, BlockPos pos, ElementType element) {
        PacketDistributor.sendToPlayersTrackingChunk(
                level, new ChunkPos(pos), new CrystalSpawnFxPacket(pos, element.ordinal()));
    }

    public static void handleClient(CrystalSpawnFxPacket packet, IPayloadContext context) {
        context.enqueueWork(
                () -> CrystalSpawnEffects.onSpawnFx(packet.pos(), packet.elementIndex()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
