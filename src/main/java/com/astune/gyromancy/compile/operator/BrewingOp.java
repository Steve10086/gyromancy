package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.entity.ball.BrewingRoutePlanner;
import com.astune.gyromancy.util.MagicBallGeometry;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class BrewingOp extends OnEntityTickOp {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "brewing");
    public static final Codec<BrewingOp> CODEC = Codec.unit(BrewingOp::new);
    private static final int EFFECT_INTERVAL = 10;
    private static final int BREWING_TIME = PotionBrewing.BREWING_TIME_SECONDS * 20;
    private static final int MAX_BREWING_ROUTE_LENGTH = 4;
    private static final int MISSING_ITEM_GRACE_TICKS = 20;
    private BrewTask brewTask;

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<BrewingOp> codec() {
        return CODEC;
    }

    @Override
    public void onEntityTick(EntityTickContext ctx) {
        if (!(ctx.level() instanceof ServerLevel server)) return;
        if (brewTask == null) {
            if (ctx.tickCount() % EFFECT_INTERVAL == 0) tryStartBrewing(ctx);
            return;
        }

        double radius = ctx.targetSize() / 2.0;
        List<ItemEntity> ingredientEntities = new ArrayList<>();
        for (int i = 0; i < brewTask.ingredientIds().size(); i++) {
            Entity entity = server.getEntity(brewTask.ingredientIds().get(i));
            if (!(entity instanceof ItemEntity item) || !item.isAlive()) {
                int missingTicks = brewTask.missingTicks() + 1;
                brewTask = missingTicks > MISSING_ITEM_GRACE_TICKS ? null : brewTask.withMissingTicks(missingTicks);
                return;
            }
            ItemStack expected = brewTask.expectedIngredients().get(i);
            if (!MagicBallGeometry.inSphere(ctx.position(), item.position(), radius)
                    || item.getItem().isEmpty()
                    || !ItemStack.isSameItemSameComponents(item.getItem(), expected)) {
                brewTask = null;
                return;
            }
            ingredientEntities.add(item);
        }
        spawnBrewingParticles(ctx, server, ingredientEntities);

        int remaining = brewTask.remainingTicks() - 1;
        if (remaining > 0) {
            brewTask = brewTask.withProgress(remaining);
            return;
        }

        ingredientEntities.forEach(BrewingOp::consumeIngredient);
        var result = new ItemEntity(ctx.level(), ctx.position().x, ctx.position().y + radius, ctx.position().z, brewTask.result().copy());
        ctx.addFreshEntity(result);
        brewTask = null;
    }

    private static void spawnBrewingParticles(EntityTickContext ctx, ServerLevel level, List<ItemEntity> items) {
        if (ctx.tickCount() % 5 != 0) return;
        var players = level.players();
        if (players.isEmpty()) return;

        for (ItemEntity item : items) {
            Vec3 p = item.position();
            var packet = new ClientboundLevelParticlesPacket(
                    ParticleTypes.CLOUD, true, p.x, p.y + 0.35, p.z, 0.0f, 0.05f, 0.0f, 0.01f, 1);
            for (var player : players) player.connection.send(packet);
        }
    }

    private void tryStartBrewing(EntityTickContext ctx) {
        List<BrewIngredient> ingredients = currentIngredients(ctx);
        ItemStack inputPotion = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        findBrewRoute(ctx, inputPotion, ingredients).ifPresent(route ->
                brewTask = new BrewTask(route.ingredientIds(), route.expectedIngredients(), route.result(),
                        BREWING_TIME * route.expectedIngredients().size(), 0));
    }

    private static List<BrewIngredient> currentIngredients(EntityTickContext ctx) {
        double radius = ctx.targetSize() / 2.0;
        List<BrewIngredient> ingredients = new ArrayList<>();
        ctx.level().getEntitiesOfClass(ItemEntity.class, ctx.bounds().inflate(0.25), Entity::isAlive).stream()
                .filter(item -> MagicBallGeometry.inSphere(ctx.position(), item.position(), radius))
                .sorted(Comparator.comparing(item -> item.getUUID().toString()))
                .forEach(item -> {
                    ItemStack stack = item.getItem();
                    boolean duplicate = ingredients.stream()
                            .anyMatch(existing -> ItemStack.isSameItemSameComponents(existing.stack(), stack));
                    if (!stack.isEmpty() && !duplicate) {
                        ingredients.add(new BrewIngredient(item.getUUID(), stack.copyWithCount(1)));
                    }
                });
        return ingredients;
    }

    private static Optional<BrewRoute> findBrewRoute(EntityTickContext ctx, ItemStack currentPotion,
                                                     List<BrewIngredient> ingredients) {
        var route = BrewingRoutePlanner.findIngredientRoute(currentPotion.copyWithCount(1), ingredients,
                MAX_BREWING_ROUTE_LENGTH,
                (ingredient, potion) -> brewOutput(ctx, ingredient.stack(), potion),
                BrewingOp::isAllowedPotion,
                ItemStack::isSameItemSameComponents,
                BrewingOp::hasPotionEffect);
        if (route.isEmpty()) return Optional.empty();
        ItemStack output = currentPotion.copyWithCount(1);
        List<UUID> ids = new ArrayList<>();
        List<ItemStack> expected = new ArrayList<>();
        for (int index : route.get()) {
            BrewIngredient ingredient = ingredients.get(index);
            output = brewOutput(ctx, ingredient.stack(), output);
            ids.add(ingredient.id());
            expected.add(ingredient.stack().copyWithCount(1));
        }
        return Optional.of(new BrewRoute(List.copyOf(ids), List.copyOf(expected), output.copyWithCount(1)));
    }

    private static ItemStack brewOutput(EntityTickContext ctx, ItemStack ingredient, ItemStack inputPotion) {
        if (ingredient.is(Items.GLOWSTONE_DUST)
                || ingredient.is(Items.GUNPOWDER)
                || ingredient.is(Items.DRAGON_BREATH)) return ItemStack.EMPTY;
        ItemStack output = ctx.level().potionBrewing().mix(ingredient, inputPotion);
        return isAllowedPotion(output) ? output.copyWithCount(1) : ItemStack.EMPTY;
    }

    private static boolean isAllowedPotion(ItemStack stack) {
        if (!stack.is(Items.POTION)) return false;
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return false;
        for (MobEffectInstance effect : contents.getAllEffects()) {
            if (effect.getAmplifier() > 0) return false;
        }
        return true;
    }

    private static boolean hasPotionEffect(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && contents.hasEffects();
    }

    private static void consumeIngredient(ItemEntity entity) {
        ItemStack stack = entity.getItem();
        ItemStack remainder = stack.hasCraftingRemainingItem() ? stack.getCraftingRemainingItem() : ItemStack.EMPTY;
        stack.shrink(1);
        if (stack.isEmpty()) {
            if (remainder.isEmpty()) entity.discard();
            else entity.setItem(remainder);
        } else {
            entity.setItem(stack);
            if (!remainder.isEmpty()) {
                entity.level().addFreshEntity(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), remainder));
            }
        }
    }

    private record BrewIngredient(UUID id, ItemStack stack) {}
    private record BrewRoute(List<UUID> ingredientIds, List<ItemStack> expectedIngredients, ItemStack result) {}
    private record BrewTask(List<UUID> ingredientIds, List<ItemStack> expectedIngredients, ItemStack result,
                            int remainingTicks, int missingTicks) {
        BrewTask withProgress(int remainingTicks) {
            return new BrewTask(ingredientIds, expectedIngredients, result, remainingTicks, 0);
        }

        BrewTask withMissingTicks(int missingTicks) {
            return new BrewTask(ingredientIds, expectedIngredients, result, remainingTicks, missingTicks);
        }
    }
}
