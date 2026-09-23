package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.field.RectangularFieldShape;
import com.astune.gyromancy.api.field.CircularFieldShape;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.ArrayNode;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.GroupNode;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.SequenceNode;
import com.astune.gyromancy.array.compile.SymbolNode;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.symbol.SecretText;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeOpTest {
    @Test
    void splitSelectsTheDefaultRectangularFieldShape() {
        PositionedGlyph boundary = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph fix = glyph("fix", SymbolRole.PARAMETER_RUNE, 2);
        PositionedGlyph split = glyph("split", SymbolRole.PARAMETER_RUNE, 3);

        ShapeOp shape = shape(ShapeOp.create(boundary, List.of(new OpInput.Rune(fix)),
                List.of(new OpInput.Rune(fix), new OpInput.Rune(split))));
        RectangularFieldShape rectangle = assertInstanceOf(RectangularFieldShape.class,
                shape.resolveShape(OpRuntimeContext.empty()).orElseThrow());

        assertEquals(1.0F, rectangle.length());
        assertEquals(1.0F, rectangle.width());
        assertEquals(1.0F, rectangle.height());
    }

    @Test
    void secretTextLayersProduceQuarterBlockDimensionsInAuthoredOrder() {
        PositionedGlyph boundary = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph fix = glyph("fix", SymbolRole.PARAMETER_RUNE, 2);
        PositionedGlyph split = glyph("split", SymbolRole.PARAMETER_RUNE, 3);
        GroupNode third = group(glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 6),
                new SymbolNode(glyph("secret_text_1", SymbolRole.PARAMETER_RUNE, 7)));
        GroupNode second = group(glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 4),
                new SymbolNode(glyph("secret_text_1", SymbolRole.PARAMETER_RUNE, 5)), third);
        GroupNode first = group(glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 8),
                new SymbolNode(glyph("secret_text_1", SymbolRole.PARAMETER_RUNE, 9)), second);

        ShapeOp shape = shape(ShapeOp.create(boundary, List.of(new OpInput.Rune(fix)),
                List.of(new OpInput.Rune(fix), new OpInput.Rune(split),
                        new OpInput.RawGroup(first, List.of()))));
        RectangularFieldShape rectangle = assertInstanceOf(RectangularFieldShape.class,
                shape.resolveShape(OpRuntimeContext.empty()).orElseThrow());

        assertEquals(0.25F, rectangle.length());
        assertEquals(0.25F, rectangle.width());
        assertEquals(0.25F, rectangle.height());
        assertEquals(0.75F, ShapeOp.decodeBinary(List.of(
                SecretText.SECRET_1, SecretText.SECRET_2)));
        assertEquals(3.75F, ShapeOp.decodeBinary(List.of(
                SecretText.SECRET_1, SecretText.SECRET_2,
                SecretText.SECRET_3, SecretText.SECRET_4)));
        assertEquals(16.0F, ShapeOp.decodeBinary(List.of(
                SecretText.SECRET_7)));
        assertEquals(16.0F, ShapeOp.decodeBinary(List.of(
                SecretText.SECRET_1, SecretText.SECRET_7)));
        assertEquals(0.25F, ShapeOp.decodeBinary(List.of(
                SecretText.SECRET_1, SecretText.SECRET_1)));
    }

    @Test
    void emptyOuterCircleSelectsTheDefaultCircularFieldShape() {
        PositionedGlyph boundary = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph fix = glyph("fix", SymbolRole.PARAMETER_RUNE, 2);
        GroupNode empty = group(glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 3));

        ShapeOp shape = shape(ShapeOp.create(boundary, List.of(new OpInput.Rune(fix)),
                List.of(new OpInput.Rune(fix), new OpInput.RawGroup(empty, List.of()))));

        CircularFieldShape circle = assertInstanceOf(CircularFieldShape.class,
                shape.resolveShape(OpRuntimeContext.empty()).orElseThrow());

        assertEquals(1.0F, circle.length());
        assertEquals(1.0F, circle.width());
        assertEquals(1.0F, circle.height());
    }

    @Test
    void emptyOuterCircleCanBeFollowedBySizeLayers() {
        PositionedGlyph boundary = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph fix = glyph("fix", SymbolRole.PARAMETER_RUNE, 2);
        GroupNode selector = group(glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 3));
        GroupNode parameters = group(glyph("parameter", SymbolRole.PARAMETER_RUNE, 6),
                new SymbolNode(glyph("secret_text_1", SymbolRole.PARAMETER_RUNE, 4)),
                new SymbolNode(glyph("secret_text_3", SymbolRole.PARAMETER_RUNE, 5)));

        ShapeOp shape = shape(ShapeOp.create(boundary, List.of(new OpInput.Rune(fix)),
                List.of(new OpInput.Rune(fix), new OpInput.RawGroup(selector, List.of()),
                        new OpInput.RawGroup(parameters, List.of()))));
        CircularFieldShape circle = assertInstanceOf(CircularFieldShape.class,
                shape.resolveShape(OpRuntimeContext.empty()).orElseThrow());

        assertEquals(1.25F, circle.length());
        assertEquals(1.0F, circle.width());
        assertEquals(1.0F, circle.height());
    }

    @Test
    void nonEmptyOuterCircleAloneIsNotTheCircularSelector() {
        PositionedGlyph boundary = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph fix = glyph("fix", SymbolRole.PARAMETER_RUNE, 2);
        GroupNode parameters = group(glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 3),
                new SymbolNode(glyph("secret_text_1", SymbolRole.PARAMETER_RUNE, 4)));

        CompileResult<CompiledOp> result = ShapeOp.create(boundary, List.of(new OpInput.Rune(fix)),
                List.of(new OpInput.Rune(fix), new OpInput.RawGroup(parameters, List.of())));
        assertInstanceOf(CompileResult.Failure.class, result);
    }

    @Test
    void resolvesDeferredShapeParametersThroughTheGenericRuntimePath() {
        PositionedGlyph boundary = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph fix = glyph("fix", SymbolRole.PARAMETER_RUNE, 2);
        PositionedGlyph split = glyph("split", SymbolRole.PARAMETER_RUNE, 3);
        GroupNode source = group(glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 4),
                new SymbolNode(glyph("secret_text_1", SymbolRole.PARAMETER_RUNE, 5)),
                new SymbolNode(glyph("secret_text_3", SymbolRole.PARAMETER_RUNE, 6)));
        OpInput.Op deferred = new OpInput.Op(new DeferredShapeParameter(boundary, source), source);

        ShapeOp shape = shape(ShapeOp.create(boundary, List.of(new OpInput.Rune(fix)),
                List.of(new OpInput.Rune(fix), new OpInput.Rune(split), deferred)));
        RectangularFieldShape rectangle = assertInstanceOf(RectangularFieldShape.class,
                shape.resolveShape(OpRuntimeContext.empty()).orElseThrow());

        // SECRET_1 + SECRET_3 is binary 101, or five quarter-blocks.
        assertEquals(1.25F, rectangle.length());
    }

    @Test
    void resolvesDeferredOuterCircleAsCircularShape() {
        PositionedGlyph boundary = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph fix = glyph("fix", SymbolRole.PARAMETER_RUNE, 2);
        GroupNode source = group(glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 3));
        OpInput.Op deferred = new OpInput.Op(new DeferredShapeParameter(boundary, source), source);

        ShapeOp shape = shape(ShapeOp.create(boundary, List.of(new OpInput.Rune(fix)),
                List.of(new OpInput.Rune(fix), deferred)));
        CircularFieldShape circle = assertInstanceOf(CircularFieldShape.class,
                shape.resolveShape(OpRuntimeContext.empty()).orElseThrow());

        assertEquals(1.0F, circle.length());
        assertEquals(1.0F, circle.width());
        assertEquals(1.0F, circle.height());
    }

    @Test
    void rejectsInvalidShapeSyntaxAtCompileTimeAndBadParametersAtRuntime() {
        PositionedGlyph boundary = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph fix = glyph("fix", SymbolRole.PARAMETER_RUNE, 2);
        PositionedGlyph split = glyph("split", SymbolRole.PARAMETER_RUNE, 3);
        CompileResult<CompiledOp> duplicateSplit = ShapeOp.create(boundary,
                List.of(new OpInput.Rune(fix)),
                List.of(new OpInput.Rune(fix), new OpInput.Rune(split), new OpInput.Rune(split)));
        assertInstanceOf(CompileResult.Failure.class, duplicateSplit);

        GroupNode invalidLayer = group(glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 4),
                new SymbolNode(glyph("arrow", SymbolRole.PARAMETER_RUNE, 5)));
        ShapeOp invalidRuntimeShape = shape(ShapeOp.create(boundary, List.of(new OpInput.Rune(fix)),
                List.of(new OpInput.Rune(fix), new OpInput.Rune(split),
                        new OpInput.RawGroup(invalidLayer, List.of()))));
        assertTrue(invalidRuntimeShape.resolveShape(OpRuntimeContext.empty()).isEmpty());
    }

    @Test
    void projectileCompilationRejectsAFieldShapeChild() {
        PositionedGlyph boundary = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph fix = glyph("fix", SymbolRole.PARAMETER_RUNE, 2);
        PositionedGlyph split = glyph("split", SymbolRole.PARAMETER_RUNE, 3);
        ShapeOp shape = shape(ShapeOp.create(boundary, List.of(new OpInput.Rune(fix)),
                List.of(new OpInput.Rune(fix), new OpInput.Rune(split))));
        assertTrue(FieldOp.supportsChild(shape));

        CompileResult<CompiledOp> result = FireProjectileOp.create(boundary, List.of(),
                List.of(new OpInput.Op(shape)));
        CompileResult.Failure<CompiledOp> failure = assertInstanceOf(CompileResult.Failure.class, result);

        assertEquals("projectile_rejects_shape", failure.diagnostics().getFirst().code());
    }

    private static ShapeOp shape(CompileResult<CompiledOp> result) {
        return assertInstanceOf(ShapeOp.class,
                assertInstanceOf(CompileResult.Success.class, result).value());
    }

    private static GroupNode group(PositionedGlyph circle, ArrayNode... children) {
        return new GroupNode(circle, new SequenceNode(List.of(children)));
    }

    private static PositionedGlyph glyph(String name, SymbolRole role, int id) {
        BlockPos pos = new BlockPos(0, 64, 0);
        return new PositionedGlyph(
                UUID.fromString("00000000-0000-0000-0000-00000000000" + id),
                id,
                ResourceLocation.fromNamespaceAndPath("gyromancy", name),
                0.9f,
                role,
                new Vec3(1.0, 0.0, 0.0),
                2.0,
                1.0,
                pos,
                0.0, 4.0, 0.0, 4.0,
                Set.of(new PixelPos(pos, Direction.NORTH, id, id, 0xFF00AA00))
        );
    }

    private record DeferredShapeParameter(PositionedGlyph boundary, GroupNode source)
            implements CompiledOp {
        @Override
        public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath("gyromancy", "deferred_shape_parameter");
        }

        @Override
        public List<OpInput> inputs() {
            return List.of();
        }

        @Override
        public int color() {
            return 0;
        }

    }
}
