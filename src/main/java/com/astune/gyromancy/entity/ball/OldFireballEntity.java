package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

record SmeltTask(ItemStack drop, int totalTime, int elapsed, ItemStack result) {}
record BlockTasks(int elapsed, List<SmeltTask> tasks, BlockState initial) {}

public class OldFireballEntity extends MagicBallEntity {
    private static final float MIN_SIZE = 0.1F;
    private static final float MERGE_RATE = 0.1F;
    private static final double FIRE_VOLUME_LOSS = 0.1;
    private static final double FIRE_EQUILIBRIUM = 1000.0;
    private static final double FIRE_PER_VOLUME = 1000.0;
    private static final double FIRE_CONVERSION_COST = 10.0;
    private static final double MANA_TO_VOLUME = 0.01;

    private final Map<ItemEntity, Integer> itemProgress = new IdentityHashMap<>();
    private final Map<BlockPos, BlockTasks> blockProgress = new HashMap<>();
    private Vec3 velocity = Vec3.ZERO;
    private Vec3 acceleration = Vec3.ZERO;

    public OldFireballEntity(EntityType<OldFireballEntity> type, Level level) {
        super(type, level);
    }

    public OldFireballEntity(Level level, Vec3 pos, Vec3 velocity, Vec3 acceleration, float size) {
        this(ModEntities.OLD_FIREBALL.get(), level);
        this.velocity = velocity;
        this.acceleration = acceleration;
        setBallSize(size);
        setPos(pos);
    }

