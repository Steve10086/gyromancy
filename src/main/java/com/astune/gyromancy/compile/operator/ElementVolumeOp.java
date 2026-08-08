package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.util.MagicBallGeometry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/**
 * Controls a magic ball's target volume from its surrounding element concentration and available mana.
 */
public final class ElementVolumeOp extends OnEntityTickOp {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "element_volume");
    private static final Codec<ElementType> ELEMENT_CODEC = Codec.STRING.xmap(
            name -> ElementType.valueOf(name.toUpperCase(Locale.ROOT)),
            element -> element.name().toLowerCase(Locale.ROOT));
    public static final Codec<ElementVolumeOp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ELEMENT_CODEC.fieldOf("element").forGetter(op -> op.element),
            Codec.STRING.optionalFieldOf("stored_mana_key", "").forGetter(op -> op.storedManaKey),
            Codec.INT.fieldOf("interval").forGetter(op -> op.interval),
            Codec.DOUBLE.fieldOf("volume_loss").forGetter(op -> op.volumeLoss),
            Codec.DOUBLE.fieldOf("equilibrium").forGetter(op -> op.equilibrium),
            Codec.DOUBLE.fieldOf("max_volume_element_level").forGetter(op -> op.maxVolumeElementLevel),
            Codec.DOUBLE.fieldOf("mana_to_volume").forGetter(op -> op.manaToVolume)
    ).apply(instance, ElementVolumeOp::new));

    private static final double DEFAULT_VOLUME_LOSS_PER_TICK = 0.01;
    private static final double DEFAULT_EQUILIBRIUM = 100.0;

    private final ElementType element;
    private final String storedManaKey;
    private final int interval;
    private final double volumeLoss;
    private final double equilibrium;
    private final double maxVolumeElementLevel;
    private final double manaToVolume;

    public ElementVolumeOp(ElementType element, String storedManaKey, int interval,
                           double volumeLoss, double equilibrium, double maxVolumeElementLevel,
                           double manaToVolume) {
        this.element = element;
        this.storedManaKey = storedManaKey;
        this.interval = interval;
        this.volumeLoss = volumeLoss;
        this.equilibrium = equilibrium;
        this.maxVolumeElementLevel = maxVolumeElementLevel;
        this.manaToVolume = manaToVolume;
    }

    public static ElementVolumeOp stability(ElementType element) {
        return new ElementVolumeOp(element, "", 1,
                DEFAULT_VOLUME_LOSS_PER_TICK, DEFAULT_EQUILIBRIUM,
                0.0, 0.0);
    }

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<ElementVolumeOp> codec() {
        return CODEC;
    }

    ElementType element() { return element; }
    String storedManaKey() { return storedManaKey; }
    int interval() { return interval; }

    @Override
    public void onEntityTick(EntityTickContext ctx) {
        if (ctx.isClientSide() || !ctx.isFullyGrown() || interval <= 0 || ctx.tickCount() % interval != 0) return;

        List<BlockPos> positions = MagicBallGeometry.containedPositions(ctx.position(), ctx.targetSize());
        if (positions.isEmpty()) return;

        double volume = MagicBallGeometry.volume(ctx.targetSize());
        double average = (double) ctx.elementStorage().sum(
                ctx.level(), positions, element) / positions.size();

        double lostVolume = lostVolume(volume, average);
        volume -= lostVolume;
        ctx.setAverageElementLevel(average);

        if (manaToVolume <= 0.0 || storedManaKey.isEmpty()) {
            if (lostVolume > 0.0) ctx.setTargetVolume(volume);
            return;
        }

        long storedMana = storedManaKey.isEmpty() ? 0L : ctx.longValue(storedManaKey);
        long manaBudget = manaBudget(volume, average);
        storedMana = Math.min(storedMana, manaBudget);
        storedMana += drainMana(ctx, positions, Math.max(0L, manaBudget - storedMana));

        long manaToGrow = Math.min(storedMana, manaBudget);
        if (!storedManaKey.isEmpty()) ctx.put(storedManaKey, storedMana - manaToGrow);
        if (lostVolume > 0.0 || manaToGrow > 0L) {
            ctx.setTargetVolume(grownVolume(volume, manaToGrow));
        }
    }

    double lostVolume(double volume, double averageElementLevel) {
        if (averageElementLevel >= equilibrium * volume) return 0.0;
        return Math.max(0.0, Math.min(
                volume * 0.005 + volumeLoss * interval,
                volume - MagicBallGeometry.volume(0.1F)));
    }

    long manaBudget(double volume, double averageElementLevel) {
        long budget = (long)Math.ceil((volume * 0.05 + volumeLoss * interval) / manaToVolume);
        if (maxVolumeElementLevel > 0.0) {
            double maxVolumeByElement = averageElementLevel / maxVolumeElementLevel;
            double allowedVolumeGain = Math.max(0.0, maxVolumeByElement - volume);
            budget = Math.min(budget, (long)Math.floor(allowedVolumeGain / manaToVolume));
        }
        return Math.max(0L, budget);
    }

    double grownVolume(double volume, long manaToGrow) {
        return volume + manaToGrow * manaToVolume;
    }

    private static long drainMana(EntityTickContext ctx, List<BlockPos> positions, long needed) {
        return ctx.elementStorage().consume(
                ctx.level(), positions, ElementType.MANA, needed);
    }
}
