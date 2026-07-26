package com.astune.gyromancy.array.runtime;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompiledArray;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.compile.operator.CompiledOp;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
