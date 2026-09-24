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
 * Grows dark crystals inside the effect's active geometry when mounted.
 *
 * <p>No array or operator mounts this payload yet; it only exists so dark
 * crystals can be produced once a matching effect is authored.
 */
public final class DarkCrystalOp extends CrystalGenOp {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "dark_crystal");

    public static final Codec<DarkCrystalOp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.optionalFieldOf("current_crystal")
                    .forGetter(op -> Optional.ofNullable(op.currentCrystal())),
            Codec.DOUBLE.optionalFieldOf("place_probability", BASE_PLACE_PROBABILITY)
                    .forGetter(DarkCrystalOp::placeProbability)
    ).apply(instance, DarkCrystalOp::fromCodec));

    public DarkCrystalOp() {
        this(null, BASE_PLACE_PROBABILITY);
    }

    public DarkCrystalOp(BlockPos currentCrystal, double placeProbability) {
        super(currentCrystal, placeProbability);
    }

    private static DarkCrystalOp fromCodec(Optional<BlockPos> currentCrystal, double placeProbability) {
        return new DarkCrystalOp(currentCrystal.orElse(null), placeProbability);
    }

    @Override
    protected ElementType element() {
        return ElementType.DARK;
    }

    @Override
    protected Block crystalBlock() {
        return ModBlocks.DARK_CRYSTAL.get();
    }

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<DarkCrystalOp> codec() {
        return CODEC;
    }
}
