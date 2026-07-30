package com.astune.gyromancy.array.runtime;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.array.MagicArrayManager;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.CompiledArray;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.compile.operator.CompiledOp;
import com.astune.gyromancy.compile.operator.PersistentOp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpRuntimeDispatcherTest {
    @Test
    void nonPersistentRootReturnsEmptyHandle() {
        CompiledOp modifier = new CompiledOp() {
            @Override
            public ResourceLocation id() {
                return ResourceLocation.fromNamespaceAndPath("gyromancy", "modifier");
            }

            @Override
            public PositionedGlyph boundary() {
                return null;
            }

            @Override
            public List<OpInput> inputs() {
                return List.of();
            }

            @Override
            public int color() {
                return 0xFFFFFFFF;
            }
        };
        CompiledArray compiled = new CompiledArray(modifier, null, List.of(), modifier.color());

        RuntimeHandle handle = OpRuntimeDispatcher.activate(compiled, null);

        assertTrue(handle.scratchData().isEmpty());
    }

    @Test
    void persistedRuntimeIdRecoversCompiledHandleForDeactivation() {
        ResourceLocation runtimeId =
                ResourceLocation.fromNamespaceAndPath("gyromancy", "test_persistent");
        OpDefinition definition = new OpDefinition() {
            @Override
            public ResourceLocation id() {
                return runtimeId;
            }

            @Override
            public List<OpInputMatcher> match() {
                return List.of(OpInputMatcher.rune("fire"));
            }

            @Override
            public CompileResult<CompiledOp> compile(
                    PositionedGlyph boundary,
                    List<OpInput> matchedInputs,
                    List<OpInput> inputs
            ) {
                return new CompileResult.Success<>(new PersistentOp() {
                    @Override public RuntimeHandle activate(OpRuntimeContext ctx) {
                        return new RuntimeHandle(Map.of());
                    }
                    @Override public void deactivate(
                            OpRuntimeContext ctx, Map<String, Object> scratchData) {}
                    @Override public ResourceLocation id() { return runtimeId; }
                    @Override public PositionedGlyph boundary() { return boundary; }
                    @Override public List<OpInput> inputs() { return inputs; }
                    @Override public int color() { return 0xFFFFFFFF; }
                });
            }
        };
        MagicArrayManager manager = new MagicArrayManager(List.of(definition));
        PositionedGlyph fire = glyph(
                "fire", SymbolRole.CENTER_SYMBOL, 1, 2.0, 3.0);
        PositionedGlyph circle = glyph(
                "circle_outer", SymbolRole.OUTER_CIRCLE, 2, 1.0, 4.0);
        ArrayObject persisted = new ArrayObject(
                UUID.fromString("00000000-0000-0000-0000-000000000010"),
                circle,
                List.of(circle, fire),
                Map.of("__runtime", runtimeId.toString()));

        PersistentOp recovered =
                OpRuntimeDispatcher.recoverPersistentOp(persisted, manager);

        assertNotNull(recovered);
        assertEquals(runtimeId, recovered.id());
    }

    private static PositionedGlyph glyph(
            String name, SymbolRole role, int id, double min, double max
    ) {
        BlockPos pos = new BlockPos(0, 64, 0);
        return new PositionedGlyph(
                UUID.fromString("00000000-0000-0000-0000-00000000000" + id),
                id,
                ResourceLocation.fromNamespaceAndPath("gyromancy", name),
                0.9f,
                role,
                new Vec3(1.0, 0.0, 0.0),
                max - min,
                max - min,
                pos,
                min, max, min, max,
                Set.of(new PixelPos(pos, Direction.NORTH, id, id, 0xFF00AA00))
        );
    }
}
