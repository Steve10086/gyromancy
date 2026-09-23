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
                OpInputMatcher.rune("revert"),
                OpInputMatcher.boundary(SymbolRole.OUTER_CIRCLE),
                OpInputMatcher.rawGroup(SymbolRole.OUTER_CIRCLE),
                OpInputMatcher.secretText()));
        if (acceptsCurl) accepted.add(OpInputMatcher.rune("curl"));
        return List.copyOf(accepted);
    }

    /** Accepted inputs for the arrow and arrow_up static vectors. */
    static List<OpInputMatcher> staticAccepted() {
        return List.of(
                OpInputMatcher.rune("arrow"),
                OpInputMatcher.rune("arrow_up"),
                OpInputMatcher.rune("revert"),
                OpInputMatcher.rune("curl"),
                OpInputMatcher.boundary(SymbolRole.OUTER_CIRCLE),
                OpInputMatcher.rawGroup(SymbolRole.OUTER_CIRCLE),
                OpInputMatcher.secretText());
    }

    /** Accepted inputs for the generic raw-group static vector, which adds no vector of its own. */
    static List<OpInputMatcher> rawGroupAccepted() {
        return List.of(
                OpInputMatcher.rawGroup(),
                OpInputMatcher.rune("revert"),
                OpInputMatcher.rune("curl"),
                OpInputMatcher.boundary(SymbolRole.OUTER_CIRCLE),
                OpInputMatcher.secretText());
    }

    /** The shared revert and scaler runes every vector op consumes. */
    static List<OpInputMatcher> modifiers() {
        return List.of(
                OpInputMatcher.rune("revert"),
                OpInputMatcher.secretText());
    }

    static boolean containsRune(List<OpInput> inputs, String name) {
        return inputs.stream()
                .filter(OpInput.Rune.class::isInstance)
                .map(OpInput.Rune.class::cast)
                .anyMatch(rune -> name.equals(rune.symbolName()));
    }
}
