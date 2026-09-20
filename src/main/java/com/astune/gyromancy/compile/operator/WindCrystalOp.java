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

/** Grows wind crystals inside the effect's active geometry. */
public final class WindCrystalOp extends CrystalGenOp {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "wind_crystal");

    public static final Codec<WindCrystalOp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.optionalFieldOf("current_crystal")
                    .forGetter(op -> Optional.ofNullable(op.currentCrystal())),
            Codec.DOUBLE.optionalFieldOf("place_probability", BASE_PLACE_PROBABILITY)
                    .forGetter(WindCrystalOp::placeProbability)
    ).apply(instance, WindCrystalOp::fromCodec));

    public WindCrystalOp() {
        this(null, BASE_PLACE_PROBABILITY);
    }

    public WindCrystalOp(BlockPos currentCrystal, double placeProbability) {
        super(currentCrystal, placeProbability);
    }

    private static WindCrystalOp fromCodec(Optional<BlockPos> currentCrystal, double placeProbability) {
        return new WindCrystalOp(currentCrystal.orElse(null), placeProbability);
    }

    @Override
    protected ElementType element() {
        return ElementType.WIND;
    }

    @Override
    protected Block crystalBlock() {
        return ModBlocks.WIND_CRYSTAL.get();
    }

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<WindCrystalOp> codec() {
        return CODEC;
    }
}