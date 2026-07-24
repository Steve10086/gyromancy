package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.ParameterRune;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.ArrayEffectDefinition;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.EffectAttributes;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.runtime.RuntimeHandle;
import com.astune.gyromancy.symbol.SymbolCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ManaProjectileOp extends EntityEffectOp {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "mana_projectile");
    public static final ArrayEffectDefinition DEFINITION = new ArrayEffectDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("mana"));
        }

        @Override
        public CompileResult<Operator> compile(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                               List<OpInput> inputs) {
            return ManaProjectileOp.create(boundary, matchedInputs, inputs);
        }
    };

    private ManaProjectileOp(PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs,
                             EffectAttributes attributes) {
        super(ID, ElementType.MANA, boundary, matchedInputs, inputs, attributes);
    }

    public static CompileResult<Operator> create(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                                 List<OpInput> inputs) {
        CompileResult<EffectAttributes> attributes = compileAttributes(inputs, ElementType.MANA);
        if (attributes instanceof CompileResult.Failure<EffectAttributes> failure) {
            return new CompileResult.Failure<>(failure.diagnostics());
        }
        EffectAttributes attrs = ((CompileResult.Success<EffectAttributes>) attributes).value();
        return new CompileResult.Success<>(new ManaProjectileOp(boundary, matchedInputs, inputs, attrs));
    }

    @Override
    public RuntimeHandle activate(ServerLevel level) {
        PositionedGlyph center = primaryRune();
        if (center == null) return new RuntimeHandle(Map.of());
        Map<String, Object> scratch = SymbolCatalog.getCenterEffect(center.symbolId())
                .execute(level, boundary().worldPos(), boundary(), center, legacyRunes(center));
        return new RuntimeHandle(scratch == null ? Map.of() : Map.copyOf(scratch));
    }

    @Override
    public void deactivate(ServerLevel level, Map<String, Object> scratchData) {
        PositionedGlyph center = primaryRune();
        if (center == null) return;
        SymbolCatalog.getEndEffect(center.symbolId())
                .execute(level, boundary().worldPos(), toRuneParams(inputs()), scratchData);
    }

    private PositionedGlyph primaryRune() {
        for (OpInput input : matchedInputs()) {
            if (input instanceof OpInput.Rune rune && "mana".equals(rune.symbolName())) return rune.glyph();
        }
        return null;
    }

    private List<PositionedGlyph> legacyRunes(PositionedGlyph center) {
        List<PositionedGlyph> runes = new ArrayList<>();
        for (OpInput input : inputs()) {
            if (!(input instanceof OpInput.Rune rune)) continue;
            if (!rune.glyph().glyphUuid().equals(center.glyphUuid())) runes.add(rune.glyph());
        }
        return List.copyOf(runes);
    }

    private static List<ParameterRune> toRuneParams(List<OpInput> inputs) {
        List<ParameterRune> params = new ArrayList<>();
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Rune rune) {
                params.add(new ParameterRune(rune.glyph().symbolId(), rune.glyph().confidence(), ""));
            }
        }
        return List.copyOf(params);
    }

    private static boolean hasMatchedRune(List<OpInput> inputs, String symbolName) {
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Rune rune && symbolName.equals(rune.symbolName())) return true;
        }
        return false;
    }
}
