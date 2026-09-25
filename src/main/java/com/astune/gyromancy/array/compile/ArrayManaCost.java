package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.element.ManaElements;
import com.astune.gyromancy.compile.operator.CompiledOp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Collects the recursive element cost of a compiled Op tree and verifies it. */
public final class ArrayManaCost {
    private ArrayManaCost() {}

    /** Sums {@link CompiledOp#getCost()} over the final compiled structure. */
    public static ManaElements collect(CompiledOp root) {
        if (root == null) return ManaElements.EMPTY;
        ManaElements total = ManaElements.EMPTY;
        Set<CompiledOp> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<CompiledOp> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            CompiledOp op = pending.pop();
            if (!seen.add(op)) continue;
            total = total.plus(op.getCost());
            for (OpInput input : op.inputs()) {
                if (input instanceof OpInput.Op child && child.operator() != null) {
                    pending.push(child.operator());
                }
            }
        }
        return total;
    }

    /**
     * Returns one diagnostic per element slot whose requirement exceeds the
     * array's solved amount. The message names the element, its minimum
     * requirement and the current value.
     */
    public static List<CompileDiagnostic> check(CompiledOp root, ManaElements available) {
        ManaElements required = collect(root);
        List<CompileDiagnostic> diagnostics = new ArrayList<>();
        for (ElementType type : ElementType.values()) {
            double need = required.at(type);
            if (need <= 0.0) continue;
            double current = available.at(type);
            if (current < need) {
                diagnostics.add(new CompileDiagnostic("insufficient_element",
                        type.name().toLowerCase(Locale.ROOT)
                                + " requires " + format(need)
                                + ", current " + format(current)));
            }
        }
        return List.copyOf(diagnostics);
    }

    private static String format(double value) {
        return value == Math.rint(value)
                ? Long.toString((long) value)
                : Double.toString(value);
    }
}
