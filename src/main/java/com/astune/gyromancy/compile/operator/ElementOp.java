package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.RegisteredOp;
import com.astune.gyromancy.symbol.SymbolCatalog;
import com.astune.gyromancy.util.MagicBallGeometry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static java.lang.Math.max;

@RegisteredOp
public final class ElementOp extends OnEntityTickOp {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "element");
    private static final ResourceLocation ENGAGING_SYMBOL =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "engaging");
    private static final int ELEMENT_EXCHANGE_INTERVAL = 10;
    private static final ElementType RELEASE_ELEMENT = ElementType.MANA;
    private static final Codec<ElementType> ELEMENT_CODEC = Codec.STRING.xmap(
            name -> ElementType.valueOf(name.toUpperCase(Locale.ROOT)),
            element -> element.name().toLowerCase(Locale.ROOT));
    public static final Codec<ElementOp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ELEMENT_CODEC.optionalFieldOf("absorbed_element", ElementType.MANA)
                    .forGetter(ElementOp::absorbedElement),
            Codec.FLOAT.optionalFieldOf("mana_expend_factor", 0.0F)
                    .forGetter(op -> op.manaExpendFactor),
            Codec.FLOAT.optionalFieldOf("stored_mana", 0.0F)
                    .forGetter(op -> op.storedMana)
    ).apply(instance, ElementOp::new));

    private final PositionedGlyph boundary;
    private final List<OpInput> matchedInputs;
    private final List<OpInput> inputs;
    private final ElementType absorbedElement;
    private float manaExpendFactor;
    private float storedMana;

    public ElementOp() {
        this(ElementType.MANA);
    }

    public ElementOp(ElementType absorbedElement) {
        this(absorbedElement, 1.0F, 0.0F);
    }

    public ElementOp(ElementType absorbedElement, float manaExpendFactor, float storedMana) {
        this.boundary = null;
        this.matchedInputs = List.of();
        this.inputs = List.of();
        this.absorbedElement = absorbedElement;
        this.manaExpendFactor = manaExpendFactor;
        this.storedMana = storedMana;
    }
    public List<OpInput> matchedInputs() {
        return matchedInputs;
    }

    public ElementType absorbedElement() {
        return absorbedElement;
    }

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<ElementOp> codec() {
        return CODEC;
    }

    @Override
    public void onEntityTick(EntityTickContext ctx) {
        if (ctx.isClientSide() || ctx.tickCount() % ELEMENT_EXCHANGE_INTERVAL != 0) return;

        if (manaExpendFactor <= 0.0F) manaExpendFactor = 2.0F * ctx.targetSize();
        float target = ctx.targetSize();
        double radius = target / 2.0;

        List<BlockPos> absorbPositions = BlockPos.betweenClosedStream(ctx.bounds().inflate(target))
                .map(BlockPos::immutable)
                .filter(pos -> !inSphere(ctx, pos, radius) && inSphere(ctx, pos, 1.5 * radius))
                .toList();
        storedMana += ctx.elementStorage().drainAll(
                ctx.level(), absorbPositions, absorbedElement);

        if (storedMana == 0.0F) return;

        List<BlockPos> releasePositions = BlockPos.betweenClosedStream(ctx.bounds())
                .map(BlockPos::immutable)
                .filter(pos -> inSphere(ctx, pos, radius))
                .toList();
        long releaseBlockCount = max(1, releasePositions.size());
        float manaPerBlock = storedMana / releaseBlockCount;

        ctx.elementStorage().addAndScaleEach(
                ctx.level(), releasePositions, RELEASE_ELEMENT,
                manaPerBlock, manaExpendFactor);
        storedMana -= manaPerBlock * releasePositions.size();

        if (storedMana > 0.0F) releaseMana(ctx, BlockPos.containing(ctx.position()), manaPerBlock);
        storedMana = 0.0F;
    }

    private void releaseMana(EntityTickContext ctx, BlockPos pos, float manaPerBlock) {
        var current = ctx.elementStorage().get(ctx.level(), pos);
        long mana = (long)((current.get(RELEASE_ELEMENT) + manaPerBlock) * manaExpendFactor);
        ctx.elementStorage().set(ctx.level(), pos, current.withValue(RELEASE_ELEMENT, mana));
        storedMana -= manaPerBlock;
    }

    private static boolean inSphere(EntityTickContext ctx, BlockPos pos, double radius) {
        return MagicBallGeometry.inSphere(ctx.position(), pos.getCenter(), radius);
    }

    private static ElementType absorbedElementFromContent(List<OpInput> inputs) {
        for (OpInput input : inputs) {
            if (!(input instanceof OpInput.Rune rune)) continue;
            Optional<ElementType> element = elementFromSymbolName(rune.symbolName());
            if (element.isPresent()) return element.get();
        }
        return ElementType.MANA;
    }

    private static Optional<ElementType> elementFromSymbolName(String symbolName) {
        try {
            return Optional.of(ElementType.valueOf(symbolName.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
