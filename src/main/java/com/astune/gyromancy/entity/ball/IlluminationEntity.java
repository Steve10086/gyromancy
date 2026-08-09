package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.compile.operator.ElementVolumeOp;
import com.astune.gyromancy.registry.ModEntities;
import com.astune.gyromancy.util.MagicBallGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import net.minecraft.server.level.ServerLevel;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A spherical light source whose temporary light blocks follow its movement. */
public final class IlluminationEntity extends MagicBallEntity {
    private static final int DEFAULT_LIFETIME = 500;
    private static final int LIGHT_LEVEL = 15;
    private static final String LIGHTS_TAG = "PlacedLights";
    private static final Map<ServerLevel, Map<BlockPos, Set<UUID>>> LIGHT_REFERENCES =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private final Set<BlockPos> placedLightPositions = new HashSet<>();
    private int lifetime = DEFAULT_LIFETIME;

    public IlluminationEntity(EntityType<IlluminationEntity> type, Level level) {
        super(type, level, ElementType.LIGHT);
    }

    public IlluminationEntity(Level level, Vec3 pos, Vec3 velocity,
                              Vec3 acceleration, float size) {
        super(ModEntities.ILLUMINATION.get(), level, ElementType.LIGHT, velocity, acceleration);
        setBallSize(size);
        this.acceleration = Vec3.ZERO;
        setPos(pos);
    }

    public void setLifetime(int lifetime) {
        this.lifetime = Math.max(1, lifetime);
    }

    @Override
    protected List<? extends com.astune.gyromancy.compile.operator.EntityPayload> defaultPayload() {
        return List.of(ElementVolumeOp.stability(ElementType.LIGHT));
    }

    @Override
    protected boolean tickBeforePayload() {
        if (!super.tickBeforePayload()) return false;
        if (tickCount > lifetime) {
            discard();
            return false;
        }
        return true;
    }

    @Override
    protected void tickAfterPayload() {
        if (level() instanceof ServerLevel serverLevel) syncLights(serverLevel);
    }

    private void syncLights(ServerLevel level) {
        float size = Math.max(getTargetSize(), getBallSize());
        Set<BlockPos> desired = new HashSet<>(MagicBallGeometry.containedPositions(position(), size));

        for (BlockPos old : List.copyOf(placedLightPositions)) {
            if (!desired.contains(old)) releaseLight(level, old);
        }

        BlockState light = Blocks.LIGHT.defaultBlockState()
                .setValue(BlockStateProperties.LEVEL, LIGHT_LEVEL);
        for (BlockPos pos : desired) {
            BlockState current = level.getBlockState(pos);
            if (current.isAir()) {
                level.setBlock(pos, light, 3);
                claimLight(level, pos);
            } else if (current.is(Blocks.LIGHT)
                    && (placedLightPositions.contains(pos) || hasReference(level, pos))) {
                claimLight(level, pos);
            }
        }
    }

    private void claimLight(ServerLevel level, BlockPos pos) {
        placedLightPositions.add(pos);
        LIGHT_REFERENCES.computeIfAbsent(level, ignored -> new HashMap<>())
                .computeIfAbsent(pos, ignored -> new HashSet<>()).add(getUUID());
    }

    private static boolean hasReference(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Set<UUID>> references = LIGHT_REFERENCES.get(level);
        return references != null && !references.getOrDefault(pos, Set.of()).isEmpty();
    }

    private void releaseLight(ServerLevel level, BlockPos pos) {
        if (!placedLightPositions.remove(pos)) return;

        Map<BlockPos, Set<UUID>> references = LIGHT_REFERENCES.get(level);
        Set<UUID> owners = references == null ? null : references.get(pos);
        if (owners != null) owners.remove(getUUID());
        boolean remaining = owners != null && !owners.isEmpty();
        if (references != null) {
            if (remaining) references.put(pos, owners);
            else references.remove(pos);
            if (references.isEmpty()) LIGHT_REFERENCES.remove(level);
        }

        if (!remaining && level.getBlockState(pos).is(Blocks.LIGHT)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private void clearLights() {
        if (!(level() instanceof ServerLevel level)) return;
        for (BlockPos pos : List.copyOf(placedLightPositions)) releaseLight(level, pos);
    }

    @Override
    public void remove(RemovalReason reason) {
        clearLights();
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        lifetime = Math.max(1, tag.getInt("Lifetime"));
        placedLightPositions.clear();
        if (tag.contains(LIGHTS_TAG, Tag.TAG_LIST)) {
            for (Tag value : tag.getList(LIGHTS_TAG, Tag.TAG_COMPOUND)) {
                if (!(value instanceof CompoundTag light)) continue;
                placedLightPositions.add(new BlockPos(
                        light.getInt("x"), light.getInt("y"), light.getInt("z")));
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Lifetime", lifetime);
        ListTag lights = new ListTag();
        for (BlockPos pos : placedLightPositions) {
            CompoundTag light = new CompoundTag();
            light.putInt("x", pos.getX());
            light.putInt("y", pos.getY());
            light.putInt("z", pos.getZ());
            lights.add(light);
        }
        tag.put(LIGHTS_TAG, lights);
    }
}
