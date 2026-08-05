package com.astune.gyromancy.network;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.geometry.SurfaceFrame;
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
                    SurfaceFrame surface = readSurfaceFrame(buf);
                    int width = buf.readInt();
                    int height = buf.readInt();
                    byte[] mask = new byte[buf.readInt()];
                    buf.readBytes(mask);
                    int sourceEntityId = buf.readInt();
                    parts.add(new BlockData(surface, center, face, sourceU, sourceV,
                            width, height, mask, sourceEntityId));
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
                    writeSurfaceFrame(buf, part.surface());
                    buf.writeInt(part.width());
                    buf.writeInt(part.height());
                    buf.writeInt(part.mask().length);
                    buf.writeBytes(part.mask());
                    buf.writeInt(part.sourceEntityId());
                }
            }
        }
    };

    public static void handleClient(SyncArrayPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Set<UUID> next = new HashSet<>();
            for (ArrayData array : packet.arrays()) {
                next.add(array.id());
                knownArrays.add(array.id());
                if (array.compilationEffectTicks() <= 0) continue;
                for (int partIndex = 0; partIndex < array.parts().size(); partIndex++) {
                    BlockData part = array.parts().get(partIndex);
                    Vec3 rayDir = part.surface().normal();
                    ClientRayEffects.spawnForLifecycle(
                            array.id(), partIndex,
                            part.center(), part.surface().normal(), rayDir,
                            part.sourceU(), part.sourceV(), part.mask(), part.width(), part.height(),
                            ignored -> 0xFFFFFFFF, array.color(),
                            array.compilationEffectTicks(), BEAM_HEIGHT);
                    if (part.sourceEntityId() >= 0) {
                        ClientRayEffects.bindLifecycleSource(
                                array.id(), partIndex, part.sourceEntityId());
                    }
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

    private static SurfaceFrame readSurfaceFrame(ByteBuf buf) {
        return new SurfaceFrame(readVec3(buf), readVec3(buf),
                readVec3(buf), readVec3(buf));
    }

    private static void writeSurfaceFrame(ByteBuf buf, SurfaceFrame surface) {
        writeVec3(buf, surface.origin());
        writeVec3(buf, surface.axisU());
        writeVec3(buf, surface.axisV());
        writeVec3(buf, surface.normal());
    }

    public record ArrayData(
            UUID id,
            int color,
            int compilationEffectTicks,
            List<BlockData> parts
    ) {}

    /**
     * {@code surface} is authoritative geometry. {@code face} is retained only
     * for callers and packet consumers that still use six-direction coordinates.
     */
    public record BlockData(SurfaceFrame surface, Vec3 center, Direction face,
                            Vec3 sourceU, Vec3 sourceV,
                            int width, int height, byte[] mask,
                            int sourceEntityId) {
        public BlockData(Vec3 center, Direction face, Vec3 sourceU, Vec3 sourceV,
                         int width, int height, byte[] mask) {
            this(SurfaceFrame.fromBlockFace(
                            net.minecraft.core.BlockPos.containing(center.subtract(
                                    Vec3.atLowerCornerOf(face.getNormal())
                                            .scale(SurfaceFrame.EPSILON))),
                            face),
                    center, face, sourceU, sourceV, width, height, mask, -1);
        }

        /** Compatibility constructor for code which supplied only a free normal. */
        public BlockData(Vec3 center, Direction face, Vec3 sourceU, Vec3 sourceV,
                         int width, int height, byte[] mask, Vec3 normal) {
            this(new SurfaceFrame(center, sourceU.normalize(), sourceV.normalize(), normal),
                    center, face, sourceU, sourceV, width, height, mask, -1);
        }
    }
}
