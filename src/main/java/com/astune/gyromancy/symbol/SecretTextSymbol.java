package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.ParameterRune;
import net.minecraft.resources.ResourceLocation;

/**
 * A parameter-rune object backed by one {@link SecretText} discriminator.
 *
 * <p>Every 秘文 has its own instance, while all instances share this class.
 * Its 3×3 resource is recognized by {@link SecretTextMatcher} before the
 * regular skeleton matcher runs.
 */
public final class SecretTextSymbol extends ParameterSymbol {

    private final SecretText type;

    SecretTextSymbol(SecretText type) {
        super("secret_text_" + type.resourceName(), SkeletonMatcher.DEFAULT_THRESHOLDS);
        this.type = type;
    }

    public SecretText type() {
        return type;
    }

    /** Resolves a persisted symbol id back to its shared-class 秘文 object. */
    public static SecretTextSymbol fromId(ResourceLocation id) {
        if (id == null) return null;
        for (SecretText secretText : SecretText.values()) {
            SecretTextSymbol symbol = secretText.symbol();
            if (symbol.id().equals(id)) return symbol;
        }
        return null;
    }

    @Override
    public String resourcePath() {
        return type.resourcePath();
    }

    /** Creates the runtime representation used by parameter-rune consumers. */
    public ParameterRune toParameterRune(float value) {
        return new ParameterRune(id(), value, type.resourceName());
    }
}
