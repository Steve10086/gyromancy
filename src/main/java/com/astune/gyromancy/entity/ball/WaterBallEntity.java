package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.registry.ModEntities;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class WaterBallEntity extends MagicBallEntity {
    private static final EntityDataAccessor<ItemStack> DATA_POTION_STATE =
            SynchedEntityData.defineId(WaterBallEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final String STORED_MANA_KEY = "storedMana";
    private ItemStack potionState = defaultPotionState();

    public WaterBallEntity(EntityType<WaterBallEntity> type, Level level) {
        super(type, level, ElementType.WATER);
    }

    public WaterBallEntity(Level level, Vec3 pos, Vec3 velocity, Vec3 acceleration, float size) {
        super(ModEntities.WATER_BALL.get(), level, ElementType.WATER, velocity, acceleration);
        setBallSize(size);
        setPos(pos);
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

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack heldStack = player.getItemInHand(hand);
        if (!heldStack.is(Items.GLASS_BOTTLE)) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.sidedSuccess(true);

        if (!player.hasInfiniteMaterials()) heldStack.shrink(1);
        ItemStack result = potionStateWithDefaultDuration();
        level().addFreshEntity(new ItemEntity(level(), player.getX(), player.getY(), player.getZ(), result));
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    private ItemStack potionStateWithDefaultDuration() {
        PotionContents contents = potionState.get(DataComponents.POTION_CONTENTS);
        if (contents == null || !contents.hasEffects()) return defaultPotionState();
        ItemStack result = potionState.copyWithCount(1);
        setPotionState(defaultPotionState());
        return result;
    }

    public List<ItemStack> getCollectedItems() {
        return currentItemsInSphere().stream()
                .map(item -> item.getItem().copy())
                .toList();
    }

    @Override
    protected boolean tickBeforePayload() {
        if (!super.tickBeforePayload()) return false;

        return true;
    }

    @Override
    protected void tickAfterPayload() {
        if (level().isClientSide) return;
        consumeDirectPotion();

        applyPotionEffects();
    }

    @Override
    protected Vec3 payloadAcceleration() {
        return acceleration;
    }

    private void consumeDirectPotion() {
        if (level().isClientSide) return;
        currentItemsInSphere().stream()
                .filter(item -> isAllowedPotion(item.getItem()))
                .filter(item -> hasPotionEffect(item.getItem()))
                .filter(item -> !ItemStack.isSameItemSameComponents(item.getItem().copyWithCount(1), potionState))
                .findFirst()
                .ifPresent(item -> {
                    setPotionState(item.getItem().copyWithCount(1));
                    consumeIngredient(item);
                });
    }

    private List<ItemEntity> currentItemsInSphere() {
        double radius = getTargetSize() / 2.0;
        return level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(0.25),
                        item -> item.isAlive() && inSphere(item.position(), radius)).stream()
                .sorted(Comparator.comparing(item -> item.getUUID().toString()))
                .toList();
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

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("AccelX")) acceleration = new Vec3(tag.getDouble("AccelX"), tag.getDouble("AccelY"), tag.getDouble("AccelZ"));
        if (tag.contains("StoredMana")) runtimeData().put(STORED_MANA_KEY, tag.getLong("StoredMana"));
        if (tag.contains("PotionState", Tag.TAG_COMPOUND)) {
            potionState = ItemStack.parse(registryAccess(), tag.getCompound("PotionState")).orElse(potionState);
            entityData.set(DATA_POTION_STATE, potionState.copy());
        }
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("AccelX", acceleration.x);
        tag.putDouble("AccelY", acceleration.y);
        tag.putDouble("AccelZ", acceleration.z);
        tag.putLong("StoredMana", ((Number)runtimeData().getOrDefault(STORED_MANA_KEY, 0L)).longValue());
        tag.put("PotionState", potionState.save(registryAccess()));
    }

}
