package com.astune.gyromancy.api.symbol;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/**
 * Defines the role a symbol plays within a magic array.
 * Extracted as a standalone enum to be shared by {@link SymbolTemplate} and {@link SymbolMatch}.
 */
public enum SymbolRole implements StringRepresentable {
    /** The outermost boundary circle that encloses the array */
    OUTER_CIRCLE("outer_circle"),
    /** The central function-defining symbol */
    CENTER_SYMBOL("center_symbol"),
    /** A parameter-modifying rune */
    PARAMETER_RUNE("parameter_rune"),
    /** Unknown or unclassified */
    UNKNOWN("unknown");

    public static final Codec<SymbolRole> CODEC =
            StringRepresentable.fromEnum(SymbolRole::values);

    private final String name;

    SymbolRole(String name) {
        this.name = name;
    }

    @Override
    @NotNull
    public String getSerializedName() {
        return name;
    }
}
