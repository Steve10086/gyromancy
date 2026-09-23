package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.compile.operator.CompiledOp;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.symbol.SecretTextSymbol;

public sealed interface OpInputMatcher permits OpInputMatcher.Rune, OpInputMatcher.Op,
        OpInputMatcher.BoundaryRole, OpInputMatcher.RawGroupRole, OpInputMatcher.RawGroup,
        OpInputMatcher.SecretTextRune {
    boolean matches(OpInput input);

    /**
     * A deferred child still carries a dynamically preserved structure and is
     * therefore accepted by any matcher until the structure pass resolves it.
     */
    static boolean isDeferred(OpInput input) {
        return input instanceof OpInput.Op op && op.deferred();
    }

    static OpInputMatcher rune(String symbolName) {
        return new Rune(symbolName);
    }

    static OpInputMatcher op(Class<? extends CompiledOp> operatorType) {
        return new Op(operatorType);
    }

    static OpInputMatcher boundary(SymbolRole role) {
        return new BoundaryRole(role);
    }

    static OpInputMatcher rawGroup(SymbolRole role) {
        return new RawGroupRole(role);
    }

    /** Matches a passive, uncompiled nested group with any boundary role. */
    static OpInputMatcher rawGroup() {
        return new RawGroup();
    }

    /** Matches any one of the distinct secret-text parameter runes. */
    static OpInputMatcher secretText() {
        return new SecretTextRune();
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

    /** Matches a nested compiled group by the role of its enclosing glyph. */
    record BoundaryRole(SymbolRole role) implements OpInputMatcher {
        @Override
        public boolean matches(OpInput input) {
            return input instanceof OpInput.Op op
                    && op.operator().boundary() != null
                    && op.operator().boundary().role() == role;
        }

    }

    /** Matches only a failed, passive nested group by its boundary role. */
    record RawGroupRole(SymbolRole role) implements OpInputMatcher {
        @Override
        public boolean matches(OpInput input) {
            return input instanceof OpInput.RawGroup raw
                    && raw.boundary() != null
                    && raw.boundary().role() == role;
        }

    }

    /** Static matcher for any failed, passive nested group. */
    record RawGroup() implements OpInputMatcher {
        @Override
        public boolean matches(OpInput input) {
            return input instanceof OpInput.RawGroup;
        }

    }

    /** Static matcher for the whole secret-text rune family. */
    record SecretTextRune() implements OpInputMatcher {
        @Override
        public boolean matches(OpInput input) {
            return input instanceof OpInput.Rune rune
                    && SecretTextSymbol.fromId(rune.glyph().symbolId()) != null;
        }

    }
}
