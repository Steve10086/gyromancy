package com.astune.gyromancy.compile.operator;

import com.astune.painter.block.CanvasBlock;
import com.astune.painter.block.CanvasBlockEntity;
import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.element.ElementStorageManager;
import com.astune.gyromancy.recipe.CrushRecipe;
import com.astune.gyromancy.registry.ModRecipes;
import com.astune.gyromancy.util.MagicBallGeometry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.List;

/**
 * One-shot crush payload: pulverizes the sphere by shell-based mining level and
 * consumes earth from every cell afterwards.
 */
public final class CrushOp extends OnEntityTickOp {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "crush_effect");
    public static final Codec<CrushOp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("radius").forGetter(CrushOp::radius),
            Codec.DOUBLE.fieldOf("center_x").forGetter(op -> op.center.x),
            Codec.DOUBLE.fieldOf("center_y").forGetter(op -> op.center.y),
            Codec.DOUBLE.fieldOf("center_z").forGetter(op -> op.center.z)
    ).apply(instance, CrushOp::new));

    private final double radius;
    private final Vec3 center;

    public CrushOp(double radius, Vec3 center) {
        this.radius = radius;
        this.center = center;
    }

    private CrushOp(double radius, double centerX, double centerY, double centerZ) {
        this(radius, new Vec3(centerX, centerY, centerZ));
    }

    public double radius() {
        return radius;
    }

    public Vec3 center() {
        return center;
    }

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<CrushOp> codec() {
        return CODEC;
    }

    @Override
    public void onEntityTick(EntityTickContext ctx) {
        if (ctx.isClientSide() || !(ctx.level() instanceof ServerLevel serverLevel)) return;

        List<BlockPos> positions = MagicBallGeometry.positionsInSphere(center, radius);
        if (positions.isEmpty()) return;
        FakePlayer harvestPlayer = FakePlayerFactory.getMinecraft(serverLevel);

        ElementStorageManager storage = ctx.elementStorage();
        long earthTotal = storage.sum(ctx.level(), positions, ElementType.EARTH);
        double average = (double) earthTotal / positions.size();
        int strength = CrushLogic.strengthForAverage(average);
        boolean belowMinimum = storage.anyBelow(ctx.level(), positions, ElementType.EARTH,
                CrushLogic.MIN_ELEMENT);
        Gyromancy.LOGGER.debug(
                "[CrushDebug] execute center={} radius={} sphereBlocks={} earthTotal={} averageEarth={} strength={} belowMinimum={}",
                center, radius, positions.size(), earthTotal, average, strength, belowMinimum);

        if (belowMinimum) return;

        // Pre-existing drops are processed before this crush spawns its own.
        crushItems(ctx, strength);
        crushBlocks(ctx, positions, strength, harvestPlayer);
        damageEntities(ctx, strength);
        storage.consumeFromEach(ctx.level(), positions, ElementType.EARTH,
                (long) strength * CrushLogic.CONSUME_PER_STRENGTH);
    }

    private void crushBlocks(EntityTickContext ctx, List<BlockPos> positions, int strength,
                             FakePlayer harvestPlayer) {
        for (BlockPos pos : positions) {
            BlockState state = ctx.level().getBlockState(pos);
            double distance = center.distanceTo(pos.getCenter());
            int level = CrushLogic.shellLevel(radius, distance, strength);
            ItemStack harvestTool = new ItemStack(CrushLogic.harvestToolForLevel(level));
            harvestPlayer.setItemInHand(InteractionHand.MAIN_HAND, harvestTool);
            boolean canCrush = CrushLogic.canCrush(ctx.level(), pos, state, harvestPlayer);
            if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)
                    || state.getBlock() instanceof CanvasBlock) {
                BlockState mimickedState = ctx.level().getBlockEntity(pos)
                        instanceof CanvasBlockEntity canvas ? canvas.getMimickedState() : null;
                Gyromancy.LOGGER.debug(
                        "[CrushDebug] harvest pos={} state={} mimickedState={} distance={} shellLevel={} tool={} destroyProgress={} canHarvest={} canCrush={}",
                        pos, state, mimickedState, distance, level, harvestTool,
                        state.getDestroyProgress(harvestPlayer, ctx.level(), pos),
                        state.getBlock().canHarvestBlock(state, ctx.level(), pos, harvestPlayer),
                        canCrush);
            }
            if (!canCrush) continue;
            ctx.level().destroyBlock(pos, true);
            spawnCrushParticles(ctx, pos);
        }
    }

    /** Three campfire smoke particles scattered over every pulverized block. */
    private static void spawnCrushParticles(EntityTickContext ctx, BlockPos pos) {
        if (ctx.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    3, 0.4, 0.4, 0.4, 0.01);
        }
    }

    /** Converts dropped stacks inside the sphere through matching crush recipes. */
    private void crushItems(EntityTickContext ctx, int strength) {
        double radiusSqr = radius * radius;
        List<ItemEntity> items = ctx.level().getEntitiesOfClass(ItemEntity.class, ctx.bounds(),
                item -> item.isAlive() && center.distanceToSqr(item.position()) <= radiusSqr);
        for (ItemEntity item : items) {
            ItemStack stack = item.getItem();
            if (stack.isEmpty()) continue;

            CrushRecipe recipe = ctx.level().getRecipeManager()
                    .getRecipeFor(ModRecipes.CRUSH_TYPE.get(), new SingleRecipeInput(stack),
                            ctx.level())
                    .map(RecipeHolder::value)
                    .orElse(null);
            if (recipe == null) continue;

            int level = CrushLogic.shellLevel(radius, center.distanceTo(item.position()), strength);
            if (level < recipe.requiredLevel()) continue;

            int multiplier = stack.getCount();
            Vec3 position = item.position();
            item.discard();
            for (ItemStack result : recipe.results()) {
                dropInPlace(ctx, position, result, multiplier);
            }
        }
    }

    /** Drops the multiplied result where the consumed stack stood, splitting stacks. */
    private static void dropInPlace(EntityTickContext ctx, Vec3 position, ItemStack result,
                                    int multiplier) {
        if (result.isEmpty() || multiplier <= 0) return;

        long remaining = (long) result.getCount() * multiplier;
        int maxStackSize = Math.max(1, result.getMaxStackSize());
        while (remaining > 0) {
            int size = (int) Math.min(remaining, maxStackSize);
            ctx.addFreshEntity(new ItemEntity(ctx.level(), position.x, position.y,
                    position.z, result.copyWithCount(size)));
            remaining -= size;
        }
    }

    private void damageEntities(EntityTickContext ctx, int strength) {
        double radiusSqr = radius * radius;
        for (LivingEntity target : ctx.level().getEntitiesOfClass(LivingEntity.class, ctx.bounds(),
                entity -> entity.isAlive()
                        && center.distanceToSqr(entity.getBoundingBox().getCenter()) <= radiusSqr)) {
            double distance = center.distanceTo(target.getBoundingBox().getCenter());
            int level = CrushLogic.shellLevel(radius, distance, strength);
            target.hurt(ctx.level().damageSources().magic(), level * 2.0F);
        }
    }
}
