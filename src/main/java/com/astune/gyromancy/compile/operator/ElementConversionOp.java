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
 * Converts mana remaining inside a magic ball into its configured element.
 */
public final class ElementConversionOp extends OnEntityTickOp {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "element_conversion");
    private static final Codec<ElementType> ELEMENT_CODEC = Codec.STRING.xmap(
            name -> ElementType.valueOf(name.toUpperCase(Locale.ROOT)),
            element -> element.name().toLowerCase(Locale.ROOT));
    public static final Codec<ElementConversionOp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ELEMENT_CODEC.fieldOf("element").forGetter(op -> op.element),
            Codec.INT.fieldOf("interval").forGetter(op -> op.interval)
    ).apply(instance, ElementConversionOp::new));

    private final ElementType element;
    private final int interval;

    public ElementConversionOp(ElementType element, int interval) {
        this.element = element;
        this.interval = interval;
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
    int interval() { return interval; }

    @Override
    public void onEntityTick(EntityTickContext ctx) {
        if (ctx.isClientSide() || !ctx.isFullyGrown() || interval <= 0 || ctx.tickCount() % interval != 0) return;

        List<BlockPos> positions = MagicBallGeometry.containedPositions(ctx.position(), ctx.targetSize());
        if (positions.isEmpty()) return;

        double convertedElement = ctx.consumePendingElementConversion(element) + drainMana(ctx, positions);
        long elementPerBlock = (long)Math.floor(convertedElement / positions.size());
        if (elementPerBlock <= 0L) return;

        for (BlockPos pos : positions) {
            var current = ctx.elementStorage().get(ctx.level(), pos);
            ctx.elementStorage().set(ctx.level(), pos,
                    current.withValue(element, current.get(element) + elementPerBlock));
        }
        ctx.setAverageElementLevel(ctx.averageElementLevel() + elementPerBlock);
    }

    private static long drainMana(EntityTickContext ctx, List<BlockPos> positions) {
        long drained = 0L;
        for (BlockPos pos : positions) {
            var current = ctx.elementStorage().get(ctx.level(), pos);
            long currentMana = Math.max(0L, current.get(ElementType.MANA));
            if (currentMana == 0L) continue;
            drained += currentMana;
            ctx.elementStorage().set(ctx.level(), pos, current.withValue(ElementType.MANA, 0L));
        }
        return drained;
    }
}
