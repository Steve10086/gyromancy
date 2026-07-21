package com.astune.gyromancy.api.array;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.ArrayEffectDefinition;
import com.astune.gyromancy.array.compile.CompiledArrayNode;
import com.astune.gyromancy.array.compile.EffectAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void managerRejectsOverlappingEffectSymbols() {
        ArrayEffectDefinition first = effect("first", "fire");
        ArrayEffectDefinition second = effect("second", "fire");

        assertThrows(IllegalStateException.class, () -> new MagicArrayManager(List.of(first, second)));
    }

    private static ArrayEffectDefinition effect(String id, String symbol) {
        return new ArrayEffectDefinition() {
            @Override
            public ResourceLocation id() {
                return ResourceLocation.fromNamespaceAndPath("gyromancy", id);
            }

            @Override
            public List<String> symbols() {
                return List.of(symbol);
            }

            @Override
            public com.astune.gyromancy.api.element.ElementType primaryElement(String symbol) {
                return com.astune.gyromancy.api.element.ElementType.FIRE;
            }

            @Override
            public CompiledArrayNode compile(PositionedGlyph boundary,
                                             com.astune.gyromancy.api.element.ElementType primaryElement,
                                             EffectAttributes attributes,
                                             List<CompiledArrayNode> children) {
                return null;
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