    @Override
    public void tick() {
        super.tick();
        growIntoTargetSize();
        exchangeWithElements(FIRE_VOLUME_LOSS, FIRE_EQUILIBRIUM, FIRE_PER_VOLUME,
                FIRE_CONVERSION_COST, MANA_TO_VOLUME);
        velocity = velocity.add(acceleration);
        setPos(position().add(velocity));
        spawnSmokeParticles();
        if (level().isClientSide) return;

        float target = getTargetSize();
        double r = target / 2.0;
        AABB box = getBoundingBox();

        // 2. Burn living entities inside
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && inSphere(e.position(), r)))
            e.setRemainingFireTicks(200);

        // 3. Smelt items inside
        itemProgress.keySet().removeIf(e -> !e.isAlive() || !inSphere(e.position(), r));
        for (ItemEntity e : level().getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && inSphere(e.position(), r)))
            smeltItem(e, 1f);

        // 4. Smelt blocks inside
        blockProgress.keySet().removeIf(p -> level().getBlockState(p) != blockProgress.get(p).initial());
        BlockPos.betweenClosedStream(box).map(BlockPos::immutable)
                .filter(p -> !blockProgress.containsKey(p) && inSphere(p.getCenter(), r))
                .forEach(this::initBlockTasks);
        BlockPos.betweenClosedStream(box).map(BlockPos::immutable)
                .filter(p -> level().getBlockState(p).isAir() && inSphere(p.getCenter(), r))
                .forEach(this::tryPutFire);
        new HashMap<>(blockProgress).forEach((p, bt) -> smeltBlock(p, bt));

        // 5. Merge with overlapping OldFireballEntity
        merge(target, r, box);
    }

    private void tryPutFire(BlockPos pos){
        if(random.nextDouble() < 0.001){
            BlockState fire = BaseFireBlock.getState(level(), pos);
            if (fire.canSurvive(level(), pos))
                level().setBlock(pos, fire, 3);
        }
    }

    private void merge(float target, double r, AABB box) {
        Set<OldFireballEntity> reducedThisTick = new HashSet<>();

        for (OldFireballEntity other : level().getEntitiesOfClass(OldFireballEntity.class, box,
                e -> e != this && e.isAlive() && inSphere(e.position(), r))) {
            float otherTarget = other.getTargetSize();
            if (target < otherTarget || reducedThisTick.contains(other)) continue;

            // Decrease smaller entity's size
            float newOtherTarget = Math.max(0, otherTarget - MERGE_RATE);
            other.setTargetSize(newOtherTarget);

            // Transfer volume to larger entity
            double myR = target / 2.0;
            double otherR = otherTarget / 2.0;
            double newOtherR = newOtherTarget / 2.0;
            double volLost = (4.0 / 3.0) * Math.PI * (otherR * otherR * otherR - newOtherR * newOtherR * newOtherR);
            double newMyR = Math.cbrt(myR * myR * myR + volLost * 3.0 / (4.0 * Math.PI));
            setTargetSize((float)(newMyR * 2.0));

            reducedThisTick.add(other);

            if (newOtherTarget < MIN_SIZE) other.discard();
        }

        for (FireballEntity other : level().getEntitiesOfClass(FireballEntity.class, box,
                e -> e.isAlive() && inSphere(e.position(), r))) {
            float otherTarget = other.getBallSize();
            if (target < otherTarget || reducedThisTick.contains(other)) continue;

            // discard unstable fireball
            other.discard();

            // Transfer volume to larger entity
            double myR = target / 2.0;
            double otherR = otherTarget / 2.0;
            double volLost = (4.0 / 3.0) * Math.PI * (otherR * otherR * otherR);
            double newMyR = Math.cbrt(myR * myR * myR + volLost * 3.0 / (4.0 * Math.PI));
            setTargetSize((float)(newMyR * 2.0));
        }
    }

    private void spawnSmokeParticles() {
        if (!(level() instanceof ServerLevel sl)) return;
        var players = sl.players();
        if (players.isEmpty()) return;

        // Items: 2 particles/tick at item position
        for (ItemEntity item : itemProgress.keySet()) {
            Vec3 p = item.position();
            var packet = new ClientboundLevelParticlesPacket(
                    ParticleTypes.SMOKE, true, p.x, p.y + 0.4, p.z, 0.0f, 0.0f, 0.0f, 0.0f, 2);
            for (var player : players) player.connection.send(packet);
        }

        // Blocks: 1 particle/tick per exposed face, random position on the face
        for (BlockPos bpos : blockProgress.keySet()) {
            Vec3 center = Vec3.atCenterOf(bpos);
            for (Direction dir : Direction.values()) {
                if (!level().getBlockState(bpos.relative(dir)).isAir()) continue;
                double px = center.x + dir.getStepX() * 0.5;
                double py = center.y + dir.getStepY() * 0.5;
                double pz = center.z + dir.getStepZ() * 0.5;
                if (dir.getAxis() != Direction.Axis.X) px += (random.nextDouble() - 0.5);
                if (dir.getAxis() != Direction.Axis.Y) py += (random.nextDouble() - 0.5);
                if (dir.getAxis() != Direction.Axis.Z) pz += (random.nextDouble() - 0.5);
                var packet = new ClientboundLevelParticlesPacket(
                        ParticleTypes.SMOKE, true, px, py, pz, 0.0f, 0.0f, 0.0f, 0.0f, 1);
                for (var player : players) player.connection.send(packet);
            }
        }
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
                List.of(new SmeltTask(stack, recipe.getCookingTime(), 0, out)), state));
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
            blockProgress.put(pos, new BlockTasks(newElapsed, bt.tasks(), bt.initial()));
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
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("VelX"))
            velocity = new Vec3(tag.getDouble("VelX"), tag.getDouble("VelY"), tag.getDouble("VelZ"));
        if (tag.contains("AccelX"))
            acceleration = new Vec3(tag.getDouble("AccelX"), tag.getDouble("AccelY"), tag.getDouble("AccelZ"));
        refreshDimensions();
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("VelX", velocity.x);
        tag.putDouble("VelY", velocity.y);
        tag.putDouble("VelZ", velocity.z);
        tag.putDouble("AccelX", acceleration.x);
        tag.putDouble("AccelY", acceleration.y);
        tag.putDouble("AccelZ", acceleration.z);
    }



}
