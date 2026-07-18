package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

public class WaterBallEntity extends MagicBallEntity {
    private static final int EFFECT_INTERVAL = 10;
    private static final double FIRE_VOLUME_LOSS = 0.1;
    private static final double FIRE_EQUILIBRIUM = 1000.0;
    private static final double MAX_VOLUME_FIRE_LEVEL = 200.0;
    private static final double FIRE_PER_VOLUME = 1000.0;
    private static final double FIRE_CONVERSION_COST = 10.0;
    private static final double MANA_TO_VOLUME = 0.05;
    private static final int BREWING_TIME = PotionBrewing.BREWING_TIME_SECONDS * 20;
    private static final int MAX_BREWING_ROUTE_LENGTH = 4;
    private static final int MISSING_ITEM_GRACE_TICKS = 20;
    private final Set<UUID> trackedItems = new HashSet<>();
    private Vec3 acceleration = Vec3.ZERO;
    private Vec3 pendingVelocity = Vec3.ZERO;
    private Vec3 pendingAcceleration = Vec3.ZERO;
    private ItemStack potionState = PotionContents.createItemStack(Items.POTION, Potions.WATER);
    private BrewTask brewTask;
    private boolean launched;
    private long storedMana;

    public WaterBallEntity(EntityType<WaterBallEntity> type, Level level) {
        super(type, level);
    }

    public WaterBallEntity(Level level, Vec3 pos, Vec3 velocity, Vec3 acceleration, float size) {
        this(ModEntities.WATER_BALL.get(), level);
        setBallSize(size);
        setPos(pos);
        pendingVelocity = velocity;
        pendingAcceleration = acceleration;
        setDeltaMovement(Vec3.ZERO);
    }

    public ItemStack getPotionState() {
        return potionState.copy();
    }

    public List<ItemStack> getCollectedItems() {
        if (!(level() instanceof ServerLevel server)) return List.of();
        double radius = getTargetSize() / 2.0;
        return trackedItems.stream()
                .map(server::getEntity)
                .filter(ItemEntity.class::isInstance)
                .map(ItemEntity.class::cast)
                .filter(Entity::isAlive)
                .filter(item -> inSphere(item.position(), radius))
                .map(item -> item.getItem().copy())
                .toList();
    }

    @Override
    public void tick() {
        super.tick();
        growIntoTargetSize();
        if (isFullyGrown() && tickCount % EFFECT_INTERVAL == 0) {
            storedMana = exchangeWithElements(ElementType.WATER, FIRE_VOLUME_LOSS * EFFECT_INTERVAL,
                    FIRE_EQUILIBRIUM, MAX_VOLUME_FIRE_LEVEL, FIRE_PER_VOLUME,
                    FIRE_CONVERSION_COST * EFFECT_INTERVAL,
                    MANA_TO_VOLUME, storedMana);
        }
        if (!launched && isFullyGrown()) {
            launched = true;
            acceleration = pendingAcceleration;
            setDeltaMovement(pendingVelocity);
        }

        Vec3 velocity = getDeltaMovement();
        if (launched && velocity.lengthSqr() > 1e-8 && hitSomething(velocity)) {
            burst();
            return;
        }
        if (launched) {
            setPos(position().add(velocity));
            setDeltaMovement(velocity.add(acceleration));
        }

        keepItemsInside();
        if (level().isClientSide) return;
        consumeDirectPotion();
        tickBrewing();

        applyPotionEffects();

    }

    private boolean hitSomething(Vec3 velocity) {
        Vec3 start = position();
        HitResult blockHit = level().clip(new ClipContext(start, start.add(velocity),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (blockHit.getType() != HitResult.Type.MISS) return true;
        return !level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().expandTowards(velocity).inflate(0.1), LivingEntity::isAlive).isEmpty();
    }

    private void burst() {
        if (!level().isClientSide) {
            fillWithFlowingWater(containedPositions(getTargetSize() * 2));
        }
        releaseItems();
        discard();
    }

    private void fillWithFlowingWater(List<BlockPos> positions) {
        var flowingWater = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1);
        positions.stream().filter(level()::isEmptyBlock).forEach(pos -> level().setBlock(pos, flowingWater, 3));
    }

