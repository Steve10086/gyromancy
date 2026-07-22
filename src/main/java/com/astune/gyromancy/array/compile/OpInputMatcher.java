package com.astune.gyromancy.array.compile;

public sealed interface OpInputMatcher permits OpInputMatcher.Rune, OpInputMatcher.Op {
    boolean matches(OpInput input);

    static OpInputMatcher rune(String symbolName) {
        return new Rune(symbolName);
    }

    static OpInputMatcher op(Class<? extends CompiledArrayNode> nodeType) {
        return new Op(nodeType);
    }

    record Rune(String symbolName) implements OpInputMatcher {
        @Override
        public boolean matches(OpInput input) {
            return input instanceof OpInput.Rune rune && symbolName.equals(rune.symbolName());
        }
    }

    record Op(Class<? extends CompiledArrayNode> nodeType) implements OpInputMatcher {
        @Override
        public boolean matches(OpInput input) {
            return input instanceof OpInput.Op op && nodeType.isInstance(op.node());
        }
    }
}
