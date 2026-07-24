package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.compile.operator.CompiledOp;

public sealed interface OpInputMatcher permits OpInputMatcher.Rune, OpInputMatcher.Op {
    boolean matches(OpInput input);

    static OpInputMatcher rune(String symbolName) {
        return new Rune(symbolName);
    }

    static OpInputMatcher op(Class<? extends CompiledOp> operatorType) {
        return new Op(operatorType);
    }

    record Rune(String symbolName) implements OpInputMatcher {
        @Override
        public boolean matches(OpInput input) {
            return input instanceof OpInput.Rune rune && symbolName.equals(rune.symbolName());
        }
    }

    record Op(Class<? extends CompiledOp> operatorType) implements OpInputMatcher {
        @Override
        public boolean matches(OpInput input) {
            return input instanceof OpInput.Op op && operatorType.isInstance(op.operator());
        }
    }
}
