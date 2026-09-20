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

/** Grows earth crystals inside the effect's active geometry when mounted. */
public final class EarthCrystalOp extends CrystalGenOp {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "earth_crystal");

    public static final Codec<EarthCrystalOp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.optionalFieldOf("current_crystal")
                    .forGetter(op -> Optional.ofNullable(op.currentCrystal())),
            Codec.DOUBLE.optionalFieldOf("place_probability", BASE_PLACE_PROBABILITY)
                    .forGetter(EarthCrystalOp::placeProbability)
    ).apply(instance, EarthCrystalOp::fromCodec));

    public EarthCrystalOp() {
        this(null, BASE_PLACE_PROBABILITY);
    }

    public EarthCrystalOp(BlockPos currentCrystal, double placeProbability) {
        super(currentCrystal, placeProbability);
    }

    private static EarthCrystalOp fromCodec(Optional<BlockPos> currentCrystal, double placeProbability) {
        return new EarthCrystalOp(currentCrystal.orElse(null), placeProbability);
    }

    @Override
    protected ElementType element() {
        return ElementType.EARTH;
    }

    @Override
    protected Block crystalBlock() {
        return ModBlocks.EARTH_CRYSTAL.get();
    }

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<EarthCrystalOp> codec() {
        return CODEC;
    }
}