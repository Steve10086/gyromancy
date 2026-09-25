package com.astune.gyromancy.network;

import com.astune.gyromancy.Gyromancy;
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
 * Tells tracking clients that a crystal block was just placed so they start its
 * grow-in animation.
 */
public record CrystalGrowthPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<CrystalGrowthPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "crystal_growth"));

    public static final StreamCodec<ByteBuf, CrystalGrowthPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public CrystalGrowthPacket decode(ByteBuf buf) {
                    return new CrystalGrowthPacket(BlockPos.of(buf.readLong()));
                }

                @Override
                public void encode(ByteBuf buf, CrystalGrowthPacket packet) {
                    buf.writeLong(packet.pos().asLong());
                }
            };

    /** Sends the grow notice to every player tracking the position's chunk. */
    public static void broadcast(ServerLevel level, BlockPos pos) {
        PacketDistributor.sendToPlayersTrackingChunk(
                level, new ChunkPos(pos), new CrystalGrowthPacket(pos));
    }

    public static void handleClient(CrystalGrowthPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> CrystalSpawnEffects.onGrow(packet.pos()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
