package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.RegisteredOp;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.symbol.CenterSymbol;
import com.astune.gyromancy.symbol.SymbolCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RegisteredOp
public final class SplitEmitOp extends EmitOp {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "split_emit");
    private static final ResourceLocation SPLIT_SYMBOL =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "split");
    public static final OpDefinition DEFINITION = new OpDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("split"));
        }

        @Override
        public List<OpInputMatcher> accepted() {
            return List.of(
                    OpInputMatcher.rune("arrow"),
                    OpInputMatcher.rune("arrow_up"),
                    OpInputMatcher.op(MomentumOp.class));
        }

        @Override
        public CompileResult<CompiledOp> compile(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                                 List<OpInput> inputs) {
            return new CompileResult.Success<>(new SplitEmitOp(boundary, matchedInputs, inputs));
        }
    };

    private SplitEmitOp(PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs) {
        super(ID, boundary, matchedInputs, inputs);
    }

    @Override
    public List<Emission> emissions() {
        return emissions(null);
    }

    @Override
    public List<Emission> emissions(OpRuntimeContext context) {
        Vec3 arrayNormal = context == null
                ? CenterSymbol.faceNormal(boundary()) : context.normalFor(boundary());
        List<Emission> emissions = new ArrayList<>();
        for (OpInput input : inputs()) {
            if (input instanceof OpInput.Rune rune) {
                PositionedGlyph glyph = context == null ? rune.glyph() : context.liveGlyph(rune.glyph());
                decodeEmissionSource(rune.symbolName(), glyph, arrayNormal)
                        .map(SplitEmitOp::emission)
                        .ifPresent(emissions::add);
            } else if (input instanceof OpInput.Op op && op.operator() instanceof MomentumOp momentum) {
                emissions.add(momentum.modifyEntityEmission(
                        new Emission(Vec3.ZERO, 0.0, 1.0F, false)));
            }
        }
        if (emissions.isEmpty()) return List.of();

        float sizeScale = 1.0F / emissions.size();
        return emissions.stream()
                .map(emission -> new Emission(
                        emission.velocity(),
                        emission.motionSum(),
                        sizeScale,
                        emission.hasMotion()))
                .toList();
    }

    private static Optional<EmissionSource> decodeEmissionSource(
            String symbolName, PositionedGlyph glyph, Vec3 arrayNormal) {
        return switch (symbolName) {
            case "arrow" -> Optional.of(new EmissionSource(glyph.front(), glyph.length()));
            case "arrow_up" -> Optional.of(new EmissionSource(arrayNormal, glyph.length()));
            default -> Optional.empty();
        };
    }

    private static Emission emission(EmissionSource source) {
        Vec3 velocity = source.direction().lengthSqr() >= 1.0E-8
                ? source.direction().normalize().scale(source.speed())
                : Vec3.ZERO;
        return new Emission(velocity, source.speed(), 1.0F, true);
    }

    @Override
    public int color() {
        return SymbolCatalog.glyphColorFor(SPLIT_SYMBOL);
    }

    private record EmissionSource(Vec3 direction, double speed) {}
}
