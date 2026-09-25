package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.OpInput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Combines direct direction runes and nested vector groups into one vector. */
public final class StaticVectorOp extends VectorOp {
    private final List<VectorComposition.Input> inputs;
    private final List<Term> terms;
    private final boolean curl;

    StaticVectorOp(ResourceLocation id, PositionedGlyph boundary, List<OpInput> rawInputs,
                   int color, List<VectorComposition.Input> inputs, boolean curl) {
        this(id, boundary, rawInputs, color, List.of(), inputs, curl, 1.0, false);
    }

    StaticVectorOp(ResourceLocation id, PositionedGlyph boundary, List<OpInput> rawInputs,
                   int color, List<VectorComposition.Input> inputs, boolean curl,
                   double scale, boolean reverted) {
        this(id, boundary, rawInputs, color, List.of(), inputs, curl, scale, reverted);
    }

    StaticVectorOp(ResourceLocation id, PositionedGlyph boundary, List<OpInput> rawInputs,
                   int color, List<Term> terms, List<VectorComposition.Input> inputs,
                   boolean curl) {
        this(id, boundary, rawInputs, color, terms, inputs, curl, 1.0, false);
    }

    StaticVectorOp(ResourceLocation id, PositionedGlyph boundary, List<OpInput> rawInputs,
                   int color, List<Term> terms, List<VectorComposition.Input> inputs,
                   boolean curl, double scale, boolean reverted) {
        super(id, boundary, rawInputs, color, scale, reverted);
        this.terms = List.copyOf(terms);
        this.inputs = List.copyOf(inputs);
        this.curl = curl;
    }

    static StaticVectorOp direct(ResourceLocation id, Term term) {
        return new StaticVectorOp(id, null, List.of(), 0, List.of(term), List.of(), false);
    }

    /** Creates a serializable literal vector for runtime payload construction. */
    public static StaticVectorOp literal(Vec3 vector) {
        return direct(RUNTIME_ID, new Term(TermKind.WORLD, vector));
    }

    @Override
    protected Vec3 provideVector(VectorContext context) {
        Vec3 result = terms.isEmpty()
                ? VectorComposition.compose(context, inputs)
                : terms.stream()
                .map(term -> term.resolve(context))
                .reduce(Vec3.ZERO, Vec3::add);
        return curl ? VectorFrameMath.orientByMovement(result, context) : result;
    }

    @Override
    List<VectorOp> composedVectors() {
        return inputs.stream().map(VectorComposition.Input::vector).toList();
    }

    List<VectorComposition.Input> vectorInputs() {
        return inputs;
    }

    List<Term> terms() {
        return terms;
    }

    boolean curl() {
        return curl;
    }

    enum TermKind {
        WORLD,
        LOCAL_FRAME,
        LIVE_FRAME,
        VELOCITY,
        FACING,
        NORMAL
    }

    record Term(TermKind kind, Vec3 value) {
        Term {
            if (kind == null || value == null) {
                throw new IllegalArgumentException("Static vector terms need kind and value");
            }
            value = value;
        }

        Vec3 resolve(VectorContext context) {
            return switch (kind) {
                case WORLD -> value;
                case LOCAL_FRAME -> local(context.activationFrame(), context.compileFrame(), value);
                case LIVE_FRAME -> local(context.liveFrame(), context.activationFrame()
                        .or(() -> context.compileFrame()), value);
                case VELOCITY -> scaled(context.velocity(), value.x);
                case FACING -> scaled(context.facing(), value.x);
                case NORMAL -> scaled(context.arrayNormal(), value.x);
            };
        }

        private static Vec3 local(java.util.Optional<com.astune.gyromancy.api.geometry.SurfaceFrame> first,
                                  java.util.Optional<com.astune.gyromancy.api.geometry.SurfaceFrame> fallback,
                                  Vec3 vector) {
            return VectorOp.localVector(null,
                    first.orElseGet(() -> fallback.orElse(null)), vector);
        }

        private static Vec3 scaled(Vec3 vector, double scale) {
            return vector.lengthSqr() < 1.0E-8 ? Vec3.ZERO : vector.normalize().scale(scale);
        }
    }
}
