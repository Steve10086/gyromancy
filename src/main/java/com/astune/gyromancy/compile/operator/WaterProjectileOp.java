package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.RegisteredOp;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.entity.ball.WaterBallEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

@RegisteredOp
public final class WaterProjectileOp extends ProjectileEntityOp {
    private static final int ELEMENT_EXCHANGE_INTERVAL = 10;
    private static final double VOLUME_LOSS = 0.1;
    private static final double EQUILIBRIUM = 1000.0;
    private static final double MAX_VOLUME_LEVEL = 200.0;
    private static final double MANA_TO_VOLUME = 0.05;
    public static final String STORED_MANA_KEY = "storedMana";

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "water_projectile");
    public static final OpDefinition DEFINITION = new OpDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("water"));
        }

        @Override
        public List<OpInputMatcher> accepted() {
            return List.of(
                    OpInputMatcher.rune("arrow"),
                    OpInputMatcher.rune("engaging"),
                    OpInputMatcher.rune("fix"),
                    OpInputMatcher.op(CompiledOp.class));
        }

        @Override
        public CompileResult<CompiledOp> compile(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                               List<OpInput> inputs) {
            return WaterProjectileOp.create(boundary, matchedInputs, inputs);
        }
    };

    @Override
    public ResourceLocation getId(){return ID;}

    private WaterProjectileOp(PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs) {
        super(ID, ElementType.WATER, boundary, matchedInputs, inputs);
    }

    public static CompileResult<CompiledOp> create(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                                 List<OpInput> inputs) {
        if (!hasMatchedRune(matchedInputs, "water")) {
            return new CompileResult.Failure<>(List.of(
                    new com.astune.gyromancy.array.compile.CompileDiagnostic(
                            "missing_primary_element", "Water operator requires water rune")));
        }
        return rejectUnsupportedInputs(new WaterProjectileOp(boundary, matchedInputs, inputs), inputs);
    }

    @Override
    public WaterBallEntity create(Level level, Vec3 pos, Vec3 velocity, Vec3 acceleration, float size) {
        WaterBallEntity entity = new WaterBallEntity(level, pos, velocity, acceleration, size);
        return entity;
    }

    public List<EntityPayload> defaultPayload() {
        return List.of(
                new WaterBurstOp(),
                new RemoveOnHitOp(),
                new CarryItemsOp(),
                new ElementVolumeOp(ElementType.WATER, STORED_MANA_KEY, ELEMENT_EXCHANGE_INTERVAL,
                        VOLUME_LOSS, EQUILIBRIUM, MAX_VOLUME_LEVEL, MANA_TO_VOLUME)
        );
    }
    public List<EntityPayload> conditionalPayload() {
        Set<EntityPayload> payload = new HashSet<>();
        for (OpInput o : inputs){
            if (o instanceof OpInput.Rune rune){
                switch (rune.symbolName()){
                    case "engaging" -> {
                        payload.add(new ElementConversionOp(ElementType.WATER, ELEMENT_EXCHANGE_INTERVAL));
                    }
                    case "split" -> {}
                    case "fix" -> {
                        payload.add(new BrewingOp());
                    }
                }
            }
        }
        return payload.stream().toList();
    }

    @Override
    protected List<EntityPayload> payloadFor() {
        return payloadFor(null);
    }

    @Override
    protected List<EntityPayload> payloadFor(OpRuntimeContext context) {
        Set<EntityPayload> payload = new HashSet<>(defaultPayload());
        payload.addAll(conditionalPayload());
        return payload(payload.stream().toList(), context);
    }

    @Override
    protected PositionedGlyph primaryRune() {
        for (OpInput input : matchedInputs()) {
            if (input instanceof OpInput.Rune rune && "water".equals(rune.symbolName())) return rune.glyph();
        }
        return null;
    }

    private static boolean hasMatchedRune(List<OpInput> inputs, String symbolName) {
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Rune rune && symbolName.equals(rune.symbolName())) return true;
        }
        return false;
    }
}
