package com.astune.gyromancy.symbol;

/**
 * Discriminators for the compact 秘文 (secret-text) rune set.
 *
 * <p>Each constant identifies one resource by its explicit filename. Numeric
 * filenames cannot be Java enum identifiers, so the enum name and resource
 * name are intentionally separate. Each enum value owns one independent
 * parameter-rune object; all of those objects share {@link SecretTextSymbol}
 * as their class and use this enum as their discriminator.
 */
public enum SecretText {
    SECRET_1("1"),
    SECRET_2("2"),
    SECRET_3("3"),
    SECRET_4("4"),
    SECRET_5("5"),
    SECRET_6("6"),
    SECRET_7("7"),
    SECRET_8("8"),
    SECRET_9("9"),
    SECRET_10("10"),
    SECRET_11("11"),
    SECRET_12("12"),
    SECRET_13("13");

    public static final int SIZE = 3;

    private final String resourceName;
    private final SecretTextSymbol symbol;

    SecretText(String resourceName) {
        this.resourceName = resourceName;
        this.symbol = new SecretTextSymbol(this);
    }

    public String resourceName() {
        return resourceName;
    }

    public String resourcePath() {
        return "/assets/gyromancy/textures/secret_text/" + resourceName + ".png";
    }

    /** The independent parameter-rune object for this enum discriminator. */
    public SecretTextSymbol symbol() {
        return symbol;
    }
}
