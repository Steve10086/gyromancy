package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.compile.operator.CompiledOp;
import com.astune.gyromancy.compile.operator.OpResolveContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public final class StaticVectorDefinition {
    static final ResourceLocation DIRECTION_ID = id("vector_direction");
    static final ResourceLocation VELOCITY_ID = id("vector_velocity");
    static final ResourceLocation RAW_GROUP_ID = id("vector_raw_group");

    static final OpDefinition DIRECTION = definition(DIRECTION_ID,
            OpInputMatcher.rune("arrow"), VectorDefinitionSupport.staticAccepted());
    static final OpDefinition VELOCITY = definition(VELOCITY_ID,
            OpInputMatcher.rune("arrow_up"), VectorDefinitionSupport.staticAccepted());
    static final OpDefinition RAW_GROUP = definition(RAW_GROUP_ID,
            OpInputMatcher.rawGroup(), VectorDefinitionSupport.rawGroupAccepted());

    private StaticVectorDefinition() {}

    public static Optional<VectorOp> vectorFor(String runeName, PositionedGlyph boundary,
                                                PositionedGlyph glyph) {
        return switch (runeName) {
            case "arrow" -> Optional.of(StaticVectorOp.direct(
                    DIRECTION_ID, directionTerm(boundary, glyph)));
            case "arrow_up" -> Optional.of(StaticVectorOp.direct(
                    VELOCITY_ID, new StaticVectorOp.Term(
                            StaticVectorOp.TermKind.VELOCITY,
                            new Vec3(glyph.length(), 0.0, 0.0))));
            default -> Optional.empty();
        };
    }

    private static OpDefinition definition(ResourceLocation id, OpInputMatcher requiredInput,
                                           List<OpInputMatcher> accepted) {
        return new OpDefinition() {
            @Override
            public ResourceLocation id() {
                return id;
            }

            @Override
            public List<OpInputMatcher> match() {
                return List.of(requiredInput);
            }

            @Override
            public List<OpInputMatcher> accepted() {
                return accepted;
            }

            @Override
            public CompileResult<CompiledOp> compile(PositionedGlyph boundary,
                                                     List<OpInput> matchedInputs,
                                                     List<OpInput> inputs) {
                return compile(OpResolveContext.forVector(boundary), boundary, matchedInputs, inputs);
            }

            @Override
            public CompileResult<CompiledOp> compile(OpResolveContext context,
                                                     PositionedGlyph boundary,
                                                     List<OpInput> matchedInputs,
                                                     List<OpInput> inputs) {
                OpResolveContext effectiveContext = context == null
                        ? OpResolveContext.forVector(boundary) : context;
                CompileResult<List<VectorComposition.Input>> resolved = VectorInputCompiler.all(
                        boundary, inputs, VectorDefinitionSupport.compiler(), effectiveContext);
                if (resolved instanceof CompileResult.Failure<
                        List<VectorComposition.Input>> failure) {
                    return new CompileResult.Failure<>(failure.diagnostics());
                }
                return new CompileResult.Success<>(new StaticVectorOp(
                        id, boundary, inputs, 0,
                        ((CompileResult.Success<List<VectorComposition.Input>>) resolved).value(),
                        VectorDefinitionSupport.containsRune(inputs, "curl"),
                        VectorOp.secretScale(inputs),
                        VectorDefinitionSupport.containsRune(inputs, "revert")));
            }
        };
    }

    private static StaticVectorOp.Term directionTerm(PositionedGlyph boundary,
                                                      PositionedGlyph glyph) {
        Vec3 world = glyph.front();
        Vec3 unit = world.lengthSqr() < 1.0E-8 ? Vec3.ZERO : world.normalize();
        Vec3 local = boundary == null ? unit.scale(glyph.length()) : new Vec3(
                unit.dot(boundary.surface().axisU()) * glyph.length(),
                unit.dot(boundary.surface().axisV()) * glyph.length(),
                unit.dot(boundary.surface().normal()) * glyph.length());
        return new StaticVectorOp.Term(StaticVectorOp.TermKind.LOCAL_FRAME, local);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, path);
    }
}
