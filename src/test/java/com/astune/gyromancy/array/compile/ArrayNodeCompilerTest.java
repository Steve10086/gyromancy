package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.compile.operator.FireProjectileOp;
import com.astune.gyromancy.compile.operator.ElementOp;
import com.astune.gyromancy.compile.operator.CompiledOp;
import com.astune.gyromancy.compile.operator.EntityEffectOp;
import com.astune.gyromancy.compile.operator.EntityPayload;
import com.astune.gyromancy.compile.operator.MomentumOp;
import com.astune.gyromancy.compile.operator.PersistentOp;
import com.astune.gyromancy.compile.operator.RotationOp;
import com.astune.gyromancy.compile.operator.SplitEmitOp;
import com.astune.gyromancy.compile.operator.WaterProjectileOp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ArrayNodeCompilerTest {
    @Test
    void compilesProjectileOperatorFromRawRuneInputs() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 2);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 3);
        PositionedGlyph revert = glyph("revert", SymbolRole.PARAMETER_RUNE, 4);
        GroupNode ast = group(circle, new SymbolNode(fire), new SymbolNode(arrow), new SymbolNode(revert));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        EntityEffectOp root = assertInstanceOf(FireProjectileOp.class, success.value().root());

        assertEquals(ElementType.FIRE, root.primaryElement());
        assertEquals(true, root.attributes().inverted());
        assertEquals(List.of(new MotionAttribute(arrow.front(), arrow.length())), root.attributes().motion());
        assertEquals(List.of("fire", "arrow", "revert"), runeNames(root.inputs()));
        assertEquals(List.of("fire"), runeNames(root.matchedInputs()));
        assertEquals(List.of(circle, fire, arrow, revert), success.value().boundGlyphs());
    }

    @Test
    void primaryRuneDoesNotNeedCenterRole() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph fire = glyph("fire", SymbolRole.PARAMETER_RUNE, 2);
        GroupNode ast = group(circle, new SymbolNode(fire));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));

        assertInstanceOf(FireProjectileOp.class, success.value().root());
        assertEquals(List.of("fire"), runeNames(success.value().root().inputs()));
    }

    @Test
    void emptyCircleFailsWithoutOperator() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        GroupNode ast = group(circle);

        @SuppressWarnings("unchecked")
        var failure = (CompileResult.Failure<CompiledArray>) assertInstanceOf(CompileResult.Failure.class,
                ArrayNodeCompiler.compile(ast));

        assertEquals("missing_primary_element", failure.diagnostics().getFirst().code());
    }

    @Test
    void arrowOnlyCircleCompilesMomentumOperator() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 2);
        GroupNode ast = group(circle, new SymbolNode(arrow));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));

        assertInstanceOf(MomentumOp.class, success.value().root());
    }

    @Test
    void arrowUpOnlyCircleCompilesMomentumOperator() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph arrowUp = glyph("arrow_up", SymbolRole.PARAMETER_RUNE, 2);
        GroupNode ast = group(circle, new SymbolNode(arrowUp));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));

        assertInstanceOf(MomentumOp.class, success.value().root());
    }

    @Test
    void momentumReadsEveryDirectArrowVariant() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 2);
        PositionedGlyph arrowUp = glyph("arrow_up", SymbolRole.PARAMETER_RUNE, 3);
        GroupNode ast = group(circle, new SymbolNode(arrow), new SymbolNode(arrowUp));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        MomentumOp momentum = assertInstanceOf(MomentumOp.class, success.value().root());

        assertEquals(2, momentum.accelerationInputs().size());
        assertEquals(false, momentum.accelerationInputs().get(0).alongFacing());
        assertEquals(true, momentum.accelerationInputs().get(1).alongFacing());
    }

    @Test
    void momentumRecognitionDoesNotClaimProjectileInputs() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph water = glyph("water", SymbolRole.CENTER_SYMBOL, 2);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 3);
        GroupNode ast = group(circle, new SymbolNode(water), new SymbolNode(arrow));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));

        assertInstanceOf(WaterProjectileOp.class, success.value().root());
    }

    @Test
    void drainCompilesRotationSpeedFromRuneLength() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph drain = glyph("drain", SymbolRole.PARAMETER_RUNE, 3);
        GroupNode ast = group(circle, new SymbolNode(drain));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        RotationOp rotation = assertInstanceOf(RotationOp.class, success.value().root());

        assertEquals(drain.length() * RotationOp.ROTATION_SPEED_SCALE, rotation.rotationSpeed());
    }

    @Test
    void revertReversesDrainRotationSpeed() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph drain = glyph("drain", SymbolRole.PARAMETER_RUNE, 3);
        PositionedGlyph revert = glyph("revert", SymbolRole.PARAMETER_RUNE, 4);
        GroupNode ast = group(circle, new SymbolNode(drain), new SymbolNode(revert));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        RotationOp rotation = assertInstanceOf(RotationOp.class, success.value().root());

        assertEquals(-drain.length() * RotationOp.ROTATION_SPEED_SCALE, rotation.rotationSpeed());
    }

    @Test
    void projectileCanOwnNestedRotationPayload() {
        PositionedGlyph outer = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph inner = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 2);
        PositionedGlyph water = glyph("water", SymbolRole.CENTER_SYMBOL, 3);
        PositionedGlyph drain = glyph("drain", SymbolRole.PARAMETER_RUNE, 4);
        PositionedGlyph revert = glyph("revert", SymbolRole.PARAMETER_RUNE, 5);
        GroupNode ast = group(outer, new SymbolNode(water),
                group(inner, new SymbolNode(drain), new SymbolNode(revert)));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        WaterProjectileOp root = assertInstanceOf(WaterProjectileOp.class, success.value().root());
        RotationOp child = assertInstanceOf(RotationOp.class,
                assertInstanceOf(OpInput.Op.class, root.inputs().get(1)).operator());
        List<EntityPayload> payloads = new ArrayList<>();

        child.contributeEntityPayloads(payloads);

        RotationOp payload = assertInstanceOf(RotationOp.class, payloads.getFirst());
        assertEquals(-drain.length() * RotationOp.ROTATION_SPEED_SCALE, payload.rotationSpeed());
    }

    @Test
    void directProjectileMomentumDoesNotBecomeAccelerationPayload() {
        PositionedGlyph outer = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph inner = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 2);
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 3);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 4);
        PositionedGlyph arrowUp = glyph("arrow_up", SymbolRole.PARAMETER_RUNE, 5);
        GroupNode ast = group(outer, new SymbolNode(fire),
                group(inner, new SymbolNode(arrow), new SymbolNode(arrowUp)));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        FireProjectileOp root = assertInstanceOf(FireProjectileOp.class, success.value().root());
        MomentumOp child = assertInstanceOf(MomentumOp.class,
                assertInstanceOf(OpInput.Op.class, root.inputs().get(1)).operator());
        List<EntityPayload> payloads = new ArrayList<>();

        child.contributeEntityPayloads(payloads);

        assertEquals(List.of(), payloads);
    }

    @Test
    void onlyMomentumNestedUnderMomentumBecomesAccelerationPayload() {
        PositionedGlyph outer = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph momentumBoundary = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 2);
        PositionedGlyph accelerationBoundary = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 3);
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 4);
        PositionedGlyph launchArrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 5);
        PositionedGlyph accelerationArrow = glyph("arrow_up", SymbolRole.PARAMETER_RUNE, 6);
        GroupNode ast = group(outer, new SymbolNode(fire),
                group(momentumBoundary, new SymbolNode(launchArrow),
                        group(accelerationBoundary, new SymbolNode(accelerationArrow))));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        FireProjectileOp root = assertInstanceOf(FireProjectileOp.class, success.value().root());
        MomentumOp launchMomentum = assertInstanceOf(MomentumOp.class,
                assertInstanceOf(OpInput.Op.class, root.inputs().get(1)).operator());
        MomentumOp accelerationMomentum = assertInstanceOf(MomentumOp.class,
                assertInstanceOf(OpInput.Op.class, launchMomentum.inputs().get(1)).operator());
        List<EntityPayload> payloads = new ArrayList<>();

        launchMomentum.contributeEntityPayloads(payloads);

        MomentumOp payload = assertInstanceOf(MomentumOp.class, payloads.getFirst());
        assertEquals(1, payloads.size());
        assertEquals(accelerationMomentum.accelerationInputs(), payload.accelerationInputs());
        assertEquals(true, payload.accelerationInputs().getFirst().alongFacing());
    }

    @Test
    void momentumCanCompileFromOnlyOneDirectMomentumChild() {
        PositionedGlyph outer = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph inner = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 2);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 3);
        GroupNode ast = group(outer, group(inner, new SymbolNode(arrow)));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        MomentumOp parent = assertInstanceOf(MomentumOp.class, success.value().root());
        MomentumOp child = assertInstanceOf(MomentumOp.class,
                assertInstanceOf(OpInput.Op.class, parent.matchedInputs().getFirst()).operator());
        List<EntityPayload> payloads = new ArrayList<>();

        parent.contributeEntityPayloads(payloads);

        assertEquals(List.of(child), parent.childOps());
        assertEquals(List.of(), parent.accelerationInputs());
        MomentumOp payload = assertInstanceOf(MomentumOp.class, payloads.getFirst());
        assertEquals(1, payloads.size());
        assertEquals(child.accelerationInputs(), payload.accelerationInputs());
    }

    @Test
    void unacceptedExtraPrimaryRuneRejectsAllCandidates() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 2);
        PositionedGlyph water = glyph("water", SymbolRole.CENTER_SYMBOL, 3);
        GroupNode ast = group(circle, new SymbolNode(fire), new SymbolNode(water));

        @SuppressWarnings("unchecked")
        var failure = (CompileResult.Failure<CompiledArray>) assertInstanceOf(CompileResult.Failure.class,
                ArrayNodeCompiler.compile(ast));

        assertEquals("missing_primary_element", failure.diagnostics().getFirst().code());
    }

    @Test
    void longestMatchingDefinitionReceivesMatchedInputsAndFullInputs() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 2);
        PositionedGlyph fix = glyph("fix", SymbolRole.PARAMETER_RUNE, 3);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 4);
        OpDefinition shortOp = runeOpAccepting("short", List.of("fix", "arrow"), "fire");
        OpDefinition longOp = runeOpAccepting("long", List.of("arrow"), "fire", "fix");
        GroupNode ast = group(circle, new SymbolNode(fire), new SymbolNode(fix), new SymbolNode(arrow));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast, List.of(shortOp, longOp)));
        DummyOperator root = assertInstanceOf(DummyOperator.class, success.value().root());

        assertEquals("long", root.id().getPath());
        assertEquals(List.of("fire", "fix"), runeNames(root.matchedInputs()));
        assertEquals(List.of("fire", "fix", "arrow"), runeNames(root.inputs()));
    }

    @Test
    void partialLongerDefinitionDoesNotBeatFullShortDefinition() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 2);
        OpDefinition shortOp = runeOp("short", "fire");
        OpDefinition partialLongOp = runeOp("partial_long", "fire", "fix");
        GroupNode ast = group(circle, new SymbolNode(fire));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast, List.of(partialLongOp, shortOp)));
        DummyOperator root = assertInstanceOf(DummyOperator.class, success.value().root());

        assertEquals("short", root.id().getPath());
        assertEquals(List.of("fire"), runeNames(root.matchedInputs()));
    }

    @Test
    void nestedGroupCompilesToChildOpAndParentCanMatchIt() {
        PositionedGlyph outer = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph inner = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 2);
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 3);
        PositionedGlyph water = glyph("water", SymbolRole.CENTER_SYMBOL, 4);
        OpDefinition innerOp = runeOp("inner", "water");
        OpDefinition outerOp = runeAndChildOp("outer", "fire", DummyOperator.class);
        GroupNode ast = group(outer, new SymbolNode(fire), group(inner, new SymbolNode(water)));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast, List.of(innerOp, outerOp)));
        DummyOperator root = assertInstanceOf(DummyOperator.class, success.value().root());

        assertEquals("outer", root.id().getPath());
        assertEquals(2, root.matchedInputs().size());
    }

    @Test
    void engagingRuneCompilesStandaloneElementOp() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph engaging = glyph("engaging", SymbolRole.PARAMETER_RUNE, 2);
        GroupNode ast = group(circle, new SymbolNode(engaging));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));

        assertInstanceOf(ElementOp.class, success.value().root());
    }

    @Test
    void elementOpDefaultsToManaAbsorptionWithoutContentElement() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph engaging = glyph("engaging", SymbolRole.PARAMETER_RUNE, 2);
        GroupNode ast = group(circle, new SymbolNode(engaging));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        ElementOp root = assertInstanceOf(ElementOp.class, success.value().root());

        assertEquals(ElementType.MANA, root.absorbedElement());
        List<EntityPayload> payloads = new ArrayList<>();
        root.contributeEntityPayloads(payloads);
        EntityPayload payload = payloads.getFirst();
        assertEquals(ElementType.MANA, assertInstanceOf(ElementOp.class, payload).absorbedElement());
    }

    @Test
    void elementOpUsesContentElementForPayloadAbsorption() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph engaging = glyph("engaging", SymbolRole.PARAMETER_RUNE, 2);
        PositionedGlyph fire = glyph("fire", SymbolRole.PARAMETER_RUNE, 3);
        GroupNode ast = group(circle, new SymbolNode(engaging), new SymbolNode(fire));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        ElementOp root = assertInstanceOf(ElementOp.class, success.value().root());

        assertEquals(ElementType.FIRE, root.absorbedElement());
        List<EntityPayload> payloads = new ArrayList<>();
        root.contributeEntityPayloads(payloads);
        EntityPayload payload = payloads.getFirst();
        assertEquals(ElementType.FIRE, assertInstanceOf(ElementOp.class, payload).absorbedElement());
    }

    @Test
    void defaultCompilerUsesDistributedRegistryDefinitions() {
        OpDefinition registered = runeOp("registered_star", "star");
        OpDefinitionRegistry.register(registered);
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph star = glyph("star", SymbolRole.PARAMETER_RUNE, 2);
        GroupNode ast = group(circle, new SymbolNode(star));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        DummyOperator root = assertInstanceOf(DummyOperator.class, success.value().root());

        assertEquals("registered_star", root.id().getPath());
    }

    @Test
    void projectileCanOwnNestedElementPayload() {
        PositionedGlyph outer = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph inner = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 2);
        PositionedGlyph water = glyph("water", SymbolRole.CENTER_SYMBOL, 3);
        PositionedGlyph engaging = glyph("engaging", SymbolRole.PARAMETER_RUNE, 4);
        GroupNode ast = group(outer, new SymbolNode(water), group(inner, new SymbolNode(engaging)));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        WaterProjectileOp root = assertInstanceOf(WaterProjectileOp.class, success.value().root());
        CompiledOp child = assertInstanceOf(OpInput.Op.class, root.inputs().get(1)).operator();

        assertInstanceOf(ElementOp.class, child);
    }

    @Test
    void splitEmitCompilesButIsNotPersistentRoot() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph split = glyph("split", SymbolRole.PARAMETER_RUNE, 2);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 3);
        GroupNode ast = group(circle, new SymbolNode(split), new SymbolNode(arrow));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));

        assertInstanceOf(SplitEmitOp.class, success.value().root());
        assertEquals(false, success.value().root() instanceof PersistentOp);
    }

    @Test
    void splitEmitPlansOneEmissionPerDirectArrow() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph split = glyph("split", SymbolRole.PARAMETER_RUNE, 2);
        PositionedGlyph arrowA = glyph("arrow", SymbolRole.PARAMETER_RUNE, 3);
        PositionedGlyph arrowB = glyph("arrow", SymbolRole.PARAMETER_RUNE, 4);
        GroupNode ast = group(circle, new SymbolNode(split), new SymbolNode(arrowA), new SymbolNode(arrowB));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        SplitEmitOp splitOp = assertInstanceOf(SplitEmitOp.class, success.value().root());

        assertEquals(2, splitOp.emissions().size());
        assertEquals(0.5F, splitOp.emissions().getFirst().sizeScale());
        assertEquals(new Vec3(2.0, 0.0, 0.0), splitOp.emissions().getFirst().velocity());
    }

    @Test
    void splitEmitTreatsArrowUpAsNormalLockedArrow() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph split = glyph("split", SymbolRole.PARAMETER_RUNE, 2);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 3);
        PositionedGlyph arrowUp = glyph("arrow_up", SymbolRole.PARAMETER_RUNE, 4);
        GroupNode ast = group(circle, new SymbolNode(split), new SymbolNode(arrow), new SymbolNode(arrowUp));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        SplitEmitOp splitOp = assertInstanceOf(SplitEmitOp.class, success.value().root());

        assertEquals(2, splitOp.emissions().size());
        assertEquals(new Vec3(2.0, 0.0, 0.0), splitOp.emissions().get(0).velocity());
        assertEquals(new Vec3(0.0, 0.0, -2.0), splitOp.emissions().get(1).velocity());
        assertEquals(0.5F, splitOp.emissions().get(1).sizeScale());
    }

    @Test
    void splitEmitTreatsDirectMomentumAsOneEmissionSource() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph momentumBoundary = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 2);
        PositionedGlyph split = glyph("split", SymbolRole.PARAMETER_RUNE, 3);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 4);
        GroupNode ast = group(circle, new SymbolNode(split),
                group(momentumBoundary, new SymbolNode(arrow)));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        SplitEmitOp splitOp = assertInstanceOf(SplitEmitOp.class, success.value().root());

        assertEquals(1, splitOp.emissions().size());
        assertEquals(new Vec3(0.2, 0.0, 0.0), splitOp.emissions().getFirst().velocity());
        assertEquals(0.2, splitOp.emissions().getFirst().motionSum());
        assertEquals(1.0F, splitOp.emissions().getFirst().sizeScale());
        assertEquals(true, splitOp.emissions().getFirst().hasMotion());
    }

    @Test
    void splitEmitCreatesSeparateEmissionsForArrowAndMomentum() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph momentumBoundary = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 2);
        PositionedGlyph split = glyph("split", SymbolRole.PARAMETER_RUNE, 3);
        PositionedGlyph directArrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 4);
        PositionedGlyph momentumArrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 5);
        GroupNode ast = group(circle, new SymbolNode(split), new SymbolNode(directArrow),
                group(momentumBoundary, new SymbolNode(momentumArrow)));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        SplitEmitOp splitOp = assertInstanceOf(SplitEmitOp.class, success.value().root());

        assertEquals(2, splitOp.emissions().size());
        assertEquals(new Vec3(2.0, 0.0, 0.0), splitOp.emissions().get(0).velocity());
        assertEquals(new Vec3(0.2, 0.0, 0.0), splitOp.emissions().get(1).velocity());
        assertEquals(0.5F, splitOp.emissions().get(0).sizeScale());
        assertEquals(0.5F, splitOp.emissions().get(1).sizeScale());
    }

    @Test
    void projectileCanOwnNestedSplitEmitOpWithoutChangingRootType() {
        PositionedGlyph outer = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph inner = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 2);
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 3);
        PositionedGlyph split = glyph("split", SymbolRole.PARAMETER_RUNE, 4);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 5);
        GroupNode ast = group(outer, new SymbolNode(fire), group(inner, new SymbolNode(split), new SymbolNode(arrow)));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        FireProjectileOp root = assertInstanceOf(FireProjectileOp.class, success.value().root());
        CompiledOp child = assertInstanceOf(OpInput.Op.class, root.inputs().get(1)).operator();

        assertInstanceOf(SplitEmitOp.class, child);
    }

    private static GroupNode group(PositionedGlyph circle, ArrayNode... children) {
        return new GroupNode(circle, new SequenceNode(List.of(children)));
    }

    private static List<String> runeNames(List<OpInput> inputs) {
        return inputs.stream()
                .filter(OpInput.Rune.class::isInstance)
                .map(OpInput.Rune.class::cast)
                .map(OpInput.Rune::symbolName)
                .toList();
    }

    private static OpDefinition runeOp(String id, String... symbols) {
        return runeOpAccepting(id, List.of(), symbols);
    }

    private static OpDefinition runeOpAccepting(String id, List<String> accepted, String... symbols) {
        List<OpInputMatcher> matchers = List.of(symbols).stream()
                .map(OpInputMatcher::rune)
                .toList();
        List<OpInputMatcher> acceptedMatchers = accepted.stream()
                .map(OpInputMatcher::rune)
                .toList();
        return op(id, matchers, acceptedMatchers);
    }

    @SafeVarargs
    private static OpDefinition runeAndChildOp(String id, String firstSymbol,
                                                        Class<? extends CompiledOp>... opTypes) {
        List<OpInputMatcher> matchers = new java.util.ArrayList<>();
        matchers.add(OpInputMatcher.rune(firstSymbol));
        for (Class<? extends CompiledOp> opType : opTypes) matchers.add(OpInputMatcher.op(opType));
        return op(id, List.copyOf(matchers));
    }

    private static OpDefinition op(String id, List<OpInputMatcher> matchers) {
        return op(id, matchers, List.of());
    }

    private static OpDefinition op(String id, List<OpInputMatcher> matchers,
                                   List<OpInputMatcher> acceptedMatchers) {
        return new OpDefinition() {
            @Override
            public ResourceLocation id() {
                return ResourceLocation.fromNamespaceAndPath("gyromancy", id);
            }

            @Override
            public List<OpInputMatcher> match() {
                return matchers;
            }

            @Override
            public List<OpInputMatcher> accepted() {
                return acceptedMatchers;
            }

            @Override
            public CompileResult<CompiledOp> compile(PositionedGlyph boundary, List<OpInput> matchedInputs,
                                                   List<OpInput> inputs) {
                return new CompileResult.Success<>(new DummyOperator(id, boundary, matchedInputs, inputs));
            }
        };
    }

    private record DummyOperator(String name, PositionedGlyph boundary, List<OpInput> matchedInputs,
                                 List<OpInput> inputs) implements CompiledOp {
        @Override
        public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath("gyromancy", name);
        }

        @Override
        public int color() {
            return 0xFFFFFFFF;
        }
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
}
