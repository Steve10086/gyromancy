package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.element.ManaElements;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.CompileDiagnostic;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.array.runtime.RuntimeHandle;
import com.astune.gyromancy.array.runtime.emit.EmitResult;
import com.astune.gyromancy.array.runtime.emit.EntityEmitter;
import com.astune.gyromancy.entity.ball.MagicBallEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public abstract class ProjectileEntityOp  extends EntityEffectOp {
    private static final String SHAPE_UNSUPPORTED = "projectile_rejects_shape";
    private static final double PROJECTILE_GRAVITY = 0.04 * 0.5;

    protected ProjectileEntityOp(ResourceLocation id, ElementType element, PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs) {
        super(id, element, boundary, matchedInputs, inputs);
    }

    /** Every projectile costs 20 points of its own element instead of the default mana. */
    @Override
    public ManaElements getCost() {
        return elementCost(20.0);
    }

    @Override
    public RuntimeHandle activate(OpRuntimeContext ctx) {
        ServerLevel level = ctx.level();
        PositionedGlyph centerGlyph = primaryRune();
        if (centerGlyph == null) return new RuntimeHandle(Map.of());
        EmitResult result = new EmitResult();
        Vec3 center = ctx.positionFor(ctx.arrayRootGlyph());
        Vec3 normal = ctx.normalFor(centerGlyph);
        for (EmitOp.Emission emission : emissions(ctx)) {
            float size = Math.max(0.1F, scale(ctx) * emission.sizeScale());
            Vec3 pos = center.add(normal.scale(size * 2.0));
            Vec3 acceleration = Vec3.ZERO;
            MagicBallEntity entity = create(level, pos, emission.velocity(), acceleration, size);
            if (emission.hasMotion() || emission.velocity().lengthSqr() > 0.0
                    || acceleration.lengthSqr() > 0.0) {
                entity.setGravity(PROJECTILE_GRAVITY);
            }            entity.setParentBindingAllowed(ctx.assignParent());
            if (ctx.assignParent()) entity.setParent(ctx.parent());
            List<EntityPayload> entityPayload = new ArrayList<>(payloadFor(ctx));
            com.astune.gyromancy.Gyromancy.LOGGER.debug(
                    "[Projectile] {} payloads={}", getId(),
                    entityPayload.stream().map(ProjectileEntityOp::describePayload).toList());
            entity.setPayload(entityPayload);
            EntityEmitter.INSTANCE.emit(level, getId(), entity, result);
        }
        return result.toRuntimeHandle();
    }

    /**
     * Every projectile records its spawned balls in the runtime handle.  Keep
     * their teardown here so all projectile variants enter their own deferred
     * discard lifecycle.
     */
    @Override
    public final void deactivate(OpRuntimeContext ctx, Map<String, Object> scratchData) {
        for (var emitted : EmitResult.emissions(scratchData)) {
            if (emitted.ref() instanceof ArrayObject.EntityRef ref
                    && ref.resolve(ctx.level()) instanceof MagicBallEntity entity) {
                entity.readyToDiscard();
            }
        }
    }

    public abstract MagicBallEntity create(Level level, Vec3 pos, Vec3 velocity, Vec3 acceleration, float size);

    private static String describePayload(EntityPayload payload) {
        if (!(payload instanceof MomentumOp momentum)) {
            return payload.getClass().getSimpleName();
        }
        return "Momentum(" + momentum.phase()
                + " accel=" + momentum.accelerationInputs().size()
                + " modes=" + momentum.accelerationInputs().stream()
                .map(input -> input.updateMode().name())
                .toList() + ")";
    }

    /** A field shape has no projectile meaning and is rejected during compilation. */
    protected static <T extends CompiledOp> CompileResult<T> rejectUnsupportedInputs(
            T compiled, List<OpInput> inputs) {
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Op op && op.operator() instanceof ShapeOp) {
                return new CompileResult.Failure<>(List.of(new CompileDiagnostic(
                        SHAPE_UNSUPPORTED,
                        "Projectile operators cannot use a field shape operator")));
            }
        }
        return new CompileResult.Success<>(compiled);
    }

    protected List<EntityPayload> payloadFor() {
        Set<EntityPayload> payload = new HashSet<>(defaultPayload());
        payload.addAll(conditionalPayload());
        return payload(payload.stream().toList());
    }

    protected List<EntityPayload> payloadFor(OpRuntimeContext context) {
        return payloadFor();
    }

    abstract Collection<? extends EntityPayload> conditionalPayload();

    abstract Collection<? extends EntityPayload> defaultPayload();

    abstract PositionedGlyph primaryRune();
    abstract ResourceLocation getId();
}
