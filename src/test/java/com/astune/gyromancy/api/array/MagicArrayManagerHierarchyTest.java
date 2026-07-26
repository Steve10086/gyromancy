package com.astune.gyromancy.api.array;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.ArrayAstBuilder;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.compile.operator.CompiledOp;
import com.mojang.serialization.JsonOps;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicArrayManagerHierarchyTest {
    @Test
    void circleClaimsOnlyUnparentedDirectNodes() {
        MagicArrayManager manager = new MagicArrayManager();
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 1, 2.0, 3.0);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 2, 3.0, 3.0);
        PositionedGlyph inner = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 3, 1.0, 4.0);
        PositionedGlyph outer = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 4, 0.0, 5.0);

        manager.registerGlyph(fire);
        manager.registerGlyph(arrow);
        manager.registerGlyph(inner);
        manager.registerGlyph(outer);

        assertEquals(inner, manager.parentCircle(fire));
        assertEquals(inner, manager.parentCircle(arrow));
        assertEquals(outer, manager.parentCircle(inner));
        assertEquals(List.of(fire, arrow), manager.directChildren(inner));
        assertEquals(List.of(inner), manager.directChildren(outer));

        manager.unregisterGlyph(inner.glyphUuid());

        assertNull(manager.parentCircle(fire));
        assertNull(manager.parentCircle(arrow));
        assertEquals(List.of(), manager.directChildren(outer));
    }

    @Test
    void managerAcceptsRegisteredOpDefinitions() {
        OpDefinition first = effect("first", "fire");
        OpDefinition second = effect("second", "arrow");

        MagicArrayManager manager = new MagicArrayManager(List.of(first, second));

        assertEquals(List.of(first, second), manager.opDefinitions());
    }

    @Test
    void codecPreservesExplicitParentChildOwnership() {
        MagicArrayManager manager = new MagicArrayManager();
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 1, 2.0, 3.0);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 2, 3.0, 3.5);
        PositionedGlyph inner = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 3, 1.0, 4.0);
        PositionedGlyph outer = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 4, 0.0, 5.0);
        manager.registerGlyph(fire);
        manager.registerGlyph(arrow);
        manager.registerGlyph(inner);
        manager.registerGlyph(outer);
        manager.registerArrayObj(new ArrayObject(
                UUID.fromString("00000000-0000-0000-0000-000000000010"),
                outer,
                List.of(outer, inner, fire, arrow),
                Map.of()));

        var encoded = MagicArrayManager.CODEC.encodeStart(JsonOps.INSTANCE, manager).getOrThrow();
        assertTrue(encoded.getAsJsonObject().has("parent_relations"));
        MagicArrayManager decoded = MagicArrayManager.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        PositionedGlyph decodedFire = decoded.getGlyph(fire.glyphUuid());
        PositionedGlyph decodedArrow = decoded.getGlyph(arrow.glyphUuid());
        PositionedGlyph decodedInner = decoded.getGlyph(inner.glyphUuid());
        PositionedGlyph decodedOuter = decoded.getGlyph(outer.glyphUuid());
        assertEquals(decodedInner, decoded.parentCircle(decodedFire));
        assertEquals(decodedInner, decoded.parentCircle(decodedArrow));
        assertEquals(decodedOuter, decoded.parentCircle(decodedInner));
        assertEquals(List.of(decodedFire, decodedArrow), decoded.directChildren(decodedInner));
        assertEquals(List.of(decodedInner), decoded.directChildren(decodedOuter));
    }

    @Test
    void circleCollectionFiltersGlyphsWithInvalidStrokes() {
        MagicArrayManager manager = new MagicArrayManager();
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 1, 2.0, 3.0);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 2, 3.0, 3.5);
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 3, 1.0, 4.0);
        manager.registerGlyph(fire);
        manager.registerGlyph(arrow);
        manager.registerGlyph(circle);

        var ast = ArrayAstBuilder.build(circle, manager,
                glyph -> !glyph.glyphUuid().equals(arrow.glyphUuid()));

        assertEquals(List.of(circle, fire), ArrayAstBuilder.boundGlyphs(ast));
    }

    @Test
    void legacyArrayListPersistenceStillRestoresHierarchy() {
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 1, 2.0, 3.0);
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 2, 1.0, 4.0);
        ArrayObject array = new ArrayObject(
                UUID.fromString("00000000-0000-0000-0000-000000000010"),
                circle,
                List.of(circle, fire),
                Map.of());
        var legacyData = ArrayObject.CODEC.listOf()
                .encodeStart(JsonOps.INSTANCE, List.of(array))
                .getOrThrow();

        MagicArrayManager decoded =
                MagicArrayManager.CODEC.parse(JsonOps.INSTANCE, legacyData).getOrThrow();

        assertEquals(decoded.getGlyph(circle.glyphUuid()),
                decoded.parentCircle(decoded.getGlyph(fire.glyphUuid())));
        assertEquals(array.arrayId(),
                decoded.getArrayForGlyph(fire.glyphUuid()).arrayId());
    }

    private static OpDefinition effect(String id, String symbol) {
        return new OpDefinition() {
            @Override
            public ResourceLocation id() {
                return ResourceLocation.fromNamespaceAndPath("gyromancy", id);
            }

            @Override
            public List<OpInputMatcher> match() {
                return List.of(OpInputMatcher.rune(symbol));
            }

            @Override
            public CompileResult<CompiledOp> compile(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                                   List<OpInput> inputs) {
                return new CompileResult.Success<>(new CompiledOp() {
                    @Override public ResourceLocation id() { return ResourceLocation.fromNamespaceAndPath("gyromancy", id); }
                    @Override public PositionedGlyph boundary() { return boundary; }
                    @Override public List<OpInput> inputs() { return inputs; }
                    @Override public int color() { return 0xFFFFFFFF; }
                });
            }
        };
    }

    private static PositionedGlyph glyph(String name, SymbolRole role, int id, double min, double max) {
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
