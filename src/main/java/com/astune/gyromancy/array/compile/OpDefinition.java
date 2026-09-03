package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.compile.operator.CompiledOp;
import com.astune.gyromancy.compile.operator.OpResolveContext;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public interface OpDefinition {
    ResourceLocation id();

    List<OpInputMatcher> match();

    default List<OpInputMatcher> accepted() {
        return List.of();
    }

    CompileResult<CompiledOp> compile(PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs);

    /**
     * Context-aware compile hook for projections which need their parent use
     * site or source array. Existing definitions retain the original compile
     * contract by default.
     */
    default CompileResult<CompiledOp> compile(OpResolveContext context,
                                               PositionedGlyph boundary,
                                               List<OpInput> matchedInputs,
                                               List<OpInput> inputs) {
        return compile(boundary, matchedInputs, inputs);
    }
}
