package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
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

        List<PositionedGlyph> symbols = new ArrayList<>();
        List<CompiledArrayNode> children = new ArrayList<>();
        List<CompileDiagnostic> diagnostics = new ArrayList<>();

        for (ArrayNode child : sequence.children()) {
            if (child instanceof SymbolNode symbol) {
                symbols.add(symbol.glyph());
            } else if (child instanceof GroupNode nested) {
                CompileResult<CompiledArrayNode> compiled = compileGroup(nested, effects);
                if (compiled instanceof CompileResult.Success<CompiledArrayNode> success) {
                    children.add(success.value());
                } else if (compiled instanceof CompileResult.Failure<CompiledArrayNode> failure) {
                    diagnostics.addAll(failure.diagnostics());
                }
            }
        }
        if (!diagnostics.isEmpty()) return new CompileResult.Failure<>(diagnostics);

        List<PositionedGlyph> primaryGlyphs = symbols.stream()
                .filter(glyph -> effectFor(glyph.symbolId(), effects) != null)
                .toList();
        if (primaryGlyphs.isEmpty()) {
            return fail("missing_primary_element", "Local direct symbols did not choose a primary element");
        }
        if (primaryGlyphs.size() > 1) {
            return fail("ambiguous_primary_element", "More than one local primary element was present");
        }

        PositionedGlyph primary = primaryGlyphs.getFirst();
        ArrayEffectDefinition effect = effectFor(primary.symbolId(), effects);
        ElementType element = effect.primaryElement(primary.symbolId().getPath());
        EffectAttributes attributes = attributesFor(symbols, primary);
        if (attributes == null) {
            return fail("unknown_symbol_semantics", "A local symbol has no compile rule");
        }
        if (attributes == EffectAttributes.INVALID_INVERSE) {
            return fail("invalid_element_inverse", "Only one revert rune is supported");
        }
        if (element == ElementType.MANA && attributes.inverted()) {
            return fail("invalid_element_inverse", "Mana has no inverse element");
        }
        if (!children.isEmpty()) {
            return fail("unsupported_child_node", "Projectile nodes do not accept child nodes yet");
        }

        return new CompileResult.Success<>(effect.compile(group.boundary(), element, attributes, List.copyOf(children)));
    }

    private static EffectAttributes attributesFor(List<PositionedGlyph> symbols, PositionedGlyph primary) {
        boolean inverted = false;
        List<MotionAttribute> motion = new ArrayList<>();
        for (PositionedGlyph glyph : symbols) {
            if (glyph.glyphUuid().equals(primary.glyphUuid())) continue;
            switch (glyph.symbolId().getPath()) {
                case "arrow" -> motion.add(new MotionAttribute(glyph.front(), glyph.length()));
                case "revert" -> {
                    if (inverted) return EffectAttributes.INVALID_INVERSE;
                    inverted = true;
                }
                case "figure_8" -> {}
                default -> {
                    return null;
                }
            }
        }
        return new EffectAttributes(inverted, List.copyOf(motion));
    }

    private static ArrayEffectDefinition effectFor(ResourceLocation symbolId, Collection<ArrayEffectDefinition> effects) {
        String name = symbolId.getPath();
        for (ArrayEffectDefinition effect : effects) {
            if (effect.symbols().contains(name)) return effect;
        }
        return null;
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
}
