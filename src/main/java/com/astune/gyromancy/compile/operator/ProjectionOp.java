package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.array.MagicArrayManager;
import com.astune.gyromancy.api.geometry.SurfaceFrame;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.RegisteredOp;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.array.runtime.RuntimeHandle;
import com.astune.gyromancy.array.runtime.emit.EmitResult;
import com.astune.gyromancy.array.runtime.emit.EntityEmitter;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.registry.ModAttachments;
import com.astune.gyromancy.symbol.SymbolCatalog;
import com.astune.gyromancy.wand.WandProjectionCanvasEntity;
import com.astune.gyromancy.wand.WandProjectionService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Projects the glyphs enclosed by a wand plane onto a fixed parallel plane. */
@RegisteredOp
public final class ProjectionOp implements CompiledOp, PersistentOp {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "projection");

    public static final OpDefinition DEFINITION = new OpDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("star"), OpInputMatcher.rune("split"));
        }

        @Override
        public List<OpInputMatcher> accepted() {
            return List.of(
                    OpInputMatcher.rune("arrow"),
                    OpInputMatcher.rune("arrow_up"),
                    OpInputMatcher.boundary(SymbolRole.OUTER_CIRCLE));
        }

        @Override
        public CompileResult<CompiledOp> compile(PositionedGlyph boundary,
                                                  List<OpInput> matchedInputs,
                                                  List<OpInput> inputs) {
            if (findSourceCircle(inputs).isEmpty()) {
                return new CompileResult.Failure<>(List.of(
                        new com.astune.gyromancy.array.compile.CompileDiagnostic(
                                "missing_projection_source",
                                "Projection requires a nested outer circle on a wand projection")));
            }
            return new CompileResult.Success<>(new ProjectionOp(boundary, matchedInputs, inputs));
        }
    };

    private final PositionedGlyph boundary;
    private final List<OpInput> inputs;
    private final List<OpInput> matchedInputs;

    private ProjectionOp(PositionedGlyph boundary, List<OpInput> matchedInputs,
                         List<OpInput> inputs) {
        this.boundary = boundary;
        this.matchedInputs = List.copyOf(matchedInputs);
        this.inputs = List.copyOf(inputs);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public PositionedGlyph boundary() {
        return boundary;
    }

    @Override
    public List<OpInput> inputs() {
        return inputs;
    }

    public List<OpInput> matchedInputs() {
        return matchedInputs;
    }

    @Override
    public int color() {
        return SymbolCatalog.glyphColorFor(ResourceLocation.fromNamespaceAndPath(
                Gyromancy.MODID, "star"));
    }

    @Override
    public RuntimeHandle activate(OpRuntimeContext context) {
        ServerLevel level = context.level();
        Optional<SourceCircle> source = findSourceCircle(inputs).flatMap(
                circle -> resolveSource(level, circle));
        if (source.isEmpty()) return new RuntimeHandle(Map.of());

        SourceCircle sourceCircle = source.get();
        MagicArrayManager manager = level.getData(ModAttachments.ARRAY_MANAGER);
        Set<UUID> selected = manager.getGlyphsForCanvas(sourceCircle.canvas().getUUID()).stream()
                .filter(glyph -> !glyph.glyphUuid().equals(sourceCircle.circle().glyphUuid()))
                .filter(glyph -> inside(sourceCircle.circle(), glyph))
                .map(PositionedGlyph::glyphUuid)
                .collect(Collectors.toSet());
        if (selected.isEmpty()) return new RuntimeHandle(Map.of());

        CanvasDocument document = WandProjectionService.copySelectedGlyphsForProjection(
                sourceCircle.canvas().document(), selected);
        Vec3 offset = projectionOffset();
        SurfaceFrame sourceFrame = sourceCircle.canvas().surfaceFrame();
        SurfaceFrame targetFrame = new SurfaceFrame(
                sourceFrame.origin().add(offset),
                sourceFrame.axisU(), sourceFrame.axisV(), sourceFrame.normal());
        WandProjectionCanvasEntity projection = WandProjectionCanvasEntity.createFixed(
                level, targetFrame, document);

        EmitResult result = new EmitResult();
        EntityEmitter.INSTANCE.emit(level, ID, projection, result);
        return result.toRuntimeHandle();
    }

    @Override
    public void deactivate(OpRuntimeContext context, Map<String, Object> scratchData) {
        // EmitResult ownership discards the fixed projection before this hook.
    }

    private Vec3 projectionOffset() {
        Vec3 offset = Vec3.ZERO;
        double arrowSizeSum = 0.0;
        for (OpInput input : inputs) {
            if (!(input instanceof OpInput.Rune rune)) continue;
            PositionedGlyph glyph = rune.glyph();
            double size = glyph.length();
            switch (rune.symbolName()) {
                case "arrow" -> {
                    arrowSizeSum += size;
                    if (glyph.front().lengthSqr() >= 1.0E-8) {
                        offset = offset.add(glyph.front().normalize().scale(size));
                    }
                }
                case "arrow_up" -> {
                    arrowSizeSum += size;
                    offset = offset.add(boundary.surface().normal().scale(size));
                }
                default -> {
                }
            }
        }
        double speed = offset.length();
        offset = offset.add(boundary.surface().normal().scale(
                (arrowSizeSum - speed) + 0.2 * speed));
        return offset.scale(arrayScale(boundary));
    }

    private static float arrayScale(PositionedGlyph circle) {
        double area = Math.max(0.0, circle.length() * circle.width());
        return (float) Math.max(0.1F, Math.sqrt(area) * 0.5);
    }

    private static boolean inside(PositionedGlyph outer, PositionedGlyph glyph) {
        SurfaceFrame.Coordinates coordinates = outer.surface().project(glyph.center());
        return coordinates.u() >= outer.minWorldX()
                && coordinates.u() <= outer.maxWorldX()
                && coordinates.v() >= outer.minWorldY()
                && coordinates.v() <= outer.maxWorldY();
    }

    private static Optional<SourceCircle> findSourceCircle(List<OpInput> inputs) {
        for (OpInput input : inputs) {
            if (!(input instanceof OpInput.Op op)) continue;
            PositionedGlyph circle = op.operator().boundary();
            if (circle != null && circle.role() == SymbolRole.OUTER_CIRCLE) {
                return Optional.of(new SourceCircle(circle, null));
            }
        }
        return Optional.empty();
    }

    private static Optional<SourceCircle> resolveSource(ServerLevel level, SourceCircle source) {
        return source.circle().sourceCanvasId()
                .flatMap(id -> level.getEntity(id) instanceof WandProjectionCanvasEntity canvas
                        ? Optional.of(new SourceCircle(source.circle(), canvas))
                        : Optional.empty());
    }

    private record SourceCircle(PositionedGlyph circle, WandProjectionCanvasEntity canvas) {}
}
