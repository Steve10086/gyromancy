package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.compile.operator.CompiledOp;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public final class StaticVectorDefinition {
    static final ResourceLocation DIRECTION_ID = id("vector_direction");
    static final ResourceLocation VELOCITY_ID = id("vector_velocity");

    static final OpDefinition DIRECTION = definition(DIRECTION_ID, "arrow");
    static final OpDefinition VELOCITY = definition(VELOCITY_ID, "arrow_up");

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

    private static OpDefinition definition(ResourceLocation id, String runeName) {
        return new OpDefinition() {
            @Override
            public ResourceLocation id() {
                return id;
            }

            @Override
            public List<OpInputMatcher> match() {
                return List.of(OpInputMatcher.rune(runeName));
            }

            @Override
            public List<OpInputMatcher> accepted() {
                return VectorDefinitionSupport.accepted(true);
            }

            @Override
            public CompileResult<CompiledOp> compile(PositionedGlyph boundary,
                                                     List<OpInput> matchedInputs,
                                                     List<OpInput> inputs) {
                return new CompileResult.Success<>(new StaticVectorOp(
                        id, boundary, inputs, 0,
                        VectorInputCompiler.all(boundary, inputs, VectorDefinitionSupport.compiler()),
                        VectorDefinitionSupport.containsRune(inputs, "curl")));
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