    private void keepItemsInside() {
        double radius = getTargetSize() / 2.0;
        Vec3 center = position().add(0.0, radius, 0.0);
        if (level() instanceof ServerLevel server) {
            for (UUID uuid : trackedItems) {
                Entity entity = server.getEntity(uuid);
                if (entity instanceof ItemEntity item && !inSphere(item.position(), radius)) item.setNoGravity(false);
            }
        }
        for (ItemEntity item : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(0.25), Entity::isAlive)) {
            if (!inSphere(item.position(), radius)) continue;
            trackedItems.add(item.getUUID());
            item.setNoGravity(true);
            // TODO: allowing item orbiting the ball at 2/3 radius trace, in additional with angle shifting.
            Vec3 offset = item.position().subtract(center);
            Vec3 tangent = new Vec3(-offset.z, 0.0, offset.x);
            if (tangent.lengthSqr() < 1e-8) tangent = new Vec3(1.0, 0.0, 0.0);
            item.setDeltaMovement(tangent.normalize().scale(0.08).add(offset.scale(-0.04)));
        }
    }

    //TODO: consume all material needed for brewing (water -> poison / water -> healing) instead of single step each time. This should be registered as recipes
    private void tickBrewing() {
        if (!(level() instanceof ServerLevel server)) return;
        if (brewTask == null) {
            if (tickCount % EFFECT_INTERVAL == 0) tryStartBrewing();
            return;
        }

        Entity entity = server.getEntity(brewTask.ingredientId());
        if (!(entity instanceof ItemEntity ingredientEntity) || !ingredientEntity.isAlive()) {
            int missingTicks = brewTask.missingTicks() + 1;
            brewTask = missingTicks > MISSING_ITEM_GRACE_TICKS ? null : brewTask.withMissingTicks(missingTicks);
            return;
        }

        double radius = getTargetSize() / 2.0;
        ItemStack ingredient = ingredientEntity.getItem();
        if (!inSphere(ingredientEntity.position(), radius)
                || ingredient.isEmpty()
                || !ItemStack.isSameItemSameComponents(ingredient, brewTask.expectedIngredient())) {
            brewTask = null;
            return;
        }

        ItemStack currentOutput = brewOutput(ingredient, potionState);
        if (!ItemStack.isSameItemSameComponents(currentOutput, brewTask.result())) {
            brewTask = null;
            return;
        }

        int remaining = brewTask.remainingTicks() - 1;
        if (remaining > 0) {
            brewTask = brewTask.withProgress(remaining);
            return;
        }

        completeBrewing(ingredientEntity, currentOutput);
        brewTask = null;
    }

    private void tryStartBrewing() {
        List<BrewIngredient> ingredients = currentIngredients();
        findBrewStep(potionState, ingredients, this::brewOutput).ifPresent(step ->
                brewTask = new BrewTask(step.ingredientId(), step.expectedIngredient(), step.result(), BREWING_TIME, 0));
    }

    private void consumeDirectPotion() {
        if (!(level() instanceof ServerLevel server)) return;
        double radius = getTargetSize() / 2.0;
        trackedItems.stream()
                .sorted(Comparator.comparing(UUID::toString))
                .map(server::getEntity)
                .filter(ItemEntity.class::isInstance)
                .map(ItemEntity.class::cast)
                .filter(Entity::isAlive)
                .filter(item -> inSphere(item.position(), radius))
                .filter(item -> isAllowedPotion(item.getItem()))
                .filter(item -> hasPotionEffect(item.getItem()))
                .filter(item -> !ItemStack.isSameItemSameComponents(item.getItem().copyWithCount(1), potionState))
                .findFirst()
                .ifPresent(item -> {
                    setPotionState(item.getItem().copyWithCount(1));
                    consumeIngredient(item);
                });
    }

    private List<BrewIngredient> currentIngredients() {
        if (!(level() instanceof ServerLevel server)) return List.of();
        double radius = getTargetSize() / 2.0;
        List<BrewIngredient> ingredients = new ArrayList<>();
        trackedItems.stream()
                .sorted(Comparator.comparing(UUID::toString))
                .map(server::getEntity)
                .filter(ItemEntity.class::isInstance)
                .map(ItemEntity.class::cast)
                .filter(Entity::isAlive)
                .filter(item -> inSphere(item.position(), radius))
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

    private ItemStack brewOutput(ItemStack ingredient, ItemStack inputPotion) {
        if (ingredient.is(Items.GLOWSTONE_DUST)
                || ingredient.is(Items.GUNPOWDER)
                || ingredient.is(Items.DRAGON_BREATH)) return ItemStack.EMPTY;
        ItemStack output = level().potionBrewing().mix(ingredient, inputPotion);
        return isAllowedPotion(output) ? output.copyWithCount(1) : ItemStack.EMPTY;
    }

    static Optional<BrewStep> findBrewStep(ItemStack currentPotion,
                                           List<BrewIngredient> ingredients,
                                           BiFunction<ItemStack, ItemStack, ItemStack> mixer) {
        var first = BrewingRoutePlanner.findFirstIngredient(currentPotion.copyWithCount(1), ingredients,
                MAX_BREWING_ROUTE_LENGTH,
                (ingredient, potion) -> mixer.apply(ingredient.stack(), potion),
                WaterBallEntity::isAllowedPotion,
                ItemStack::isSameItemSameComponents,
                WaterBallEntity::hasPotionEffect);
        if (first.isEmpty()) return Optional.empty();
        BrewIngredient ingredient = ingredients.get(first.getAsInt());
        ItemStack output = mixer.apply(ingredient.stack(), currentPotion);
        return Optional.of(new BrewStep(ingredient.id(), ingredient.stack().copyWithCount(1), output.copyWithCount(1)));
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

    private void completeBrewing(ItemEntity ingredientEntity, ItemStack result) {
        setPotionState(result.copyWithCount(1));
        consumeIngredient(ingredientEntity);
        applyPotionEffects();
    }

    private void setPotionState(ItemStack result) {
        List<Holder<MobEffect>> oldEffects = potionEffects(potionState);
        potionState = result.copyWithCount(1);

        double radius = getTargetSize() / 2.0;
        for (LivingEntity entity : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox(), LivingEntity::isAlive)) {
            if (!inSphere(entity.position(), radius)) continue;
            oldEffects.forEach(entity::removeEffect);
        }
    }

    private void consumeIngredient(ItemEntity entity) {
        ItemStack stack = entity.getItem();
        ItemStack remainder = stack.hasCraftingRemainingItem() ? stack.getCraftingRemainingItem() : ItemStack.EMPTY;
        stack.shrink(1);
        if (stack.isEmpty()) {
            if (remainder.isEmpty()) entity.discard();
            else entity.setItem(remainder);
        } else {
            entity.setItem(stack);
            if (!remainder.isEmpty()) {
                level().addFreshEntity(new ItemEntity(level(), entity.getX(), entity.getY(), entity.getZ(), remainder));
            }
        }
    }

    private void applyPotionEffects() {
        List<Holder<MobEffect>> effects = potionEffects(potionState);
        if (effects.isEmpty()) return;
        double radius = getTargetSize() / 2.0;
        for (LivingEntity entity : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox(), LivingEntity::isAlive)) {
            if (!inSphere(entity.position(), radius)) continue;
            for (Holder<MobEffect> effect : effects) {
                int amplifier = effectAmplifier();
                if (effect.value().isInstantenous()) effect.value().applyInstantenousEffect(this, this, entity, amplifier, 1.0);
                else entity.addEffect(new MobEffectInstance(effect, 40, amplifier));
            }
        }
    }

    private int effectAmplifier() {
        return Math.max(0, (int)(getAverageElementLevel() * 0.001) - 1);
    }

    private static List<Holder<MobEffect>> potionEffects(ItemStack potion) {
        PotionContents contents = potion.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return List.of();
        List<Holder<MobEffect>> effects = new ArrayList<>();
        for (MobEffectInstance effect : contents.getAllEffects()) effects.add(effect.getEffect());
        return effects;
    }

    private void releaseItems() {
        for (UUID uuid : trackedItems) {
            Entity entity = level() instanceof ServerLevel server ? server.getEntity(uuid) : null;
            if (entity instanceof ItemEntity item) item.setNoGravity(false);
        }
        trackedItems.clear();
    }

    @Override
    public void remove(RemovalReason reason) {
        releaseItems();
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("AccelX")) acceleration = new Vec3(tag.getDouble("AccelX"), tag.getDouble("AccelY"), tag.getDouble("AccelZ"));
        if (tag.contains("PendingVelX")) pendingVelocity = new Vec3(tag.getDouble("PendingVelX"), tag.getDouble("PendingVelY"), tag.getDouble("PendingVelZ"));
        if (tag.contains("PendingAccelX")) pendingAcceleration = new Vec3(tag.getDouble("PendingAccelX"), tag.getDouble("PendingAccelY"), tag.getDouble("PendingAccelZ"));
        launched = tag.getBoolean("Launched");
        if (tag.contains("StoredMana")) storedMana = tag.getLong("StoredMana");
        if (tag.contains("PotionState", Tag.TAG_COMPOUND)) {
            potionState = ItemStack.parse(registryAccess(), tag.getCompound("PotionState")).orElse(potionState);
        }
        for (Tag value : tag.getList("TrackedItems", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag)value;
            if (entry.hasUUID("Uuid")) trackedItems.add(entry.getUUID("Uuid"));
        }
        if (tag.contains("BrewTask", Tag.TAG_COMPOUND)) {
            brewTask = BrewTask.load(tag.getCompound("BrewTask"), registryAccess()).orElse(null);
        }
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("AccelX", acceleration.x);
        tag.putDouble("AccelY", acceleration.y);
        tag.putDouble("AccelZ", acceleration.z);
        tag.putDouble("PendingVelX", pendingVelocity.x);
        tag.putDouble("PendingVelY", pendingVelocity.y);
        tag.putDouble("PendingVelZ", pendingVelocity.z);
        tag.putDouble("PendingAccelX", pendingAcceleration.x);
        tag.putDouble("PendingAccelY", pendingAcceleration.y);
        tag.putDouble("PendingAccelZ", pendingAcceleration.z);
        tag.putBoolean("Launched", launched);
        tag.putLong("StoredMana", storedMana);
        tag.put("PotionState", potionState.save(registryAccess()));
        ListTag tracked = new ListTag();
        trackedItems.forEach(uuid -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Uuid", uuid);
            tracked.add(entry);
        });
        tag.put("TrackedItems", tracked);
        if (brewTask != null) tag.put("BrewTask", brewTask.save(registryAccess()));
    }

    record BrewIngredient(UUID id, ItemStack stack) {}
    record BrewStep(UUID ingredientId, ItemStack expectedIngredient, ItemStack result) {}
    private record BrewTask(UUID ingredientId, ItemStack expectedIngredient, ItemStack result,
                            int remainingTicks, int missingTicks) {
        BrewTask withProgress(int remainingTicks) {
            return new BrewTask(ingredientId, expectedIngredient, result, remainingTicks, 0);
        }

        BrewTask withMissingTicks(int missingTicks) {
            return new BrewTask(ingredientId, expectedIngredient, result, remainingTicks, missingTicks);
        }

        CompoundTag save(net.minecraft.core.HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("IngredientId", ingredientId);
            tag.put("ExpectedIngredient", expectedIngredient.save(registries));
            tag.put("Result", result.save(registries));
            tag.putInt("RemainingTicks", remainingTicks);
            tag.putInt("MissingTicks", missingTicks);
            return tag;
        }

        static Optional<BrewTask> load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            if (!tag.hasUUID("IngredientId")) return Optional.empty();
            Optional<ItemStack> expected = ItemStack.parse(registries, tag.getCompound("ExpectedIngredient"));
            Optional<ItemStack> result = ItemStack.parse(registries, tag.getCompound("Result"));
            if (expected.isEmpty() || result.isEmpty()) return Optional.empty();
            return Optional.of(new BrewTask(tag.getUUID("IngredientId"), expected.get(), result.get(),
                    tag.getInt("RemainingTicks"), tag.getInt("MissingTicks")));
        }
    }
}
