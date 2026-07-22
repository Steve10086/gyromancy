package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.compile.operator.ElementConversionOp;
import com.astune.gyromancy.compile.operator.EntityTickContext;
import com.astune.gyromancy.compile.operator.OnEntityTickOp;
import com.astune.gyromancy.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

public class WaterBallEntity extends MagicBallEntity {
    private static final EntityDataAccessor<ItemStack> DATA_POTION_STATE =
            SynchedEntityData.defineId(WaterBallEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final int EFFECT_INTERVAL = 10;
    private static final double FIRE_VOLUME_LOSS = 0.1;
    private static final double FIRE_EQUILIBRIUM = 1000.0;
    private static final double MAX_VOLUME_FIRE_LEVEL = 200.0;
    private static final double FIRE_PER_VOLUME = 1000.0;
    private static final double FIRE_CONVERSION_COST = 10.0;
    private static final double MANA_TO_VOLUME = 0.05;
    private static final String STORED_MANA_KEY = "storedMana";
    private static final int BREWING_TIME = PotionBrewing.BREWING_TIME_SECONDS * 20;
    private static final int MAX_BREWING_ROUTE_LENGTH = 4;
    private static final int MISSING_ITEM_GRACE_TICKS = 20;
    private static final double BURST_PUSH_STRENGTH = 1.2;
    private final Set<UUID> trackedItems = new HashSet<>();
    private final Map<String, Object> runtimeData = new HashMap<>();
    private final OnEntityTickOp elementConversion = new ElementConversionOp(ElementType.WATER, STORED_MANA_KEY,
            EFFECT_INTERVAL, FIRE_VOLUME_LOSS, FIRE_EQUILIBRIUM, MAX_VOLUME_FIRE_LEVEL,
            FIRE_PER_VOLUME, FIRE_CONVERSION_COST, MANA_TO_VOLUME);
    private Vec3 acceleration = Vec3.ZERO;
    private Vec3 pendingVelocity = Vec3.ZERO;
    private Vec3 pendingAcceleration = Vec3.ZERO;
    private ItemStack potionState = defaultPotionState();
    private BrewTask brewTask;
    private boolean launched;

    public WaterBallEntity(EntityType<WaterBallEntity> type, Level level) {
        super(type, level, ElementType.WATER);
    }

    public WaterBallEntity(Level level, Vec3 pos, Vec3 velocity, double arrowSizeSum,
                           double liftDirection, Vec3 acceleration, float size) {
        this(ModEntities.WATER_BALL.get(), level);
        setBallSize(size);
        setPos(pos);
        pendingVelocity = launchVelocity(velocity, arrowSizeSum, liftDirection);
        pendingAcceleration = acceleration;
        setDeltaMovement(Vec3.ZERO);
    }

    public ItemStack getPotionState() {
        return entityData.get(DATA_POTION_STATE).copy();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_POTION_STATE, defaultPotionState());
    }

