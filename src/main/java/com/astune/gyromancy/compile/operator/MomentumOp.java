package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.geometry.SurfaceFrame;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.array.compile.LocalCompileContext;
import com.astune.gyromancy.array.compile.LocalCompileResult;
import com.astune.gyromancy.compile.vector.VectorContext;
import com.astune.gyromancy.compile.vector.VectorOp;
import com.astune.gyromancy.compile.vector.VectorOpSerialization;
import com.astune.gyromancy.entity.MagicEntity;
import com.astune.gyromancy.symbol.SymbolCatalog;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Consumes VectorOps and applies them as movement. Vector construction and
 * matching remain outside this class.
 */
public final class MomentumOp extends OnEntityTickOp implements CompiledOp, LocalCompilable,
        EntityEmissionModifier, EntityPayloadContributor {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "momentum");

    private static final double MOMENTUM_SCALE = 1.0 / 10;
    private static final double DIRECTION_EPSILON = 1.0E-8;

    /**
     * Applies the same lift used by the projectile momentum path.  Arrow
     * vectors are authored in the array plane; the residual length is added
     * on the world-up axis so opposing arrows still produce a usable motion
     * direction.  Fields use this helper when converting their arrows into a
     * fixed {@link com.astune.gyromancy.api.field.FieldDirection}.
     */
    public static Vec3 withUpwardComponent(Vec3 vector, double arrowSizeSum,
                                           double liftDirection) {
        Vec3 safeVector = vector == null ? Vec3.ZERO : vector;
        double speed = safeVector.length();
        double lift = ((arrowSizeSum - speed)) * liftDirection;
        return safeVector.add(0.0, lift, 0.0);
    }

    /** How the provided vector is applied against the reference axis. */
    public enum MotionMode {
        DIRECT,
        TANGENTIAL
    }

    /** Whether the compiled operator modifies emission or the entity payload. */
    public enum ApplicationPhase {
        SPAWN,
        TICK
    }

    /** Controls how a nested MomentumOp vector is consumed as acceleration. */
    public enum UpdateMode {
        SNAPSHOT,
        DYNAMIC
    }

    private static final Codec<PersistedInput> INPUT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            VectorOpSerialization.VEC3_CODEC.optionalFieldOf("direction", Vec3.ZERO)
                    .forGetter(PersistedInput::direction),
            Codec.DOUBLE.optionalFieldOf("magnitude", 0.0).forGetter(PersistedInput::magnitude),
            Codec.BOOL.optionalFieldOf("along_facing", false).forGetter(PersistedInput::alongFacing),
            VectorOpSerialization.VEC3_CODEC.optionalFieldOf("local_direction", Vec3.ZERO)
                    .forGetter(PersistedInput::localDirection),
            Codec.STRING.optionalFieldOf("direction_frame")
                    .forGetter(PersistedInput::directionFrame),
            VectorOpSerialization.CODEC.optionalFieldOf("vector")
                    .forGetter(PersistedInput::vector),
            VectorOpSerialization.CODEC.optionalFieldOf("provider")
                    .forGetter(PersistedInput::legacyProvider),
            enumCodec(UpdateMode.class).optionalFieldOf("update_mode", UpdateMode.SNAPSHOT)
                    .forGetter(PersistedInput::updateMode),
            enumCodec(MotionMode.class).optionalFieldOf("motion_mode", MotionMode.DIRECT)
                    .forGetter(PersistedInput::motionMode)
    ).apply(instance, PersistedInput::new));

    public static final Codec<MomentumOp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            INPUT_CODEC.listOf().fieldOf("inputs").forGetter(MomentumOp::serializedInputs),
            INPUT_CODEC.listOf().optionalFieldOf("velocity_inputs", List.of())
                    .forGetter(MomentumOp::serializedVelocityInputs),
            VectorOpSerialization.VEC3_CODEC.optionalFieldOf("acceleration", Vec3.ZERO)
                    .forGetter(op -> op.acceleration),
            Codec.INT.optionalFieldOf("elapsed_ticks", 0).forGetter(op -> op.elapsedTicks),
            Codec.DOUBLE.optionalFieldOf("drain_rotation_speed", 0.0)
                    .forGetter(op -> op.drainRotationSpeed),
            enumCodec(ApplicationPhase.class).optionalFieldOf("phase", ApplicationPhase.TICK)
                    .forGetter(MomentumOp::phase),
            SurfaceFrame.CODEC.optionalFieldOf("compile_frame")
                    .forGetter(op -> op.compileFrame),
            SurfaceFrame.CODEC.optionalFieldOf("activation_frame")
                    .forGetter(op -> op.activationFrame)
    ).apply(instance, MomentumOp::fromCodec));

    private final PositionedGlyph boundary;
    private final List<OpInput> matchedInputs;
    private final List<OpInput> inputs;
    /** Compile-time geometry only. Runtime frames are kept separately. */
    private final Optional<SurfaceFrame> compileFrame;
    private final List<VectorInput> velocityInputs;
    private final List<AccelerationInput> accelerationInputs;
    private final Optional<SurfaceFrame> activationFrame;
    private final ApplicationPhase phase;
    private final boolean dynamic;
    private final double drainRotationSpeed;
    private Vec3 acceleration;
    private int elapsedTicks;
    /** Spawn vectors are sampled once and reused for every emission read. */
    private List<Vec3> spawnVectorSnapshots;
    /** Snapshot acceleration vectors are sampled once on their first tick. */
    private List<Vec3> accelerationSnapshots;

    static MomentumOp compiled(PositionedGlyph boundary, List<OpInput> matchedInputs,
                               List<OpInput> inputs, MomentumInputResolver resolver) {
        return compiled(boundary, matchedInputs, inputs, resolver,
                OpResolveContext.forVector(boundary));
    }

    static MomentumOp compiled(PositionedGlyph boundary, List<OpInput> matchedInputs,
                               List<OpInput> inputs, MomentumInputResolver resolver,
                               OpResolveContext context) {
        Optional<SurfaceFrame> compileFrame = Optional.ofNullable(
                boundary == null ? null : boundary.surface());
        MomentumInputResolver.ResolvedInputs resolved = resolver.resolve(boundary, inputs, context);
        return new MomentumOp(boundary, matchedInputs, inputs, compileFrame,
                resolved.velocityInputs(), resolved.accelerationInputs(), Vec3.ZERO, 0,
                resolver.drainRotationSpeed(inputs), ApplicationPhase.SPAWN,
                resolved.dynamic(), Optional.empty());
    }

    @Override
    public LocalCompileResult localCompile(LocalCompileContext context) {
        try {
            MomentumInputResolver.ResolvedInputs resolved = resolvedInputs(null);
            return LocalCompileResult.success(new MomentumOp(
                    boundary, matchedInputs, inputs, compileFrame,
                    resolved.velocityInputs(), resolved.accelerationInputs(),
                    acceleration, elapsedTicks, drainRotationSpeed, phase,
                    resolved.dynamic(), activationFrame));
        } catch (RuntimeException exception) {
            return LocalCompileResult.failure("invalid_momentum_inputs", exception.getMessage());
        }
    }

    private MomentumOp(List<AccelerationInput> accelerationInputs, Vec3 acceleration,
                       int elapsedTicks, double drainRotationSpeed, ApplicationPhase phase,
                       Optional<SurfaceFrame> compileFrame, Optional<SurfaceFrame> activationFrame) {
        this(null, List.of(), List.of(), compileFrame, phase == ApplicationPhase.SPAWN
                        ? accelerationInputs.stream()
                        .map(input -> new VectorInput(input.vector(), input.motionMode()))
                        .toList()
                        : List.of(),
                phase == ApplicationPhase.TICK ? accelerationInputs : List.of(),
                acceleration, elapsedTicks, drainRotationSpeed, phase, false, activationFrame);
    }

    MomentumOp(List<AccelerationInput> accelerationInputs) {
        this(accelerationInputs, ApplicationPhase.TICK);
    }

    MomentumOp(List<AccelerationInput> accelerationInputs, ApplicationPhase phase) {
        this(accelerationInputs, Vec3.ZERO, 0, 0.0, phase,
                Optional.empty(), Optional.empty());
    }

    private MomentumOp(PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs,
                       Optional<SurfaceFrame> compileFrame, List<VectorInput> velocityInputs,
                       List<AccelerationInput> accelerationInputs,
                        Vec3 acceleration, int elapsedTicks, double drainRotationSpeed,
                        ApplicationPhase phase, boolean dynamic,
                        Optional<SurfaceFrame> activationFrame) {
        this.boundary = boundary;
        this.matchedInputs = List.copyOf(matchedInputs);
        this.inputs = List.copyOf(inputs);
        this.compileFrame = compileFrame == null ? Optional.empty() : compileFrame;
        this.velocityInputs = List.copyOf(velocityInputs);
        this.accelerationInputs = List.copyOf(accelerationInputs);
        this.activationFrame = activationFrame == null ? Optional.empty() : activationFrame;
        this.phase = Objects.requireNonNull(phase, "phase");
        this.dynamic = dynamic;
        this.drainRotationSpeed = drainRotationSpeed;
        this.acceleration = acceleration == null ? Vec3.ZERO : acceleration;
        this.elapsedTicks = Math.max(0, elapsedTicks);
    }

    private static MomentumOp fromCodec(List<PersistedInput> inputs,
                                        List<PersistedInput> velocityInputs,
                                        Vec3 acceleration,
                                        int elapsedTicks, double drainRotationSpeed,
                                        ApplicationPhase phase, Optional<SurfaceFrame> compileFrame,
                                        Optional<SurfaceFrame> activationFrame) {
        List<AccelerationInput> runtimeInputs = inputs.stream()
                .map(PersistedInput::runtimeAccelerationInput)
                .toList();
        List<VectorInput> runtimeVelocityInputs = velocityInputs.stream()
                .map(PersistedInput::runtimeVectorInput)
                .toList();
        return new MomentumOp(null, List.of(), List.of(), compileFrame, runtimeVelocityInputs,
                runtimeInputs, acceleration, elapsedTicks, drainRotationSpeed, phase,
                false, activationFrame);
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

    public List<VectorInput> velocityInputs() {
        return velocityInputs;
    }

    public boolean dynamic() {
        return dynamic;
    }

    public Optional<SurfaceFrame> compileFrame() {
        return compileFrame;
    }

    public Optional<SurfaceFrame> activationFrame() {
        return activationFrame;
    }

    public ApplicationPhase phase() {
        return phase;
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

    public EmitOp.Emission modifyEntityEmission(EmitOp.Emission emission,
                                                OpRuntimeContext context) {
        if (phase != ApplicationPhase.SPAWN) return emission;
        MomentumInputResolver.ResolvedInputs resolved = resolvedInputs(context);
        List<VectorInput> runtimeVelocityInputs = resolved.velocityInputs();
        if (runtimeVelocityInputs.isEmpty()) return emission;

        SurfaceFrame runtimeFrame = runtimeFrame(context);
        Vec3 fallbackNormal = runtimeNormal(context, runtimeFrame);
        Vec3 reference = emission.velocity();
        if (reference.lengthSqr() < DIRECTION_EPSILON) reference = fallbackNormal;

        VectorContext vectorContext = vectorContext(context, reference, reference,
                runtimeFrame, fallbackNormal);
        List<Vec3> sampled = spawnVectorSnapshots(vectorContext, runtimeVelocityInputs);
        Vec3 contribution = solveVector(reference, reference, vectorContext,
                runtimeVelocityInputs, sampled);
        double momentumSum = vectorMagnitudeSum(sampled);
        return new EmitOp.Emission(
                emission.velocity().add(contribution),
                emission.motionSum() + momentumSum,
                emission.sizeScale(),
                emission.hasMotion() || !runtimeVelocityInputs.isEmpty());
    }

    public void contributeEntityPayloads(List<EntityPayload> payloads,
                                         OpRuntimeContext context) {
        if (phase != ApplicationPhase.SPAWN) return;
        List<AccelerationInput> runtimeAccelerationInputs = resolvedInputs(context).accelerationInputs();
        Gyromancy.LOGGER.debug("[Momentum] payload contribution boundary={} accel={} modes={}",
                boundary == null ? "none" : boundary.glyphId(),
                runtimeAccelerationInputs.size(),
                runtimeAccelerationInputs.stream()
                        .map(input -> input.updateMode() + ":" + input.vector().getClass().getSimpleName())
                        .toList());
        if (!runtimeAccelerationInputs.isEmpty()) {
            payloads.add(runtimePayload(context, runtimeAccelerationInputs));
        }
    }

    @Override
    public void onEntityTick(EntityTickContext ctx) {
        if (ctx.isClientSide() || phase != ApplicationPhase.TICK) return;

        SurfaceFrame arrayFrame = ctx.arrayFrame();
        Vec3 arrayNormal = arrayFrame == null
                ? compileFrame.map(SurfaceFrame::normal).orElse(Vec3.ZERO)
                : arrayFrame.normal();
        VectorContext vectorContext = vectorContext(null, ctx.velocity(), ctx.facing(),
                arrayFrame, arrayNormal, gravityFor(ctx.owner()));
        List<Vec3> sampled = accelerationVectors(vectorContext);
        acceleration = solveVector(ctx.velocity(), ctx.facing(), vectorContext,
                accelerationInputs, sampled);
        Vec3 result = acceleration.scale(MOMENTUM_SCALE);
        if (elapsedTicks < 12) {
            Gyromancy.LOGGER.debug(
                    "[Momentum] tick={} boundary={} sampled={} acceleration={} velocity={} facing={} normal={} gravity={}",
                    elapsedTicks, boundary == null ? "none" : boundary.glyphId(), sampled,
                    acceleration, ctx.velocity(), ctx.facing(), arrayNormal,
                    gravityFor(ctx.owner()));
        }
        elapsedTicks++;
        ctx.owner().addDeltaMovement(result);
    }

    Vec3 accelerationForTick(Vec3 facingDirection) {
        return accelerationForTick(facingDirection, facingDirection);
    }

    Vec3 accelerationForTick(Vec3 velocityDirection, Vec3 entityFacing) {
        VectorContext vectorContext = vectorContext(null, velocityDirection, entityFacing,
                null, compileFrame.map(SurfaceFrame::normal).orElse(Vec3.ZERO));
        List<Vec3> sampled = accelerationVectors(vectorContext);
        acceleration = solveVector(velocityDirection, entityFacing, vectorContext,
                accelerationInputs, sampled);
        Vec3 result = acceleration.scale(MOMENTUM_SCALE);
        elapsedTicks++;
        return result;
    }

    Vec3 solveVector(Vec3 facingDirection) {
        return solveVector(facingDirection, facingDirection,
                vectorContext(null, facingDirection, facingDirection, null,
                        compileFrame.map(SurfaceFrame::normal).orElse(Vec3.ZERO)));
    }

    private Vec3 solveVector(Vec3 velocityDirection, Vec3 entityFacing,
                             VectorContext vectorContext) {
        List<Vec3> sampled = accelerationInputs.stream()
                .map(input -> input.vector().provide(vectorContext))
                .toList();
        return solveVector(velocityDirection, entityFacing, vectorContext,
                accelerationInputs, sampled);
    }

    private Vec3 solveVector(Vec3 velocityDirection, Vec3 entityFacing,
                             VectorContext vectorContext,
                             List<? extends AppliedInput> appliedInputs,
                             List<Vec3> sampledVectors) {
        Vec3 velocityAxis = directionOrZero(velocityDirection);
        Vec3 facingAxis = directionOrZero(entityFacing);
        Vec3 normalAxis = directionOrZero(vectorContext.arrayNormal());
        Vec3 referenceAxis = velocityAxis.lengthSqr() >= DIRECTION_EPSILON
                ? velocityAxis
                : facingAxis.lengthSqr() >= DIRECTION_EPSILON ? facingAxis : normalAxis;

        Vec3 result = Vec3.ZERO;
        for (int index = 0; index < appliedInputs.size(); index++) {
            AppliedInput input = appliedInputs.get(index);
            Vec3 vector = sampledVectors == null
                    ? input.vector().provide(vectorContext)
                    : sampledVectors.get(index);
            double magnitude = vector.length();
            if (input.motionMode() == MotionMode.TANGENTIAL) {
                if (referenceAxis.lengthSqr() < DIRECTION_EPSILON) continue;
                vector = vector.subtract(referenceAxis.scale(vector.dot(referenceAxis)));
            }
            if (vector.lengthSqr() < DIRECTION_EPSILON) continue;
            result = result.add(vector.normalize().scale(magnitude));
        }

        if (drainRotationSpeed != 0.0
                && referenceAxis.lengthSqr() >= DIRECTION_EPSILON
                && result.lengthSqr() >= DIRECTION_EPSILON) {
            double radians = Math.toRadians(drainRotationSpeed * elapsedTicks);
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            result = result.scale(cos)
                    .add(referenceAxis.cross(result).scale(sin))
                    .add(referenceAxis.scale(referenceAxis.dot(result) * (1.0 - cos)));
        }
        return result;
    }

    private MomentumInputResolver.ResolvedInputs resolvedInputs(OpRuntimeContext context) {
        return new MomentumInputResolver.ResolvedInputs(
                velocityInputs, accelerationInputs, dynamic);
    }

    private List<Vec3> spawnVectorSnapshots(VectorContext context,
                                            List<VectorInput> runtimeVelocityInputs) {
        if (spawnVectorSnapshots == null
                || spawnVectorSnapshots.size() != runtimeVelocityInputs.size()) {
            List<Vec3> snapshots = new ArrayList<>(runtimeVelocityInputs.size());
            for (VectorInput input : runtimeVelocityInputs) {
                snapshots.add(input.vector().provide(context));
            }
            spawnVectorSnapshots = List.copyOf(snapshots);
        }
        return spawnVectorSnapshots;
    }

    private List<Vec3> accelerationVectors(VectorContext context) {
        if (accelerationSnapshots == null
                || accelerationSnapshots.size() != accelerationInputs.size()) {
            accelerationSnapshots = new ArrayList<>(accelerationInputs.size());
            for (int index = 0; index < accelerationInputs.size(); index++) {
                accelerationSnapshots.add(null);
            }
        }

        List<Vec3> vectors = new ArrayList<>(accelerationInputs.size());
        for (int index = 0; index < accelerationInputs.size(); index++) {
            AccelerationInput input = accelerationInputs.get(index);
            Vec3 vector = accelerationSnapshots.get(index);
            if (input.updateMode() == UpdateMode.DYNAMIC || vector == null) {
                vector = input.vector().provide(context);
                if (input.updateMode() == UpdateMode.SNAPSHOT) {
                    accelerationSnapshots.set(index, vector);
                }
            }
            vectors.add(vector);
        }
        return List.copyOf(vectors);
    }

    private static double vectorMagnitudeSum(List<Vec3> vectors) {
        double sum = 0.0;
        for (Vec3 vector : vectors) sum += vector.length();
        return sum;
    }

    int elapsedTicks() {
        return elapsedTicks;
    }

    Vec3 currentAcceleration() {
        return acceleration;
    }

    private MomentumOp runtimePayload(OpRuntimeContext context,
                                      List<AccelerationInput> runtimeAccelerationInputs) {
        Optional<SurfaceFrame> activation = context == null
                ? Optional.empty()
                : Optional.ofNullable(context.activationFrame());
        return new MomentumOp(null, List.of(), List.of(), compileFrame, List.of(),
                runtimeAccelerationInputs, Vec3.ZERO, 0, drainRotationSpeed,
                ApplicationPhase.TICK, false, activation);
    }

    private static List<PersistedInput> serializedInputs(MomentumOp op) {
        List<PersistedInput> serialized = new ArrayList<>();
        for (AccelerationInput input : op.accelerationInputs) {
            VectorOpSerialization.SerializedVector vector =
                    VectorOpSerialization.encode(input.vector())
                            .orElseThrow(() -> new IllegalStateException(
                                    "VectorOp has no persistence adapter: "
                                            + input.vector().getClass().getName()));
            VectorOpSerialization.LegacyData legacy =
                    VectorOpSerialization.encodeLegacy(input.vector())
                            .orElse(new VectorOpSerialization.LegacyData(
                                    Vec3.ZERO, 0.0, false, Vec3.ZERO, Optional.empty()));
            serialized.add(PersistedInput.from(legacy, vector, input.updateMode(),
                    input.motionMode()));
        }
        return List.copyOf(serialized);
    }

    private static List<PersistedInput> serializedVelocityInputs(MomentumOp op) {
        List<PersistedInput> serialized = new ArrayList<>();
        for (VectorInput input : op.velocityInputs) {
            VectorOpSerialization.SerializedVector vector =
                    VectorOpSerialization.encode(input.vector())
                            .orElseThrow(() -> new IllegalStateException(
                                    "VectorOp has no persistence adapter: "
                                            + input.vector().getClass().getName()));
            VectorOpSerialization.LegacyData legacy =
                    VectorOpSerialization.encodeLegacy(input.vector())
                            .orElse(new VectorOpSerialization.LegacyData(
                                    Vec3.ZERO, 0.0, false, Vec3.ZERO, Optional.empty()));
            serialized.add(PersistedInput.from(legacy, vector, UpdateMode.SNAPSHOT,
                    input.motionMode()));
        }
        return List.copyOf(serialized);
    }

    private SurfaceFrame runtimeFrame(OpRuntimeContext context) {
        if (context != null && boundary != null) return context.frameFor(boundary);
        return compileFrame.orElse(null);
    }

    private Vec3 runtimeNormal(OpRuntimeContext context, SurfaceFrame runtimeFrame) {
        if (context != null && boundary != null) return context.normalFor(boundary);
        return runtimeFrame == null ? Vec3.ZERO : runtimeFrame.normal();
    }

    private VectorContext vectorContext(OpRuntimeContext context, Vec3 velocity,
                                        Vec3 facing, SurfaceFrame liveFrame,
                                        Vec3 arrayNormal) {
        return vectorContext(context, velocity, facing, liveFrame, arrayNormal,
                gravityFor(context));
    }

    private VectorContext vectorContext(OpRuntimeContext context, Vec3 velocity,
                                        Vec3 facing, SurfaceFrame liveFrame,
                                        Vec3 arrayNormal, double gravity) {
        SurfaceFrame activation = activationFrame.orElse(null);
        if (context != null) {
            activation = context.activationFrame() == null ? activation : context.activationFrame();
            liveFrame = context.liveFrame() == null ? liveFrame : context.liveFrame();
        }
        return new VectorContext(Optional.ofNullable(compileFrame.orElse(null)),
                Optional.ofNullable(activation), Optional.ofNullable(liveFrame),
                velocity, facing, arrayNormal, elapsedTicks, gravity);
    }

    private static double gravityFor(OpRuntimeContext context) {
        if (context == null || !(context.parent() instanceof Entity entity)) return 0.0;
        return gravityFor(entity);
    }

    private static double gravityFor(Entity entity) {
        if (entity instanceof MagicEntity magic) return magic.gravity();
        return entity == null ? 0.0 : entity.getGravity();
    }

    private static Vec3 directionOrZero(Vec3 direction) {
        return direction.lengthSqr() < DIRECTION_EPSILON ? Vec3.ZERO : direction.normalize();
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.xmap(
                value -> Enum.valueOf(type, value.toUpperCase(Locale.ROOT)),
                value -> value.name().toLowerCase(Locale.ROOT));
    }

    private interface AppliedInput {
        VectorOp vector();

        MotionMode motionMode();
    }

    /** A vector consumed as the spawn-time velocity contribution. */
    public record VectorInput(VectorOp vector, MotionMode motionMode)
            implements AppliedInput {
        public VectorInput {
            vector = Objects.requireNonNull(vector, "vector");
            motionMode = Objects.requireNonNull(motionMode, "motionMode");
        }
    }

    /** Momentum's consumer parameters; VectorOp behavior remains independent. */
    public record AccelerationInput(VectorOp vector, MotionMode motionMode,
                                    UpdateMode updateMode) implements AppliedInput {
        public AccelerationInput {
            vector = Objects.requireNonNull(vector, "vector");
            motionMode = Objects.requireNonNull(motionMode, "motionMode");
            updateMode = Objects.requireNonNull(updateMode, "updateMode");
        }

        public AccelerationInput(VectorOp vector, MotionMode motionMode) {
            this(vector, motionMode, UpdateMode.SNAPSHOT);
        }

        public AccelerationInput(VectorOp vector) {
            this(vector, MotionMode.DIRECT, UpdateMode.SNAPSHOT);
        }
    }

    private record PersistedInput(
            Vec3 direction,
            double magnitude,
            boolean alongFacing,
            Vec3 localDirection,
            Optional<String> directionFrame,
            Optional<VectorOpSerialization.SerializedVector> vector,
            Optional<VectorOpSerialization.SerializedVector> legacyProvider,
            UpdateMode updateMode,
            MotionMode motionMode
    ) {
        private PersistedInput {
            direction = direction == null ? Vec3.ZERO : direction;
            localDirection = localDirection == null ? Vec3.ZERO : localDirection;
            directionFrame = directionFrame == null ? Optional.empty() : directionFrame;
            vector = vector == null ? Optional.empty() : vector;
            legacyProvider = legacyProvider == null ? Optional.empty() : legacyProvider;
            updateMode = updateMode == null ? UpdateMode.SNAPSHOT : updateMode;
            motionMode = motionMode == null ? MotionMode.DIRECT : motionMode;
        }

        private static PersistedInput from(VectorOpSerialization.LegacyData legacy,
                                            VectorOpSerialization.SerializedVector vector,
                                            UpdateMode updateMode, MotionMode motionMode) {
            return new PersistedInput(legacy.direction(), legacy.magnitude(), legacy.alongFacing(),
                    legacy.localDirection(), legacy.directionFrame(), Optional.of(vector), Optional.empty(),
                    updateMode, motionMode);
        }

        private VectorOp resolveVector() {
            return vector.or(() -> legacyProvider)
                    .map(VectorOpSerialization::decode)
                    .orElseGet(() -> VectorOpSerialization.decodeLegacy(
                            new VectorOpSerialization.LegacyData(
                                    direction, magnitude, alongFacing, localDirection, directionFrame)));
        }

        private AccelerationInput runtimeAccelerationInput() {
            return new AccelerationInput(resolveVector(), motionMode, updateMode);
        }

        private VectorInput runtimeVectorInput() {
            return new VectorInput(resolveVector(), motionMode);
        }
    }
}
