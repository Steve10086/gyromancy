package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.ArrayNodeCompiler;
import com.astune.gyromancy.array.compile.ArrayNode;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.CompiledArray;
import com.astune.gyromancy.array.compile.GroupNode;
import com.astune.gyromancy.array.compile.LocalCompiler;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.RuntimeModel;
import com.astune.gyromancy.array.compile.SequenceNode;
import com.astune.gyromancy.array.compile.SymbolNode;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterMomentumTickTest {
    @Test
    void tickAccelerationRotatesOverTime() {
        GroupNode ast = group(circle(479),
                rune("water", 468),
                group(circle(476),
                        rune("motion", 469),
                        group(circle(478),
                                rune("motion", 470),
                                rune("loop", 471),
                                group(circle(463),
                                        rune("revert", 428),
                                        rune("arrow", 462),
                                        group(circle(464),
                                                rune("loop", 427),
                                                facedRune("arrow", 458, new Vec3(0, 1, 0)),
                                                group(circle(466),
                                                        rune("arrow", 429),
                                                        rune("arrow", 430))),
                                        group(circle(465), rune("space", 425))))));

        CompileResult.Success<CompiledArray> success = assertInstanceOf(
                CompileResult.Success.class, ArrayNodeCompiler.compile(ast));
        CompileResult<RuntimeModel> runtime = new LocalCompiler().compile(success.value());
        CompileResult.Success<RuntimeModel> runtimeSuccess = assertInstanceOf(
                CompileResult.Success.class, runtime);
        WaterProjectileOp root = assertInstanceOf(WaterProjectileOp.class,
                runtimeSuccess.value().root());

        MomentumOp outer = null;
        for (OpInput input : root.inputs()) {
            if (input instanceof OpInput.Op op && op.operator() instanceof MomentumOp momentum) {
                outer = momentum;
            }
        }
        assertTrue(outer != null);

        List<EntityPayload> payloads = new ArrayList<>();
        outer.contributeEntityPayloads(payloads, OpRuntimeContext.empty());
        MomentumOp tick = assertInstanceOf(MomentumOp.class, payloads.getFirst());

        assertTrue(tick.accelerationInputs().stream()
                        .allMatch(input -> input.updateMode() == MomentumOp.UpdateMode.DYNAMIC),
                "loop-sourced acceleration must be re-sampled every tick");

        // Simulate the live feedback: the ball's delta movement is integrated
        // from the solved acceleration and then used as the sampling reference.
        Vec3 velocity = Vec3.ZERO;
        Vec3 facing = new Vec3(0, 0, 1);
        double peak = 0.0;
        for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
            Vec3 solved = tick.accelerationForTick(velocity, facing);
            System.out.printf("tick=%d accel=%s |accel|=%.5f%n", tickIndex, solved, solved.length());
            if (tickIndex >= 2) peak = Math.max(peak, solved.length());
            velocity = velocity.add(solved.scale(0.1));
        }
        assertTrue(peak > 0.001,
                "loop-sourced rotation must keep producing acceleration after the first tick");
    }

    private static GroupNode group(PositionedGlyph circle, ArrayNode... children) {
        return new GroupNode(circle, new SequenceNode(List.of(children)));
    }

    private static SymbolNode rune(String name, int id) {
        return new SymbolNode(glyph(name, SymbolRole.PARAMETER_RUNE, id, new Vec3(1, 0, 0)));
    }

    private static SymbolNode facedRune(String name, int id, Vec3 front) {
        return new SymbolNode(glyph(name, SymbolRole.PARAMETER_RUNE, id, front));
    }

    private static PositionedGlyph circle(int id) {
        return glyph("circle_outer", SymbolRole.OUTER_CIRCLE, id, new Vec3(1, 0, 0));
    }

    private static PositionedGlyph glyph(String name, SymbolRole role, int id, Vec3 front) {
        BlockPos pos = new BlockPos(0, 64, 0);
        return new PositionedGlyph(
                UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", id)),
                id,
                ResourceLocation.fromNamespaceAndPath("gyromancy", name),
                0.9f,
                role,
                front,
                2.0,
                1.0,
                pos,
                0.0, 4.0, 0.0, 4.0,
                Set.of(new PixelPos(pos, Direction.NORTH, id, id, 0xFF00AA00)));
    }
}
