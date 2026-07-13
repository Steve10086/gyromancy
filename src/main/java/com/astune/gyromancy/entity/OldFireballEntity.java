package com.astune.gyromancy.entity;

import com.astune.gyromancy.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

record SmeltTask(ItemStack drop, int totalTime, int elapsed, ItemStack result) {}
record BlockTasks(int elapsed, List<SmeltTask> tasks) {}

public class OldFireballEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_SIZE =
            SynchedEntityData.defineId(OldFireballEntity.class, EntityDataSerializers.FLOAT);

    private final Map<ItemEntity, Integer> itemProgress = new IdentityHashMap<>();
    private final Map<BlockPos, BlockTasks> blockProgress = new HashMap<>();
    private Vec3 velocity = Vec3.ZERO;
    private Vec3 acceleration = Vec3.ZERO;

    public OldFireballEntity(EntityType<OldFireballEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public OldFireballEntity(Level level, Vec3 pos, Vec3 velocity, Vec3 acceleration, float size) {
        this(ModEntities.OLD_FIREBALL.get(), level);
        this.velocity = velocity;
        this.acceleration = acceleration;
        entityData.set(DATA_SIZE, size);
        setPos(pos);
        refreshDimensions();
    }

    @Override
    public void tick() {
        super.tick();
        velocity = velocity.add(acceleration);
        setPos(position().add(velocity));
        if (level().isClientSide) return;

        double r = getFireballSize() / 2.0;
        var box = getBoundingBox();

        // 2. Burn living entities inside
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && inSphere(e.position(), r)))
            e.setRemainingFireTicks(200);

        // 3. Smelt items inside
        itemProgress.keySet().removeIf(e -> !e.isAlive() || !inSphere(e.position(), r));
        for (ItemEntity e : level().getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && inSphere(e.position(), r)))
            smeltItem(e, 1f);

        // 4. Smelt blocks inside
        blockProgress.keySet().removeIf(p -> !inSphere(p.getCenter(), r));
        BlockPos.betweenClosedStream(box).map(BlockPos::immutable)
                .filter(p -> !blockProgress.containsKey(p) && inSphere(p.getCenter(), r))
                .forEach(this::initBlockTasks);
        new HashMap<>(blockProgress).forEach((p, bt) -> smeltBlock(p, bt));
    }

    private void initBlockTasks(BlockPos pos) {
        BlockState state = level().getBlockState(pos);
        if (state.isAir()) return;
        var blockItem = state.getBlock().asItem();
        if (blockItem == null) return;
        ItemStack stack = new ItemStack(blockItem);
        if (stack.isEmpty()) return;

        var recipe = level().getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level())
                .map(RecipeHolder::value).orElse(null);
        if (recipe == null) return;
        var out = recipe.getResultItem(level().registryAccess());
        if (out.isEmpty()) return;
        out = out.copyWithCount(out.getCount() * stack.getCount());

        blockProgress.put(pos, new BlockTasks(0,
                List.of(new SmeltTask(stack, recipe.getCookingTime(), 0, out))));
    }

    private void smeltItem(ItemEntity item, Float speed) {
        var stack = item.getItem();
        if (stack.isEmpty()) return;
        var recipe = level().getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level())
                .map(RecipeHolder::value).orElse(null);
        if (recipe == null) return;

        int t = itemProgress.getOrDefault(item, 0) + 1;
        if (t < recipe.getCookingTime()) {
            itemProgress.put(item, Math.round(t * speed));
            return;
        }
        itemProgress.remove(item);
        var out = recipe.getResultItem(level().registryAccess());
        if (!out.isEmpty())
            item.setItem(out.copyWithCount(out.getCount() * stack.getCount()));
    }

    private void smeltBlock(BlockPos pos, BlockTasks bt) {
        int newElapsed = bt.elapsed() + 1;
        int maxTime = bt.tasks().stream().mapToInt(SmeltTask::totalTime).max().orElse(0);
        if (newElapsed < maxTime) {
            blockProgress.put(pos, new BlockTasks(newElapsed, bt.tasks()));
            return;
        }
        blockProgress.remove(pos);
        level().destroyBlock(pos, false);

        boolean placed = false;
        for (var task : bt.tasks()) {
            if (!placed && task.result().getItem() instanceof BlockItem bi) {
                level().setBlock(pos, bi.getBlock().defaultBlockState(), 3);
                placed = true;
            } else {
                Block.popResource(level(), pos, task.result());
            }
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        builder.define(DATA_SIZE, 0.0F);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        if (tag.contains("Size")) entityData.set(DATA_SIZE, tag.getFloat("Size"));
        if (tag.contains("VelX"))
            velocity = new Vec3(tag.getDouble("VelX"), tag.getDouble("VelY"), tag.getDouble("VelZ"));
        if (tag.contains("AccelX"))
            acceleration = new Vec3(tag.getDouble("AccelX"), tag.getDouble("AccelY"), tag.getDouble("AccelZ"));
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        tag.putFloat("Size", getFireballSize());
        tag.putDouble("VelX", velocity.x);
        tag.putDouble("VelY", velocity.y);
        tag.putDouble("VelZ", velocity.z);
        tag.putDouble("AccelX", acceleration.x);
        tag.putDouble("AccelY", acceleration.y);
        tag.putDouble("AccelZ", acceleration.z);
    }

    private boolean inSphere(Vec3 target, double radius) {
        return position().distanceToSqr(target.add(0, radius/2, 0)) <= radius * radius;
    }

    public float getFireballSize() {
        return entityData.get(DATA_SIZE);
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SIZE.equals(key)) refreshDimensions();
    }

    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull Pose pose) {
        return EntityDimensions.scalable(getFireballSize(), getFireballSize());
    }
}
