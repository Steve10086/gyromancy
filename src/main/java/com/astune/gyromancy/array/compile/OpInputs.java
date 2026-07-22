package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.element.ElementType;

import java.util.ArrayList;
import java.util.List;

public final class OpInputs {
    private OpInputs() {}

    public static CompileResult<EffectAttributes> projectileAttributes(List<OpInput> inputs, ElementType element) {
        boolean inverted = false;
        List<MotionAttribute> motion = new ArrayList<>();
        for (OpInput input : inputs) {
            if (!(input instanceof OpInput.Rune rune)) continue;
            switch (rune.symbolName()) {
                case "arrow" -> motion.add(new MotionAttribute(rune.glyph().front(), rune.glyph().length()));
                case "revert" -> {
                    if (inverted) return fail("invalid_element_inverse", "Only one revert rune is supported");
                    if (element == ElementType.MANA) return fail("invalid_element_inverse", "Mana has no inverse element");
                    inverted = true;
                }
                default -> {}
            }
        }
        return new CompileResult.Success<>(new EffectAttributes(inverted, List.copyOf(motion)));
    }

    public static boolean hasRune(List<OpInput> inputs, String symbolName) {
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Rune rune && symbolName.equals(rune.symbolName())) return true;
        }
        return false;
    }

    private static <T> CompileResult<T> fail(String code, String message) {
        return new CompileResult.Failure<>(List.of(new CompileDiagnostic(code, message)));
    }
}
