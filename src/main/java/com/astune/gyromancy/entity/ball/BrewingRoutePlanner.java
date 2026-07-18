package com.astune.gyromancy.entity.ball;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

final class BrewingRoutePlanner {
    private BrewingRoutePlanner() {}

    static <S, I> OptionalInt findFirstIngredient(S initialState, List<I> ingredients, int maxDepth,
                                                   BiFunction<I, S, S> mixer,
                                                   Predicate<S> isValid,
                                                   BiPredicate<S, S> isSame,
                                                   Predicate<S> isGoal) {
        Queue<Node<S>> queue = new ArrayDeque<>();
        queue.add(new Node<>(initialState, Set.of(), -1, 0));
        while (!queue.isEmpty()) {
            Node<S> node = queue.remove();
            if (node.depth() >= maxDepth) continue;
            for (int i = 0; i < ingredients.size(); i++) {
                if (node.used().contains(i)) continue;
                S output = mixer.apply(ingredients.get(i), node.state());
                if (!isValid.test(output) || isSame.test(output, node.state())) continue;
                int first = node.firstIngredient() < 0 ? i : node.firstIngredient();
                if (isGoal.test(output)) return OptionalInt.of(first);
                Set<Integer> used = new HashSet<>(node.used());
                used.add(i);
                queue.add(new Node<>(output, Set.copyOf(used), first, node.depth() + 1));
            }
        }
        return OptionalInt.empty();
    }

    private record Node<S>(S state, Set<Integer> used, int firstIngredient, int depth) {}
}
