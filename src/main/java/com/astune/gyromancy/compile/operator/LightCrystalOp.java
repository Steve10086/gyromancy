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

/**
 * Grows light crystals inside the effect's active geometry when mounted.
 *
 * <p>No array or operator mounts this payload yet; it only exists so light
 * crystals can be produced once a matching effect is authored.
 */
public final class LightCrystalOp extends CrystalGenOp {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "light_crystal");

    public static final Codec<LightCrystalOp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.optionalFieldOf("current_crystal")
                    .forGetter(op -> Optional.ofNullable(op.currentCrystal())),
            Codec.DOUBLE.optionalFieldOf("place_probability", BASE_PLACE_PROBABILITY)
                    .forGetter(LightCrystalOp::placeProbability)
    ).apply(instance, LightCrystalOp::fromCodec));

    public LightCrystalOp() {
        this(null, BASE_PLACE_PROBABILITY);
    }

    public LightCrystalOp(BlockPos currentCrystal, double placeProbability) {
        super(currentCrystal, placeProbability);
    }

    private static LightCrystalOp fromCodec(Optional<BlockPos> currentCrystal, double placeProbability) {
        return new LightCrystalOp(currentCrystal.orElse(null), placeProbability);
    }

    @Override
    protected ElementType element() {
        return ElementType.LIGHT;
    }

    @Override
    protected Block crystalBlock() {
        return ModBlocks.LIGHT_CRYSTAL.get();
    }

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<LightCrystalOp> codec() {
        return CODEC;
    }
}
