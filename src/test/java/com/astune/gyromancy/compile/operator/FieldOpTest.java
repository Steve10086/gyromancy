package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.field.RectangularFieldShape;
import com.astune.gyromancy.api.field.ShapeOrientation;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpInput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FieldOpTest {
    @Test
    void rejectsMomentumAsTheOnlyCurrentlyUnsupportedCompiledChild() {
        MomentumOp momentum = new MomentumOp(List.of());

        assertFalse(FieldOp.supportsChild(momentum));
        assertInstanceOf(CompileResult.Failure.class,
                FieldOp.rejectUnsupportedInputs(momentum, List.of(new OpInput.Op(momentum))));
    }

    @Test
    void acceptsOtherCompiledOperationsByDefault() {
        CompiledOp ordinaryChild = new CompiledOp() {
            @Override
            public ResourceLocation id() {
                return ResourceLocation.withDefaultNamespace("ordinary_child");
            }

            @Override
            public com.astune.gyromancy.api.symbol.PositionedGlyph boundary() {
                return null;
            }

            @Override
            public List<OpInput> inputs() {
                return List.of();
            }

            @Override
            public int color() {
                return 0;
            }
        };

        assertTrue(FieldOp.supportsChild(ordinaryChild));
    }

    @Test
    void offsetsTheFieldCentreUntilItsRearBoundaryIsOnTheSourcePlane() {
        Vec3 offset = FieldOp.normalBoundaryOffset(
                new RectangularFieldShape(4.0F, 2.0F, 1.0F),
                ShapeOrientation.IDENTITY, new Vec3(0.0, 0.0, 1.0));

        assertEquals(0.0, offset.x, 1.0E-10);
        assertEquals(0.0, offset.y, 1.0E-10);
        assertEquals(2.0, offset.z, 1.0E-10);
    }
}
