package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.compile.operator.CompiledOp;
import com.astune.gyromancy.compile.operator.OpResolvable;
import com.astune.gyromancy.compile.operator.OpResolution;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.symbol.SecretTextSymbol;

public sealed interface OpInputMatcher permits OpInputMatcher.Rune, OpInputMatcher.Op,
        OpInputMatcher.BoundaryRole, OpInputMatcher.RawGroupRole, OpInputMatcher.RawGroup,
        OpInputMatcher.SecretTextRune {
    boolean matches(OpInput input);

    /**
     * Matches the effective child exposed when a parent consumes a dynamic
     * input.  This deliberately does not decide whether that child is valid;
     * the consuming parent owns that decision.
     */
    boolean matches(OpResolution resolution);

    static boolean anyMatches(Iterable<? extends OpInputMatcher> matchers,
                              OpResolution resolution) {
        for (OpInputMatcher matcher : matchers) {
            if (matcher.matches(resolution)) return true;
        }
        return false;
    }

    private static boolean isDeferred(OpInput input) {
        return input instanceof OpInput.Op op && op.operator() instanceof OpResolvable;
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
            return isDeferred(input)
                    || input instanceof OpInput.Rune rune && symbolName.equals(rune.symbolName());
        }

        @Override
        public boolean matches(OpResolution resolution) {
            return false;
        }
    }

    record Op(Class<? extends CompiledOp> operatorType) implements OpInputMatcher {
        @Override
        public boolean matches(OpInput input) {
            return isDeferred(input)
                    || input instanceof OpInput.Op op && operatorType.isInstance(op.operator());
        }

        @Override
        public boolean matches(OpResolution resolution) {
            return resolution != null && operatorType.isInstance(resolution.operator());
        }
    }

    /** Matches a nested compiled group by the role of its enclosing glyph. */
    record BoundaryRole(SymbolRole role) implements OpInputMatcher {
        @Override
        public boolean matches(OpInput input) {
            return isDeferred(input)
                    || input instanceof OpInput.Op op
                    && op.operator().boundary() != null
                    && op.operator().boundary().role() == role;
        }

        @Override
        public boolean matches(OpResolution resolution) {
            if (resolution == null) return false;
            if (resolution.sourceGroup() != null
                    && resolution.sourceGroup().boundary() != null
                    && resolution.sourceGroup().boundary().role() == role) return true;
            return resolution.operator().boundary() != null
                    && resolution.operator().boundary().role() == role;
        }
    }

    /** Matches only a failed, passive nested group by its boundary role. */
    record RawGroupRole(SymbolRole role) implements OpInputMatcher {
        @Override
        public boolean matches(OpInput input) {
            return isDeferred(input)
                    || input instanceof OpInput.RawGroup raw
                    && raw.boundary() != null
                    && raw.boundary().role() == role;
        }

        @Override
        public boolean matches(OpResolution resolution) {
            return resolution != null
                    && resolution.sourceGroup() != null
                    && resolution.sourceGroup().boundary() != null
                    && resolution.sourceGroup().boundary().role() == role;
        }
    }

    /** Static matcher for any failed, passive nested group. */
    record RawGroup() implements OpInputMatcher {
        @Override
        public boolean matches(OpInput input) {
            return isDeferred(input) || input instanceof OpInput.RawGroup;
        }

        @Override
        public boolean matches(OpResolution resolution) {
            return resolution != null && resolution.sourceGroup() != null;
        }
    }

    /** Static matcher for the whole secret-text rune family. */
    record SecretTextRune() implements OpInputMatcher {
        @Override
        public boolean matches(OpInput input) {
            return isDeferred(input)
                    || input instanceof OpInput.Rune rune
                    && SecretTextSymbol.fromId(rune.glyph().symbolId()) != null;
        }

        @Override
        public boolean matches(OpResolution resolution) {
            return false;
        }
    }
}
