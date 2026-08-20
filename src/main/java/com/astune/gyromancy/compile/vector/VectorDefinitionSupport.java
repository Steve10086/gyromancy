package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.VectorCompiler;

import java.util.ArrayList;
import java.util.List;

final class VectorDefinitionSupport {
    private VectorDefinitionSupport() {}

    static VectorCompiler compiler() {
        return new VectorCompiler(VectorOpDefinitions.definitions());
    }

    static List<OpInputMatcher> accepted(boolean acceptsCurl) {
        List<OpInputMatcher> accepted = new ArrayList<>(List.of(
                OpInputMatcher.rune("arrow"),
                OpInputMatcher.rune("arrow_up"),
                OpInputMatcher.boundary(SymbolRole.OUTER_CIRCLE),
                OpInputMatcher.rawGroup(SymbolRole.OUTER_CIRCLE)));
        if (acceptsCurl) accepted.add(OpInputMatcher.rune("curl"));
        return List.copyOf(accepted);
    }

    static boolean containsRune(List<OpInput> inputs, String name) {
        return inputs.stream()
                .filter(OpInput.Rune.class::isInstance)
                .map(OpInput.Rune.class::cast)
                .anyMatch(rune -> name.equals(rune.symbolName()));
    }
}
