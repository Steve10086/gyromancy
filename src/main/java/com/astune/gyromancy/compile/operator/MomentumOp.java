package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.RegisteredOp;
import com.astune.gyromancy.symbol.SymbolCatalog;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

@RegisteredOp(definitions = {"ARROW_DEFINITION", "ARROW_UP_DEFINITION"})
public final class MomentumOp extends OnEntityTickOp implements CompiledOp {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "momentum");
    public static final ResourceLocation ARROW_DEFINITION_ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "momentum_arrow");
    public static final ResourceLocation ARROW_UP_DEFINITION_ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "momentum_arrow_up");

    static final int ACTIVE_TICKS = 100;
    private static final double ACCELERATION_SCALE = 1.0/10;
    private static final double DIRECTION_EPSILON = 1.0E-8;

    private static final Codec<Vec3> VEC3_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("x").forGetter(vector -> vector.x),
            Codec.DOUBLE.fieldOf("y").forGetter(vector -> vector.y),
            Codec.DOUBLE.fieldOf("z").forGetter(vector -> vector.z)
    ).apply(instance, Vec3::new));

    private static final Codec<AccelerationInput> INPUT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            VEC3_CODEC.optionalFieldOf("direction", Vec3.ZERO).forGetter(AccelerationInput::direction),
            Codec.DOUBLE.fieldOf("magnitude").forGetter(AccelerationInput::magnitude),
            Codec.BOOL.optionalFieldOf("along_facing", false).forGetter(AccelerationInput::alongFacing)
    ).apply(instance, AccelerationInput::new));

    public static final Codec<MomentumOp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            INPUT_CODEC.listOf().fieldOf("inputs").forGetter(MomentumOp::accelerationInputs),
            VEC3_CODEC.optionalFieldOf("acceleration", Vec3.ZERO).forGetter(op -> op.acceleration),
            Codec.INT.optionalFieldOf("elapsed_ticks", 0).forGetter(op -> op.elapsedTicks)
    ).apply(instance, MomentumOp::new));

    public static final OpDefinition ARROW_DEFINITION = definition(ARROW_DEFINITION_ID, "arrow");
    public static final OpDefinition ARROW_UP_DEFINITION = definition(ARROW_UP_DEFINITION_ID, "arrow_up");

    private final PositionedGlyph boundary;
    private final List<OpInput> matchedInputs;
    private final List<OpInput> inputs;
    private final List<AccelerationInput> accelerationInputs;
    private Vec3 acceleration;
    private int elapsedTicks;

    private MomentumOp(PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs) {
        this(boundary, matchedInputs, inputs, accelerationInputs(inputs), Vec3.ZERO, 0);
    }

    private MomentumOp(List<AccelerationInput> accelerationInputs, Vec3 acceleration, int elapsedTicks) {
        this(null, List.of(), List.of(), accelerationInputs, acceleration, elapsedTicks);
    }

    MomentumOp(List<AccelerationInput> accelerationInputs) {
        this(accelerationInputs, Vec3.ZERO, 0);
    }

    private MomentumOp(PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs,
                       List<AccelerationInput> accelerationInputs, Vec3 acceleration,
                       int elapsedTicks) {
        this.boundary = boundary;
        this.matchedInputs = List.copyOf(matchedInputs);
        this.inputs = List.copyOf(inputs);
        this.accelerationInputs = List.copyOf(accelerationInputs);
        this.acceleration = acceleration;
        this.elapsedTicks = Math.max(0, Math.min(ACTIVE_TICKS, elapsedTicks));
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public PositionedGlyph boundary() {
        return boundary;
    }

    @Override
    public List<OpInput> inputs() {
        return inputs;
    }

    public List<OpInput> matchedInputs() {
        return matchedInputs;
    }

    public List<AccelerationInput> accelerationInputs() {
        return accelerationInputs;
    }

    @Override
    public int color() {
        ResourceLocation symbol = matchedInputs.stream()
                .filter(OpInput.Rune.class::isInstance)
                .map(OpInput.Rune.class::cast)
                .map(rune -> rune.glyph().symbolId())
                .findFirst()
                .orElse(ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "arrow"));
        return SymbolCatalog.glyphColorFor(symbol);
    }

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<MomentumOp> codec() {
        return CODEC;
    }

    @Override
    public void contributeEntityPayloads(List<EntityPayload> payloads) {
        payloads.add(new MomentumOp(accelerationInputs, Vec3.ZERO, 0));
    }

    @Override
    public void onEntityTick(EntityTickContext ctx) {
        if (ctx.isClientSide()) return;
        ctx.owner().addDeltaMovement(accelerationForTick(ctx.velocity()));
    }

    Vec3 accelerationForTick(Vec3 facingDirection) {
        if (elapsedTicks >= ACTIVE_TICKS) return Vec3.ZERO;
        acceleration = solveAcceleration(facingDirection);
        Vec3 result = acceleration;
        elapsedTicks++;
        if (elapsedTicks >= ACTIVE_TICKS) acceleration = Vec3.ZERO;
        return result;
    }

    Vec3 solveAcceleration(Vec3 facingDirection) {
        if (facingDirection.lengthSqr() < DIRECTION_EPSILON) return Vec3.ZERO;

        Vec3 normal = facingDirection.normalize();
        Vec3 result = Vec3.ZERO;
        for (AccelerationInput input : accelerationInputs) {
            Vec3 direction;
            if (input.alongFacing()) {
                direction = normal;
            } else {
                direction = input.direction().subtract(normal.scale(input.direction().dot(normal)));
                if (direction.lengthSqr() < DIRECTION_EPSILON) continue;
                direction = direction.normalize();
            }
            result = result.add(direction.scale(input.magnitude()));
        }
        return result;
    }

    int elapsedTicks() {
        return elapsedTicks;
    }

    Vec3 currentAcceleration() {
        return acceleration;
    }

    private static OpDefinition definition(ResourceLocation definitionId, String runeName) {
        return new OpDefinition() {
            @Override
            public ResourceLocation id() {
                return definitionId;
            }

            @Override
            public List<OpInputMatcher> match() {
                return List.of(OpInputMatcher.rune(runeName));
            }

            @Override
            public List<OpInputMatcher> accepted() {
                return List.of(
                        OpInputMatcher.rune("arrow"),
                        OpInputMatcher.rune("arrow_up"));
            }

            @Override
            public CompileResult<CompiledOp> compile(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                                     List<OpInput> inputs) {
                return new CompileResult.Success<>(new MomentumOp(boundary, matchedInputs, inputs));
            }
        };
    }

    private static List<AccelerationInput> accelerationInputs(List<OpInput> inputs) {
        List<AccelerationInput> result = new ArrayList<>();
        for (OpInput input : inputs) {
            if (!(input instanceof OpInput.Rune rune)) continue;
            PositionedGlyph glyph = rune.glyph();
            double magnitude = glyph.length() * ACCELERATION_SCALE;
            switch (rune.symbolName()) {
                case "arrow" -> result.add(new AccelerationInput(glyph.front(), magnitude, false));
                case "arrow_up" -> result.add(new AccelerationInput(Vec3.ZERO, magnitude, true));
                default -> {
                }
            }
        }
        return List.copyOf(result);
    }

    public record AccelerationInput(Vec3 direction, double magnitude, boolean alongFacing) {
    }
}
