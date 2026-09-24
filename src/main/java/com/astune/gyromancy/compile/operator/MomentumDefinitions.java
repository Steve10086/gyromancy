package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.RegisteredOp;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Global definitions and compile-time input resolution for MomentumOp. */
@RegisteredOp(definitions = {"MOTION_DEFINITION"})
public final class MomentumDefinitions {
    public static final ResourceLocation MOTION_DEFINITION_ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "momentum_motion");

    private MomentumDefinitions() {}

    public static final OpDefinition MOTION_DEFINITION =
            definition(MOTION_DEFINITION_ID, OpInputMatcher.rune("motion"));

    private static OpDefinition definition(ResourceLocation definitionId,
                                           OpInputMatcher requiredInput) {
        return new OpDefinition() {
            @Override
            public ResourceLocation id() {
                return definitionId;
            }

            @Override
            public List<OpInputMatcher> match() {
                return List.of(requiredInput);
            }

            @Override
            public List<OpInputMatcher> accepted() {
                return List.of(
                        OpInputMatcher.rune("arrow"),
                        OpInputMatcher.rune("arrow_up"),
                        OpInputMatcher.rune("loop"),
                        OpInputMatcher.rune("drain"),
                        OpInputMatcher.op(MomentumOp.class),
                        OpInputMatcher.boundary(SymbolRole.OUTER_CIRCLE),
                        OpInputMatcher.rawGroup(SymbolRole.OUTER_CIRCLE));
            }

            @Override
            public CompileResult<CompiledOp> compile(
                    PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs) {
                return new CompileResult.Success<>(
                        MomentumOp.symbolic(boundary, matchedInputs, inputs));
            }
        };
    }
}
