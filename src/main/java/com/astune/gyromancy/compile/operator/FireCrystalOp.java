package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.registry.ModBlocks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

/** Grows fire crystals inside the effect's active geometry. */
public final class FireCrystalOp extends CrystalGenOp {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "fire_crystal");

    public static final Codec<FireCrystalOp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.optionalFieldOf("current_crystal")
                    .forGetter(op -> Optional.ofNullable(op.currentCrystal())),
            Codec.DOUBLE.optionalFieldOf("place_probability", BASE_PLACE_PROBABILITY)
                    .forGetter(FireCrystalOp::placeProbability)
    ).apply(instance, FireCrystalOp::fromCodec));

    public FireCrystalOp() {
        this(null, BASE_PLACE_PROBABILITY);
    }

    public FireCrystalOp(BlockPos currentCrystal, double placeProbability) {
        super(currentCrystal, placeProbability);
    }

    private static FireCrystalOp fromCodec(Optional<BlockPos> currentCrystal, double placeProbability) {
        return new FireCrystalOp(currentCrystal.orElse(null), placeProbability);
    }

    @Override
    protected ElementType element() {
        return ElementType.FIRE;
    }

    @Override
    protected Block crystalBlock() {
        return ModBlocks.FIRE_CRYSTAL.get();
    }

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<FireCrystalOp> codec() {
        return CODEC;
    }
}