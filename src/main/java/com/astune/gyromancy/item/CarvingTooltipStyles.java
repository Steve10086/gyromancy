package com.astune.gyromancy.item;

import com.astune.gyromancy.api.symbol.SymbolRole;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * Visual language for carved rune lines in an item tooltip.
 *
 * <p>The vanilla tooltip component API does not expose an arbitrary point
 * size. The two font faces below still give the two sections different font
 * metrics, while the title, weight, colours, and tree branches provide the
 * stronger visual scale and hierarchy.
 */
final class CarvingTooltipStyles {
    private static final ResourceLocation DEFAULT_FONT =
            ResourceLocation.fromNamespaceAndPath("minecraft", "default");
    private static final ResourceLocation RUNE_FONT =
            ResourceLocation.fromNamespaceAndPath("minecraft", "uniform");

    static final Style FRAME = Style.EMPTY
            .withFont(DEFAULT_FONT)
            .withColor(0x8A674A);

    static final Style PARAMETER_RUNE = Style.EMPTY
            .withFont(RUNE_FONT)
            .withColor(0xB8D9FF);

    static final Style CENTER_RUNE = Style.EMPTY
            .withFont(RUNE_FONT)
            .withColor(0xFFB86B)
            .withBold(true);

    static final Style OUTER_CIRCLE = Style.EMPTY
            .withFont(RUNE_FONT)
            .withColor(0xD5A6FF)
            .withBold(true);

    static final Style UNKNOWN_RUNE = Style.EMPTY
            .withFont(RUNE_FONT)
            .withColor(0xD0D0D0);

    private CarvingTooltipStyles() {
    }

    static Style forRole(SymbolRole role) {
        return switch (role) {
            case OUTER_CIRCLE -> OUTER_CIRCLE;
            case CENTER_SYMBOL -> CENTER_RUNE;
            case PARAMETER_RUNE -> PARAMETER_RUNE;
            case UNKNOWN -> UNKNOWN_RUNE;
        };
    }
}
