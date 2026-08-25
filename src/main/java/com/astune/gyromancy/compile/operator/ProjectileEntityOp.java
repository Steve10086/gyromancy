package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.array.runtime.RuntimeHandle;
import com.astune.gyromancy.array.runtime.emit.EmitResult;
import com.astune.gyromancy.array.runtime.emit.EntityEmitter;
import com.astune.gyromancy.entity.ball.MagicBallEntity;
import com.astune.gyromancy.entity.ball.WaterBallEntity;
import com.astune.gyromancy.symbol.CenterSymbol;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public abstract class ProjectileEntityOp  extends EntityEffectOp {
    protected ProjectileEntityOp(ResourceLocation id, ElementType element, PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs) {
        super(id, element, boundary, matchedInputs, inputs);
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
            Vec3 acceleration = emission.hasMotion() ? new Vec3(0.0, -0.04 * 0.5, 0.0) : Vec3.ZERO;
            MagicBallEntity entity = create(level, pos, emission.velocity(), acceleration, size);
            entity.setParentBindingAllowed(ctx.assignParent());
            if (ctx.assignParent()) entity.setParent(ctx.parent());
            List<EntityPayload> entityPayload = new ArrayList<>(payloadFor(ctx));
            entity.setPayload(entityPayload);
            EntityEmitter.INSTANCE.emit(level, getId(), entity, result);
        }
        return result.toRuntimeHandle();
    }

    public abstract MagicBallEntity create(Level level, Vec3 pos, Vec3 velocity, Vec3 acceleration, float size);

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
