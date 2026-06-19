package com.astune.gyromancy.network;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.client.ElementDebugRenderer;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Full-snapshot sync: replaces the entire client debug dataset atomically. */
public record SyncDebugElementPacket(List<BlockPos> positions, long[] flatValues, long[] flatDerivatives)
        implements CustomPacketPayload {

    public static final Type<SyncDebugElementPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "sync_debug"));

    private static final int ELEMENTS = ElementConcentrations.SIZE;

    public static final StreamCodec<ByteBuf, SyncDebugElementPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncDebugElementPacket decode(ByteBuf buf) {
            int count = buf.readInt();
            var poses = new ArrayList<BlockPos>(count);
            long[] vals = new long[count * ELEMENTS];
            long[] derivs = new long[count * ELEMENTS];
            for (int i = 0; i < count; i++) {
                poses.add(BlockPos.STREAM_CODEC.decode(buf));
                for (int j = 0; j < ELEMENTS; j++) vals[i * ELEMENTS + j] = buf.readLong();
                for (int j = 0; j < ELEMENTS; j++) derivs[i * ELEMENTS + j] = buf.readLong();
            }
            return new SyncDebugElementPacket(poses, vals, derivs);
        }

        @Override
        public void encode(ByteBuf buf, SyncDebugElementPacket p) {
            buf.writeInt(p.positions.size());
            for (int i = 0; i < p.positions.size(); i++) {
                BlockPos.STREAM_CODEC.encode(buf, p.positions.get(i));
                for (int j = 0; j < ELEMENTS; j++) buf.writeLong(p.flatValues[i * ELEMENTS + j]);
                for (int j = 0; j < ELEMENTS; j++) buf.writeLong(p.flatDerivatives[i * ELEMENTS + j]);
            }
        }
    };

    public static void handleClient(SyncDebugElementPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var map = new java.util.concurrent.ConcurrentHashMap<BlockPos, ElementConcentrations>();
            for (int i = 0; i < packet.positions.size(); i++) {
                long[] vals = new long[ELEMENTS];
                long[] derivs = new long[ELEMENTS];
                int off = i * ELEMENTS;
                System.arraycopy(packet.flatValues, off, vals, 0, ELEMENTS);
                System.arraycopy(packet.flatDerivatives, off, derivs, 0, ELEMENTS);
                map.put(packet.positions.get(i), new ElementConcentrations(vals, derivs));
            }
            ElementDebugRenderer.replaceDebugData(map);
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