    private static ItemStack defaultPotionState() {
        return PotionContents.createItemStack(Items.POTION, Potions.WATER);
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
        elementConversion.onEntityTick(EntityTickContext.from(this, runtimeData, acceleration));
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
            pushFrontEntities();
            fillWithFlowingWater(containedPositions(getTargetSize() * 2));
        }
        releaseItems();
        discard();
    }

    private void pushFrontEntities() {
        Vec3 direction = getDeltaMovement();
        if (direction.lengthSqr() < 1.0E-8) return;
        direction = direction.normalize();
        double radius = getTargetSize() / 2.0;
        Vec3 front = position().add(0.0, radius, 0.0).add(direction.scale(radius));
        double range = Math.max(1.0, radius);
        for (Entity entity : level().getEntities(this, getBoundingBox().inflate(range).expandTowards(direction.scale(range)))) {
            if (!entity.isAlive() || entity instanceof ItemEntity || front.distanceToSqr(entity.getBoundingBox().getCenter()) > range * range) continue;
            entity.push(direction.x * BURST_PUSH_STRENGTH, direction.y * BURST_PUSH_STRENGTH, direction.z * BURST_PUSH_STRENGTH);
        }
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
            double angle = tickCount * 0.08 + item.getUUID().getLeastSignificantBits() * 0.0001;
            double orbitRadius = radius * 2.0 / 3.0;
            Vec3 target = center.add(Math.cos(angle) * orbitRadius, 0.0, Math.sin(angle) * orbitRadius);
            item.setDeltaMovement(target.subtract(item.position()).scale(0.2));
        }
    }

    private void tickBrewing() {
        if (!(level() instanceof ServerLevel server)) return;
        if (brewTask == null) {
            if (tickCount % EFFECT_INTERVAL == 0) tryStartBrewing();
            return;
        }

        double radius = getTargetSize() / 2.0;
        List<ItemEntity> ingredientEntities = new ArrayList<>();
        for (int i = 0; i < brewTask.ingredientIds().size(); i++) {
            Entity entity = server.getEntity(brewTask.ingredientIds().get(i));
            if (!(entity instanceof ItemEntity item) || !item.isAlive()) {
                int missingTicks = brewTask.missingTicks() + 1;
                brewTask = missingTicks > MISSING_ITEM_GRACE_TICKS ? null : brewTask.withMissingTicks(missingTicks);
                return;
            }
            ItemStack expected = brewTask.expectedIngredients().get(i);
            if (!inSphere(item.position(), radius)
                    || item.getItem().isEmpty()
                    || !ItemStack.isSameItemSameComponents(item.getItem(), expected)) {
                brewTask = null;
                return;
            }
            ingredientEntities.add(item);
        }

        ItemStack currentOutput = brewRouteOutput(brewTask.expectedIngredients(), potionState);
        if (!ItemStack.isSameItemSameComponents(currentOutput, brewTask.result())) {
            brewTask = null;
            return;
        }

        int remaining = brewTask.remainingTicks() - 1;
        if (remaining > 0) {
            brewTask = brewTask.withProgress(remaining);
            return;
        }

        completeBrewing(ingredientEntities, currentOutput);
        brewTask = null;
    }

    private void tryStartBrewing() {
        List<BrewIngredient> ingredients = currentIngredients();
        findBrewRoute(potionState, ingredients, this::brewOutput).ifPresent(route ->
                brewTask = new BrewTask(route.ingredientIds(), route.expectedIngredients(), route.result(),
                        BREWING_TIME * route.expectedIngredients().size(), 0));
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
                    brewTask = null;
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

    private ItemStack brewRouteOutput(List<ItemStack> ingredients, ItemStack inputPotion) {
        ItemStack output = inputPotion.copyWithCount(1);
        for (ItemStack ingredient : ingredients) {
            output = brewOutput(ingredient, output);
            if (output.isEmpty()) return ItemStack.EMPTY;
        }
        return output.copyWithCount(1);
    }

    static Optional<BrewRoute> findBrewRoute(ItemStack currentPotion,
                                             List<BrewIngredient> ingredients,
                                             BiFunction<ItemStack, ItemStack, ItemStack> mixer) {
        var route = BrewingRoutePlanner.findIngredientRoute(currentPotion.copyWithCount(1), ingredients,
                MAX_BREWING_ROUTE_LENGTH,
                (ingredient, potion) -> mixer.apply(ingredient.stack(), potion),
                WaterBallEntity::isAllowedPotion,
                ItemStack::isSameItemSameComponents,
                WaterBallEntity::hasPotionEffect);
        if (route.isEmpty()) return Optional.empty();
        ItemStack output = currentPotion.copyWithCount(1);
        List<UUID> ids = new ArrayList<>();
        List<ItemStack> expected = new ArrayList<>();
        for (int index : route.get()) {
            BrewIngredient ingredient = ingredients.get(index);
            output = mixer.apply(ingredient.stack(), output);
            ids.add(ingredient.id());
            expected.add(ingredient.stack().copyWithCount(1));
        }
        return Optional.of(new BrewRoute(List.copyOf(ids), List.copyOf(expected), output.copyWithCount(1)));
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

    private void completeBrewing(List<ItemEntity> ingredientEntities, ItemStack result) {
        setPotionState(result.copyWithCount(1));
        ingredientEntities.forEach(this::consumeIngredient);
        applyPotionEffects();
    }

    private void setPotionState(ItemStack result) {
        List<Holder<MobEffect>> oldEffects = potionEffects(potionState);
        potionState = result.copyWithCount(1);
        entityData.set(DATA_POTION_STATE, potionState.copy());

        double radius = getTargetSize() / 2.0;
        for (LivingEntity entity : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox(), LivingEntity::isAlive)) {
            if (!entityInSphere(entity, radius)) continue;
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
            if (!entityInSphere(entity, radius)) continue;
            for (Holder<MobEffect> effect : effects) {
                int amplifier = effectAmplifier();
                if (effect.value().isInstantenous()) effect.value().applyInstantenousEffect(this, this, entity, amplifier, 1.0);
                else entity.addEffect(new MobEffectInstance(effect, 40, amplifier));
            }
        }
    }

    private boolean entityInSphere(Entity entity, double radius) {
        return inSphere(entity.getBoundingBox().getCenter(), radius);
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
        if (tag.contains("StoredMana")) runtimeData.put(STORED_MANA_KEY, tag.getLong("StoredMana"));
        if (tag.contains("PotionState", Tag.TAG_COMPOUND)) {
            potionState = ItemStack.parse(registryAccess(), tag.getCompound("PotionState")).orElse(potionState);
            entityData.set(DATA_POTION_STATE, potionState.copy());
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
        tag.putLong("StoredMana", ((Number)runtimeData.getOrDefault(STORED_MANA_KEY, 0L)).longValue());
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
    record BrewRoute(List<UUID> ingredientIds, List<ItemStack> expectedIngredients, ItemStack result) {}
    private record BrewTask(List<UUID> ingredientIds, List<ItemStack> expectedIngredients, ItemStack result,
                            int remainingTicks, int missingTicks) {
        BrewTask withProgress(int remainingTicks) {
            return new BrewTask(ingredientIds, expectedIngredients, result, remainingTicks, 0);
        }

        BrewTask withMissingTicks(int missingTicks) {
            return new BrewTask(ingredientIds, expectedIngredients, result, remainingTicks, missingTicks);
        }

        CompoundTag save(net.minecraft.core.HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            ListTag ids = new ListTag();
            ingredientIds.forEach(uuid -> {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("Uuid", uuid);
                ids.add(entry);
            });
            tag.put("IngredientIds", ids);
            ListTag expected = new ListTag();
            expectedIngredients.forEach(stack -> expected.add(stack.save(registries)));
            tag.put("ExpectedIngredients", expected);
            tag.put("Result", result.save(registries));
            tag.putInt("RemainingTicks", remainingTicks);
            tag.putInt("MissingTicks", missingTicks);
            return tag;
        }

        static Optional<BrewTask> load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            Optional<ItemStack> result = ItemStack.parse(registries, tag.getCompound("Result"));
            if (result.isEmpty()) return Optional.empty();
            List<UUID> ids = new ArrayList<>();
            for (Tag value : tag.getList("IngredientIds", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag)value;
                if (!entry.hasUUID("Uuid")) return Optional.empty();
                ids.add(entry.getUUID("Uuid"));
            }
            List<ItemStack> expected = new ArrayList<>();
            for (Tag value : tag.getList("ExpectedIngredients", Tag.TAG_COMPOUND)) {
                Optional<ItemStack> stack = ItemStack.parse(registries, (CompoundTag)value);
                if (stack.isEmpty()) return Optional.empty();
                expected.add(stack.get());
            }
            if (ids.isEmpty() || ids.size() != expected.size()) return Optional.empty();
            return Optional.of(new BrewTask(List.copyOf(ids), List.copyOf(expected), result.get(),
                    tag.getInt("RemainingTicks"), tag.getInt("MissingTicks")));
        }
    }
}
