package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.symbol.CenterSymbol;
import com.astune.gyromancy.symbol.SymbolCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        Vec3 arrayNormal = CenterSymbol.faceNormal(boundary());
        List<EmissionSource> sources = new ArrayList<>();
        for (OpInput input : inputs()) {
            if (input instanceof OpInput.Rune rune) {
                decodeEmissionSource(rune, arrayNormal).ifPresent(sources::add);
            }
        }
        if (sources.isEmpty()) return List.of();

        float sizeScale = 1.0F / sources.size();
        List<Emission> emissions = new ArrayList<>(sources.size());
        for (EmissionSource source : sources) {
            Vec3 velocity = source.direction().lengthSqr() >= 1.0E-8
                    ? source.direction().normalize().scale(source.speed())
                    : Vec3.ZERO;
            emissions.add(new Emission(velocity, source.speed(), sizeScale, true));
        }
        return List.copyOf(emissions);
    }

    private static Optional<EmissionSource> decodeEmissionSource(OpInput.Rune rune, Vec3 arrayNormal) {
        return switch (rune.symbolName()) {
            case "arrow" -> Optional.of(new EmissionSource(rune.glyph().front(), rune.glyph().length()));
            case "arrow_up" -> Optional.of(new EmissionSource(arrayNormal, rune.glyph().length()));
            default -> Optional.empty();
        };
    }

    @Override
    public int color() {
        return SymbolCatalog.glyphColorFor(SPLIT_SYMBOL);
    }

    private record EmissionSource(Vec3 direction, double speed) {}
}
