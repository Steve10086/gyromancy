package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.element.ElementStorageManager;
import com.astune.gyromancy.mixin.LevelChunkSectionAccessor;
import com.astune.gyromancy.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IceBallEntity extends ResistantBallEntity {
    private static final int EFFECT_INTERVAL = 10;
    private final Map<BlockPos, ResourceKey<Biome>> originalBiomes = new HashMap<>();

    public IceBallEntity(EntityType<IceBallEntity> type, Level level) {
        super(type, level);
    }

    public IceBallEntity(Level level, Vec3 pos, Vec3 velocity, float size) {
        this(ModEntities.ICE_BALL.get(), level);
        configure(pos, velocity, size);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || tickCount % EFFECT_INTERVAL != 0) return;
        List<BlockPos> positions = containedPositions(getTargetSize());
        exchangeColdElements(positions);
        applySnowyBiome(server, positions);
    }

    private void exchangeColdElements(List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            var current = ElementStorageManager.INSTANCE.get(level(), pos);
            long mana = Math.max(0L, current.get(ElementType.MANA));
            long fire = Math.max(0L, current.get(ElementType.FIRE));
            long removedFire = Math.min(fire, mana / 2L);
            mana -= removedFire * 2L;
            long time = Math.max(0L, current.get(ElementType.TIME));
            long removedTime = fire == removedFire ? Math.min(time, mana / 100L) : 0L;
            mana -= removedTime * 100L;
            ElementStorageManager.INSTANCE.set(level(), pos, current
                    .withValue(ElementType.FIRE, fire - removedFire)
                    .withValue(ElementType.TIME, time - removedTime)
                    .withValue(ElementType.MANA, mana));
        }
    }

    private void applySnowyBiome(ServerLevel level, List<BlockPos> positions) {
        Holder<Biome> snowy = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.SNOWY_PLAINS);
        Set<LevelChunk> changedChunks = new HashSet<>();
        for (BlockPos pos : positions) {
            BlockPos quart = new BlockPos(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
            LevelChunk chunk = level.getChunk(quart.getX() >> 2, quart.getZ() >> 2);
            int sectionIndex = chunk.getSectionIndex(quart.getY() << 2);
            if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) continue;
            var section = chunk.getSections()[sectionIndex];
            var accessor = (LevelChunkSectionAccessor)(Object)section;
            PalettedContainer<Holder<Biome>> biomes = mutableBiomes(accessor);
            int x = quart.getX() & 3;
            int y = quart.getY() & 3;
            int z = quart.getZ() & 3;
            Holder<Biome> old = biomes.get(x, y, z);
            originalBiomes.computeIfAbsent(quart, ignored -> old.unwrapKey().orElse(Biomes.PLAINS));
            if (!old.is(Biomes.SNOWY_PLAINS)) {
                biomes.getAndSetUnchecked(x, y, z, snowy);
                changedChunks.add(chunk);
            }
        }
        syncBiomes(level, changedChunks);
    }

    private void restoreBiomes() {
        if (!(level() instanceof ServerLevel server) || originalBiomes.isEmpty()) return;
        var registry = server.registryAccess().registryOrThrow(Registries.BIOME);
        Set<LevelChunk> changedChunks = new HashSet<>();
        for (var entry : originalBiomes.entrySet()) {
            BlockPos quart = entry.getKey();
            LevelChunk chunk = server.getChunk(quart.getX() >> 2, quart.getZ() >> 2);
            int sectionIndex = chunk.getSectionIndex(quart.getY() << 2);
            if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) continue;
            var accessor = (LevelChunkSectionAccessor)(Object)chunk.getSections()[sectionIndex];
            PalettedContainer<Holder<Biome>> biomes = mutableBiomes(accessor);
            Holder<Biome> current = biomes.get(quart.getX() & 3, quart.getY() & 3, quart.getZ() & 3);
            if (!current.is(Biomes.SNOWY_PLAINS)) continue;
            biomes.getAndSetUnchecked(quart.getX() & 3, quart.getY() & 3, quart.getZ() & 3,
                    registry.getHolderOrThrow(entry.getValue()));
            changedChunks.add(chunk);
        }
        originalBiomes.clear();
        syncBiomes(server, changedChunks);
    }

    private static PalettedContainer<Holder<Biome>> mutableBiomes(LevelChunkSectionAccessor accessor) {
        var existing = accessor.gyromancy$getBiomes();
        if (existing instanceof PalettedContainer<?> container) {
            @SuppressWarnings("unchecked")
            PalettedContainer<Holder<Biome>> result = (PalettedContainer<Holder<Biome>>)container;
            return result;
        }
        PalettedContainer<Holder<Biome>> copy = existing.recreate();
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < 4; z++) copy.getAndSetUnchecked(x, y, z, existing.get(x, y, z));
            }
        }
        accessor.gyromancy$setBiomes(copy);
        return copy;
    }

    private static void syncBiomes(ServerLevel level, Set<LevelChunk> chunks) {
        if (chunks.isEmpty()) return;
        var packet = ClientboundChunksBiomesPacket.forChunks(List.copyOf(chunks));
        level.players().forEach(player -> player.connection.send(packet));
    }

    @Override
    public void remove(RemovalReason reason) {
        restoreBiomes();
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ListTag entries = tag.getList("OriginalBiomes", Tag.TAG_COMPOUND);
        for (Tag value : entries) {
            CompoundTag entry = (CompoundTag)value;
            BlockPos quart = new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z"));
            originalBiomes.put(quart, ResourceKey.create(Registries.BIOME, ResourceLocation.parse(entry.getString("Biome"))));
        }
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ListTag entries = new ListTag();
        originalBiomes.forEach((quart, biome) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("X", quart.getX());
            entry.putInt("Y", quart.getY());
            entry.putInt("Z", quart.getZ());
            entry.putString("Biome", biome.location().toString());
            entries.add(entry);
        });
        tag.put("OriginalBiomes", entries);
    }
}
