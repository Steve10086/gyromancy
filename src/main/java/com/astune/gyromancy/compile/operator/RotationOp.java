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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

@RegisteredOp
public final class RotationOp extends OnEntityTickOp implements CompiledOp {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "rotation");
    private static final ResourceLocation DRAIN_SYMBOL =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "drain");

    static final int ACTIVE_TICKS = 100;
    public static final double ROTATION_SPEED_SCALE = 18;
    private static final double DIRECTION_EPSILON = 1.0E-8;
    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);
    private static final Vec3 WORLD_FORWARD = new Vec3(0.0, 0.0, 1.0);

    public static final Codec<RotationOp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("rotation_speed").forGetter(RotationOp::rotationSpeed),
            Codec.INT.optionalFieldOf("elapsed_ticks", 0).forGetter(RotationOp::elapsedTicks)
    ).apply(instance, RotationOp::new));

    public static final OpDefinition DEFINITION = new OpDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("drain"));
        }

        @Override
        public List<OpInputMatcher> accepted() {
            return List.of(OpInputMatcher.rune("revert"));
        }

        @Override
        public CompileResult<CompiledOp> compile(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                                 List<OpInput> inputs) {
            return new CompileResult.Success<>(new RotationOp(boundary, matchedInputs, inputs));
        }
    };

    private final PositionedGlyph boundary;
    private final List<OpInput> matchedInputs;
    private final List<OpInput> inputs;
    private final double rotationSpeed;
    private int elapsedTicks;

    private RotationOp(PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs) {
        this(boundary, matchedInputs, inputs, rotationSpeed(inputs), 0);
    }

    RotationOp(double rotationSpeed) {
        this(null, List.of(), List.of(), rotationSpeed, 0);
    }

    private RotationOp(double rotationSpeed, int elapsedTicks) {
        this(null, List.of(), List.of(), rotationSpeed, elapsedTicks);
    }

    private RotationOp(PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs,
                       double rotationSpeed, int elapsedTicks) {
        this.boundary = boundary;
        this.matchedInputs = List.copyOf(matchedInputs);
        this.inputs = List.copyOf(inputs);
        this.rotationSpeed = rotationSpeed;
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

    public double rotationSpeed() {
        return rotationSpeed;
    }

    @Override
    public int color() {
        return SymbolCatalog.glyphColorFor(DRAIN_SYMBOL);
    }

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<RotationOp> codec() {
        return CODEC;
    }

    @Override
    public void contributeEntityPayloads(List<EntityPayload> payloads) {
        payloads.add(new RotationOp(rotationSpeed));
    }

    @Override
    public void onEntityTick(EntityTickContext ctx) {
        if (ctx.isClientSide()) return;
        boolean initializeFromVelocity = elapsedTicks == 0
                && ctx.velocity().lengthSqr() >= DIRECTION_EPSILON;
        double rotation = rotationForTick();
        if (rotation == 0.0) return;

        Vec3 facing = rotatedFacingForTick(ctx.facing(), ctx.velocity(), rotation, initializeFromVelocity);
        if (facing.lengthSqr() < DIRECTION_EPSILON) return;
        applyFacing(ctx, facing);
    }

    double rotationForTick() {
        if (elapsedTicks >= ACTIVE_TICKS) return 0.0;
        elapsedTicks++;
        return rotationSpeed;
    }

    int elapsedTicks() {
        return elapsedTicks;
    }

    static Vec3 rotatedFacingForTick(Vec3 entityFacing, Vec3 velocity, double rotationDegrees,
                                     boolean initializeFromVelocity) {
        return rotatedFacing(initializeFromVelocity ? Vec3.ZERO : entityFacing, velocity, rotationDegrees);
    }

    static Vec3 rotatedFacing(Vec3 entityFacing, Vec3 velocity, double rotationDegrees) {
        Vec3 forward = directionOrZero(entityFacing);
        if (forward.lengthSqr() < DIRECTION_EPSILON) forward = directionOrZero(velocity);
        if (forward.lengthSqr() < DIRECTION_EPSILON) return Vec3.ZERO;

        Vec3 referenceUp = Math.abs(forward.dot(WORLD_UP)) < 1.0 - DIRECTION_EPSILON
                ? WORLD_UP
                : WORLD_FORWARD;
        Vec3 localUp = referenceUp.subtract(forward.scale(referenceUp.dot(forward))).normalize();
        return rotateAroundAxis(forward, localUp, -rotationDegrees).normalize();
    }

    private static Vec3 directionOrZero(Vec3 direction) {
        return direction.lengthSqr() < DIRECTION_EPSILON ? Vec3.ZERO : direction.normalize();
    }

    private static Vec3 rotateAroundAxis(Vec3 vector, Vec3 axis, double degrees) {
        double radians = Math.toRadians(degrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return vector.scale(cosine)
                .add(axis.cross(vector).scale(sine))
                .add(axis.scale(axis.dot(vector) * (1.0 - cosine)));
    }

    private static void applyFacing(EntityTickContext ctx, Vec3 facing) {
        double horizontal = Math.sqrt(facing.x * facing.x + facing.z * facing.z);
        float yaw = (float)Math.toDegrees(Math.atan2(-facing.x, facing.z));
        float pitch = (float)Math.toDegrees(Math.atan2(-facing.y, horizontal));
        ctx.owner().setYRot(Mth.wrapDegrees(yaw));
        ctx.owner().setXRot(Mth.wrapDegrees(pitch));
    }

    private static double rotationSpeed(List<OpInput> inputs) {
        double speed = 0.0;
        boolean reversed = false;
        for (OpInput input : inputs) {
            if (!(input instanceof OpInput.Rune rune)) continue;
            switch (rune.symbolName()) {
                case "drain" -> speed = rune.glyph().length() * ROTATION_SPEED_SCALE;
                case "revert" -> reversed = true;
                default -> {
                }
            }
        }
        return reversed ? -speed : speed;
    }
}
