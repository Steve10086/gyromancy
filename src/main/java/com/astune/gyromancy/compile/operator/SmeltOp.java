package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.util.MagicBallGeometry;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
record SmeltTask(ItemStack drop, int totalTime, int elapsed, ItemStack result) {}
record BlockTasks(double elapsed, List<SmeltTask> tasks, BlockState initial) {}
public final class SmeltOp extends OnEntityTickOp {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "smelt");
    public static final Codec<SmeltOp> CODEC = Codec.unit(SmeltOp::new);
    private final Map<ItemEntity, Double> itemProgress = new IdentityHashMap<>();
    private final Map<BlockPos, BlockTasks> blockProgress = new HashMap<>();

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<SmeltOp> codec() {
        return CODEC;
    }

    @Override
    public void onEntityTick(EntityTickContext ctx) {
        if (ctx.isClientSide()) return;

        float target = ctx.targetSize();
        double r = target / 2.0;
        AABB box = ctx.bounds();
        double smeltSpeed = smeltSpeed(ctx);

        spawnSmokeParticles(ctx);

        for (LivingEntity e : ctx.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && MagicBallGeometry.inSphere(ctx.position(), e.position(), r)))
            e.setRemainingFireTicks(200);

        itemProgress.keySet().removeIf(e -> !e.isAlive() || !MagicBallGeometry.inSphere(ctx.position(), e.position(), r));
        for (ItemEntity e : ctx.level().getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && MagicBallGeometry.inSphere(ctx.position(), e.position(), r)))
            smeltItem(ctx, e, smeltSpeed);

        blockProgress.keySet().removeIf(p -> ctx.level().getBlockState(p) != blockProgress.get(p).initial());
        BlockPos.betweenClosedStream(box).map(BlockPos::immutable)
                .filter(p -> !blockProgress.containsKey(p) && MagicBallGeometry.inSphere(ctx.position(), p.getCenter(), r))
                .forEach(pos -> initBlockTasks(ctx, pos));
        new HashMap<>(blockProgress).forEach((p, bt) -> smeltBlock(ctx, p, bt, smeltSpeed));
    }

    private double smeltSpeed(EntityTickContext ctx) {
        return Math.max(1.0, ctx.averageElementLevel() * 0.001);
    }

    private void spawnSmokeParticles(EntityTickContext ctx) {
        if (!(ctx.level() instanceof ServerLevel sl)) return;
        var players = sl.players();
        if (players.isEmpty()) return;

        for (ItemEntity item : itemProgress.keySet()) {
            Vec3 p = item.position();
            var packet = new ClientboundLevelParticlesPacket(
                    ParticleTypes.SMOKE, true, p.x, p.y + 0.4, p.z, 0.0f, 0.0f, 0.0f, 0.0f, 2);
            for (var player : players) player.connection.send(packet);
        }

        for (BlockPos bpos : blockProgress.keySet()) {
            Vec3 center = Vec3.atCenterOf(bpos);
            for (Direction dir : Direction.values()) {
                if (!ctx.level().getBlockState(bpos.relative(dir)).isAir()) continue;
                double px = center.x + dir.getStepX() * 0.5;
                double py = center.y + dir.getStepY() * 0.5;
                double pz = center.z + dir.getStepZ() * 0.5;
                if (dir.getAxis() != Direction.Axis.X) px += (ctx.owner().getRandom().nextDouble() - 0.5);
                if (dir.getAxis() != Direction.Axis.Y) py += (ctx.owner().getRandom().nextDouble() - 0.5);
                if (dir.getAxis() != Direction.Axis.Z) pz += (ctx.owner().getRandom().nextDouble() - 0.5);
                var packet = new ClientboundLevelParticlesPacket(
                        ParticleTypes.SMOKE, true, px, py, pz, 0.0f, 0.0f, 0.0f, 0.0f, 1);
                for (var player : players) player.connection.send(packet);
            }
        }
    }

    private void initBlockTasks(EntityTickContext ctx, BlockPos pos) {
        BlockState state = ctx.level().getBlockState(pos);
        if (state.isAir()) return;
        var blockItem = state.getBlock().asItem();
        if (blockItem == null) return;
        ItemStack stack = new ItemStack(blockItem);
        if (stack.isEmpty()) return;

        var recipe = ctx.level().getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), ctx.level())
                .map(RecipeHolder::value).orElse(null);
        if (recipe == null) return;
        var out = recipe.getResultItem(ctx.level().registryAccess());
        if (out.isEmpty()) return;
        out = out.copyWithCount(out.getCount() * stack.getCount());

        blockProgress.put(pos, new BlockTasks(0.0,
                List.of(new SmeltTask(stack, recipe.getCookingTime(), 0, out)), state));
    }

    private void smeltItem(EntityTickContext ctx, ItemEntity item, double speed) {
        var stack = item.getItem();
        if (stack.isEmpty()) return;
        var recipe = ctx.level().getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), ctx.level())
                .map(RecipeHolder::value).orElse(null);
        if (recipe == null) return;

        double progress = itemProgress.getOrDefault(item, 0.0) + speed;
        if (progress < recipe.getCookingTime()) {
            itemProgress.put(item, progress);
            return;
        }
        itemProgress.remove(item);
        var out = recipe.getResultItem(ctx.level().registryAccess());
        if (!out.isEmpty())
            item.setItem(out.copyWithCount(out.getCount() * stack.getCount()));
    }

    private void smeltBlock(EntityTickContext ctx, BlockPos pos, BlockTasks bt, double speed) {
        double newElapsed = bt.elapsed() + speed;
        int maxTime = bt.tasks().stream().mapToInt(SmeltTask::totalTime).max().orElse(0);
        if (newElapsed < maxTime) {
            blockProgress.put(pos, new BlockTasks(newElapsed, bt.tasks(), bt.initial()));
            return;
        }
        blockProgress.remove(pos);
        ctx.level().destroyBlock(pos, false);

        boolean placed = false;
        for (var task : bt.tasks()) {
            if (!placed && task.result().getItem() instanceof BlockItem bi) {
                ctx.level().setBlock(pos, bi.getBlock().defaultBlockState(), 3);
                placed = true;
            } else {
                Block.popResource(ctx.level(), pos, task.result());
            }
        }
    }
}
