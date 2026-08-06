package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.runtime.emit.EntityEmitter;
import com.astune.gyromancy.array.runtime.emit.EmitResult;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.RegisteredOp;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.array.runtime.RuntimeHandle;
import com.astune.gyromancy.entity.ball.FireballEntity;
import com.astune.gyromancy.entity.ball.MagicBallEntity;
import com.astune.gyromancy.symbol.CenterSymbol;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

@RegisteredOp
public final class FireProjectileOp extends ProjectileEntityOp {
    private static final int ELEMENT_EXCHANGE_INTERVAL = 10;
    private static final double FIRE_VOLUME_LOSS = 0.1;
    private static final double FIRE_EQUILIBRIUM = 100.0;
    private static final double MAX_VOLUME_FIRE_LEVEL = 2000.0;
    private static final double FIRE_PER_VOLUME = 1000.0;
    private static final double FIRE_CONVERSION_COST = 100.0;
    private static final double MANA_TO_VOLUME = 0.01;

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
                    OpInputMatcher.rune("revert"),
                    OpInputMatcher.op(CompiledOp.class));
        }

        @Override
        public CompileResult<CompiledOp> compile(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                               List<OpInput> inputs) {
            return FireProjectileOp.create(boundary, matchedInputs, inputs);
        }
    };

    public static final String STORED_MANA_KEY = "storedMana";
    public static final String OLD_SPAWNED_KEY = "oldSpawned";
    public static final String LIFETIME_KEY = "lifetime";

    private FireProjectileOp(PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs) {
        super(ID, ElementType.FIRE, boundary, matchedInputs, inputs);
    }

    public static CompileResult<CompiledOp> create(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                                 List<OpInput> inputs) {
        boolean reverted = false;
        for (OpInput input : inputs) {
            if (!(input instanceof OpInput.Rune rune) || !"revert".equals(rune.symbolName())) continue;
            if (reverted) {
                return new CompileResult.Failure<>(List.of(
                        new com.astune.gyromancy.array.compile.CompileDiagnostic(
                                "invalid_element_inverse", "Only one revert rune is supported")));
            }
            reverted = true;
        }
        return new CompileResult.Success<>(new FireProjectileOp(boundary, matchedInputs, inputs));
    }

    @Override
    public FireballEntity create(Level level, Vec3 pos, Vec3 velocity, Vec3 acceleration, float size) {
        FireballEntity entity = new FireballEntity(level, pos, velocity, acceleration, size);
        entity.setPayload(defaultPayload());
        return entity;
    }

    public static List<EntityPayload> defaultPayload() {
        return List.of(
                new ExplosionOp(),
                new ElementVolumeOp(ElementType.FIRE, STORED_MANA_KEY, ELEMENT_EXCHANGE_INTERVAL,
                        FIRE_VOLUME_LOSS, FIRE_EQUILIBRIUM, MAX_VOLUME_FIRE_LEVEL,
                        FIRE_PER_VOLUME, FIRE_CONVERSION_COST, MANA_TO_VOLUME),
                new ElementConversionOp(ElementType.FIRE, ELEMENT_EXCHANGE_INTERVAL));
    }

    @Override
    public void deactivate(OpRuntimeContext ctx, Map<String, Object> scratchData) {
        ServerLevel level = ctx.level();
        CenterSymbol.boundEntity(level, scratchData, CenterSymbol.FIREBALL_KEY)
                .ifPresent(entity -> entity.discard());
    }

    @Override
    protected List<EntityPayload> payloadFor() {
        return payload(defaultPayload());
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
