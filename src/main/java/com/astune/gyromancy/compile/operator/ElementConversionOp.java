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

public class ElementConversionOp extends OnEntityTickOp {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "element_conversion");
    private static final Codec<ElementType> ELEMENT_CODEC = Codec.STRING.xmap(
            name -> ElementType.valueOf(name.toUpperCase(Locale.ROOT)),
            element -> element.name().toLowerCase(Locale.ROOT));
    public static final Codec<ElementConversionOp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ELEMENT_CODEC.fieldOf("element").forGetter(op -> op.element),
            Codec.STRING.fieldOf("stored_mana_key").forGetter(op -> op.storedManaKey),
            Codec.INT.fieldOf("interval").forGetter(op -> op.interval),
            Codec.DOUBLE.fieldOf("volume_loss").forGetter(op -> op.volumeLoss),
            Codec.DOUBLE.fieldOf("equilibrium").forGetter(op -> op.equilibrium),
            Codec.DOUBLE.fieldOf("max_volume_element_level").forGetter(op -> op.maxVolumeElementLevel),
            Codec.DOUBLE.fieldOf("element_per_volume").forGetter(op -> op.elementPerVolume),
            Codec.DOUBLE.fieldOf("conversion_cost").forGetter(op -> op.conversionCost),
            Codec.DOUBLE.fieldOf("mana_to_volume").forGetter(op -> op.manaToVolume)
    ).apply(instance, ElementConversionOp::new));

    private final ElementType element;
    private final String storedManaKey;
    private final int interval;
    private final double volumeLoss;
    private final double equilibrium;
    private final double maxVolumeElementLevel;
    private final double elementPerVolume;
    private final double conversionCost;
    private final double manaToVolume;

    public ElementConversionOp(ElementType element, String storedManaKey, int interval,
                               double volumeLoss, double equilibrium, double maxVolumeElementLevel,
                               double elementPerVolume, double conversionCost, double manaToVolume) {
        this.element = element;
        this.storedManaKey = storedManaKey;
        this.interval = interval;
        this.volumeLoss = volumeLoss;
        this.equilibrium = equilibrium;
        this.maxVolumeElementLevel = maxVolumeElementLevel;
        this.elementPerVolume = elementPerVolume;
        this.conversionCost = conversionCost;
        this.manaToVolume = manaToVolume;
    }

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<ElementConversionOp> codec() {
        return CODEC;
    }

    ElementType element() { return element; }
    String storedManaKey() { return storedManaKey; }
    int interval() { return interval; }

    @Override
    public void onEntityTick(EntityTickContext ctx) {
        if (!ctx.isFullyGrown() || ctx.tickCount() % interval != 0) return;
        ctx.put(storedManaKey, convertMana(ctx, ctx.longValue(storedManaKey)));
        afterExchange(ctx);
    }

    private long convertMana(EntityTickContext ctx, long storedMana) {
        if (ctx.isClientSide()) return storedMana;

        List<BlockPos> positions = MagicBallGeometry.containedPositions(ctx.position(), ctx.targetSize());
        if (positions.isEmpty() || manaToVolume <= 0.0) return storedMana;

        double volume = MagicBallGeometry.volume(ctx.targetSize());
        double average = positions.stream()
                .mapToLong(pos -> ctx.elementStorage().get(ctx.level(), pos).get(element))
                .average()
                .orElse(0.0);

        long manaBudget = (long)Math.ceil((volume * 0.05 + volumeLoss * interval) / manaToVolume);
        if (maxVolumeElementLevel > 0.0) {
            double maxVolumeByElement = average / maxVolumeElementLevel;
            double allowedVolumeGain = Math.max(0.0, maxVolumeByElement - volume);
            manaBudget = Math.min(manaBudget, (long)Math.floor(allowedVolumeGain / manaToVolume));
        }

        storedMana = Math.min(storedMana, manaBudget);
        storedMana += drainMana(ctx, positions, Math.max(0L, manaBudget - storedMana));

        long manaToConvert = Math.min(storedMana, manaBudget);
        storedMana -= manaToConvert;
        long elementPerBlock = (long)Math.floor(Math.max(0.0,
                elementPerVolume * manaToConvert * manaToVolume - conversionCost * interval) / positions.size());
        if (elementPerBlock <= 0L) return storedMana;

        for (BlockPos pos : positions) {
            var current = ctx.elementStorage().get(ctx.level(), pos);
            ctx.elementStorage().set(ctx.level(), pos, current.withValue(element, current.get(element) + elementPerBlock));
        }
        return storedMana;
    }

    private long drainMana(EntityTickContext ctx, List<BlockPos> positions, long needed) {
        long drained = 0L;
        for (BlockPos pos : positions) {
            if (drained >= needed) break;
            var current = ctx.elementStorage().get(ctx.level(), pos);
            long currentMana = current.get(ElementType.MANA);
            long absorbed = Math.min(currentMana, needed - drained);
            if (absorbed == 0L) continue;
            drained += absorbed;
            ctx.elementStorage().set(ctx.level(), pos, current.withValue(ElementType.MANA, currentMana - absorbed));
        }
        return drained;
    }

    protected void afterExchange(EntityTickContext ctx) {}
}
