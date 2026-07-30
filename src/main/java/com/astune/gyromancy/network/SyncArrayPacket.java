package com.astune.gyromancy.network;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.client.effect.ClientRayEffects;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public record SyncArrayPacket(List<ArrayData> arrays) implements CustomPacketPayload {
    private static final double BEAM_HEIGHT = 1.0;
    private static final Set<UUID> knownArrays = ConcurrentHashMap.newKeySet();

    public static final Type<SyncArrayPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "sync_arrays"));

    public static final StreamCodec<ByteBuf, SyncArrayPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncArrayPacket decode(ByteBuf buf) {
            int count = buf.readInt();
            java.util.ArrayList<ArrayData> arrays = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                UUID id = new UUID(buf.readLong(), buf.readLong());
                int color = buf.readInt();
                int compilationEffectTicks = buf.readInt();
                int partCount = buf.readInt();
                java.util.ArrayList<BlockData> parts = new java.util.ArrayList<>(partCount);
                for (int j = 0; j < partCount; j++) {
                    Vec3 center = readVec3(buf);
                    Direction face = Direction.values()[buf.readUnsignedByte() % Direction.values().length];
                    Vec3 sourceU = readVec3(buf);
                    Vec3 sourceV = readVec3(buf);
                    int width = buf.readInt();
                    int height = buf.readInt();
                    byte[] mask = new byte[buf.readInt()];
                    buf.readBytes(mask);
                    parts.add(new BlockData(center, face, sourceU, sourceV, width, height, mask));
                }
                arrays.add(new ArrayData(id, color, compilationEffectTicks, parts));
            }
            return new SyncArrayPacket(arrays);
        }

        @Override
        public void encode(ByteBuf buf, SyncArrayPacket packet) {
            buf.writeInt(packet.arrays().size());
            for (ArrayData array : packet.arrays()) {
                buf.writeLong(array.id().getMostSignificantBits());
                buf.writeLong(array.id().getLeastSignificantBits());
                buf.writeInt(array.color());
                buf.writeInt(array.compilationEffectTicks());
                buf.writeInt(array.parts().size());
                for (BlockData part : array.parts()) {
                    writeVec3(buf, part.center());
                    buf.writeByte(part.face().ordinal());
                    writeVec3(buf, part.sourceU());
                    writeVec3(buf, part.sourceV());
                    buf.writeInt(part.width());
                    buf.writeInt(part.height());
                    buf.writeInt(part.mask().length);
                    buf.writeBytes(part.mask());
                }
            }
        }
    };

    public static void handleClient(SyncArrayPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Set<UUID> next = new HashSet<>();
            for (ArrayData array : packet.arrays()) {
                next.add(array.id());
                boolean firstSeen = knownArrays.add(array.id());
                if (!firstSeen || array.compilationEffectTicks() <= 0) continue;
                for (int partIndex = 0; partIndex < array.parts().size(); partIndex++) {
                    BlockData part = array.parts().get(partIndex);
                    Vec3 rayDir = Vec3.atLowerCornerOf(part.face().getNormal());
                    ClientRayEffects.spawnForLifecycle(
                            array.id(), partIndex,
                            part.center(), part.face(), rayDir,
                            part.sourceU(), part.sourceV(), part.mask(), part.width(), part.height(),
                            ignored -> 0xFFFFFFFF, array.color(),
                            array.compilationEffectTicks(), BEAM_HEIGHT);
                }
            }
            Set<UUID> removed = new HashSet<>(knownArrays);
            removed.removeAll(next);
            removed.forEach(ClientRayEffects::stopLifecycle);
            knownArrays.retainAll(next);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void resetClientState() {
        knownArrays.clear();
    }

    private static Vec3 readVec3(ByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private static void writeVec3(ByteBuf buf, Vec3 vec) {
        buf.writeDouble(vec.x);
        buf.writeDouble(vec.y);
        buf.writeDouble(vec.z);
    }

    public record ArrayData(
            UUID id,
            int color,
            int compilationEffectTicks,
            List<BlockData> parts
    ) {}

    public record BlockData(Vec3 center, Direction face, Vec3 sourceU, Vec3 sourceV,
                            int width, int height, byte[] mask) {}
}
