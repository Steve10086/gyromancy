package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.symbol.SymbolCatalog;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ArrayNodeCompiler {
    private ArrayNodeCompiler() {}

    public static CompileResult<CompiledArray> compile(GroupNode ast) {
        return compile(ast, ArrayEffectRegistry.effects());
    }

    public static CompileResult<CompiledArray> compile(GroupNode ast, Collection<ArrayEffectDefinition> effects) {
        CompileResult<CompiledArrayNode> root = compileGroup(ast, effects);
        if (root instanceof CompileResult.Failure<CompiledArrayNode> failure) {
            return new CompileResult.Failure<>(failure.diagnostics());
        }

        CompiledArrayNode node = ((CompileResult.Success<CompiledArrayNode>) root).value();
        return new CompileResult.Success<>(new CompiledArray(
                new ArrayScript(node),
                ast.boundary(),
                ArrayAstBuilder.boundGlyphs(ast),
                color(node)));
    }

    private static CompileResult<CompiledArrayNode> compileGroup(GroupNode group, Collection<ArrayEffectDefinition> effects) {
        if (!(group.body() instanceof SequenceNode sequence)) {
            return fail("missing_primary_element", "Group has no sequence body");
        }

        List<OpInput> inputs = new ArrayList<>();
        List<CompileDiagnostic> diagnostics = new ArrayList<>();

        for (ArrayNode child : sequence.children()) {
            if (child instanceof SymbolNode symbol) {
                inputs.add(new OpInput.Rune(symbol.glyph()));
            } else if (child instanceof GroupNode nested) {
                CompileResult<CompiledArrayNode> compiled = compileGroup(nested, effects);
                if (compiled instanceof CompileResult.Success<CompiledArrayNode> success) {
                    inputs.add(new OpInput.Op(success.value()));
                } else if (compiled instanceof CompileResult.Failure<CompiledArrayNode> failure) {
                    diagnostics.addAll(failure.diagnostics());
                }
            }
        }
        if (!diagnostics.isEmpty()) return new CompileResult.Failure<>(diagnostics);

        Match best = bestMatch(inputs, effects);
        if (best == null) {
            return fail("missing_primary_element", "Local direct symbols did not choose a primary element");
        }
        if (best.ambiguous()) {
            return fail("ambiguous_primary_element", "More than one local primary element was present");
        }
        if (hasUnmatchedCenterRune(inputs, best.inputs())) {
            return fail("ambiguous_primary_element", "A local center rune was not consumed by the selected op");
        }
        return best.effect().compile(group.boundary(), List.copyOf(inputs));
    }

    private static Match bestMatch(List<OpInput> inputs, Collection<ArrayEffectDefinition> effects) {
        Match best = null;
        for (ArrayEffectDefinition effect : effects) {
            List<OpInput> matched = matchedInputs(inputs, effect);
            if (!hasCenterRune(matched)) continue;
            if (matched.isEmpty()) continue;
            if (best == null || matched.size() > best.inputs().size()) {
                best = new Match(effect, matched, false);
            } else if (matched.size() == best.inputs().size()) {
                best = new Match(best.effect(), best.inputs(), true);
            }
        }
        return best;
    }

    private static List<OpInput> matchedInputs(List<OpInput> inputs, ArrayEffectDefinition effect) {
        List<OpInput> matched = new ArrayList<>();
        for (OpInput input : inputs) {
            if (matches(input, effect.match())) matched.add(input);
        }
        return matched;
    }

    private static boolean matches(OpInput input, List<OpInputMatcher> matchers) {
        for (OpInputMatcher matcher : matchers) {
            if (matcher.matches(input)) return true;
        }
        return false;
    }

    private static boolean hasCenterRune(List<OpInput> inputs) {
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Rune rune && rune.glyph().role() == SymbolRole.CENTER_SYMBOL) return true;
        }
        return false;
    }

    private static boolean hasUnmatchedCenterRune(List<OpInput> inputs, List<OpInput> matched) {
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Rune rune
                    && rune.glyph().role() == SymbolRole.CENTER_SYMBOL
                    && !matched.contains(input)) {
                return true;
            }
        }
        return false;
    }

    private static int color(CompiledArrayNode node) {
        if (node instanceof EffectNode effect) {
            return SymbolCatalog.glyphColorFor(ResourceLocation.fromNamespaceAndPath("gyromancy", symbolName(effect.primaryElement())));
        }
        return SymbolCatalog.DEFAULT_GLYPH_COLOR;
    }

    public static String symbolName(ElementType element) {
        return switch (element) {
            case FIRE -> "fire";
            case WATER -> "water";
            case MANA -> "mana";
            case WIND -> "wind";
            case EARTH -> "earth";
            case LIGHT -> "light";
            case DARK -> "dark";
            case SPACE -> "space";
            case TIME -> "time";
        };
    }

    private static <T> CompileResult<T> fail(String code, String message) {
        return new CompileResult.Failure<>(List.of(new CompileDiagnostic(code, message)));
    }

    private record Match(ArrayEffectDefinition effect, List<OpInput> inputs, boolean ambiguous) {}
}
