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
import com.astune.gyromancy.entity.ball.FireballEntity;
import com.astune.gyromancy.entity.ball.MagicBallEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

@RegisteredOp
public final class FireProjectileOp extends ProjectileEntityOp {
    private static final int ELEMENT_EXCHANGE_INTERVAL = 10;
    private static final double VOLUME_LOSS = 0.1;
    private static final double EQUILIBRIUM = 1000.0;
    private static final double MAX_VOLUME_LEVEL = 200.0;
    private static final double MANA_TO_VOLUME = 0.05;
    public static final String STORED_MANA_KEY = "storedMana";

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "fireball");
    public static final OpDefinition DEFINITION = new OpDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("fire"));
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
            return FireProjectileOp.create(boundary, matchedInputs, inputs);
        }
    };

    public static final String OLD_SPAWNED_KEY = "oldSpawned";

    private FireProjectileOp(PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs) {
        super(ID, ElementType.FIRE, boundary, matchedInputs, inputs);
    }

    public static CompileResult<CompiledOp> create(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                                 List<OpInput> inputs) {
        return rejectUnsupportedInputs(new FireProjectileOp(boundary, matchedInputs, inputs), inputs);
    }

    @Override
    public FireProjectileOp copyWithInputs(List<OpInput> inputs) {
        return new FireProjectileOp(boundary(), matchedInputs(), inputs);
    }

    @Override
    public FireballEntity create(Level level, Vec3 pos, Vec3 velocity, Vec3 acceleration, float size) {
        return new FireballEntity(level, pos, velocity, acceleration, size);
    }

    public List<EntityPayload> defaultPayload() {
        return List.of(
                new ElementVolumeOp(ElementType.FIRE, STORED_MANA_KEY, ELEMENT_EXCHANGE_INTERVAL,
                        VOLUME_LOSS, EQUILIBRIUM, MAX_VOLUME_LEVEL, MANA_TO_VOLUME),
                new FireCrystalOp());
    }

    public List<EntityPayload> conditionalPayload() {
        Set<EntityPayload> payload = new LinkedHashSet<>();
        for (OpInput o : inputs){
            if (o instanceof OpInput.Rune rune){
                switch (rune.symbolName()){
                    case "engaging" -> {
                        payload.add(new ElementConversionOp(ElementType.FIRE, ELEMENT_EXCHANGE_INTERVAL));
                    }
                    case "fix" -> {
                        payload.add(new SmeltOp());
                    }
                }
            }
        }
        if (payload.isEmpty()) {
            payload.add(new ExplosionOp());
            //payload.add(new RemoveOnHitOp());
        }
        return payload.stream().toList();
    }

    @Override
    protected List<EntityPayload> payloadFor() {
        return payloadFor(null);
    }

    @Override
    protected List<EntityPayload> payloadFor(OpRuntimeContext context) {
        Set<EntityPayload> payload = new LinkedHashSet<>(defaultPayload());
        payload.addAll(conditionalPayload());
        return payload(payload.stream().toList(), context);
    }
    @Override
    public ResourceLocation getId(){return ID;}
    protected PositionedGlyph primaryRune() {
        for (var input : matchedInputs()) {
            if (input instanceof OpInput.Rune rune && "fire".equals(rune.symbolName())) return rune.glyph();
        }
        return null;
    }

}
