package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.array.ArrayObject;
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
import com.astune.gyromancy.array.runtime.emit.EmittedObject;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.canvas.CanvasEntity;
import com.astune.gyromancy.registry.ModAttachments;
import com.astune.gyromancy.symbol.SymbolCatalog;
import com.astune.gyromancy.entity.projection.ProjectionCanvasEntity;
import com.astune.gyromancy.entity.projection.WandProjectionService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.lang.Math.max;

/** Projects the glyphs enclosed by a canvas outer circle onto a fixed parallel plane. */
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
                    OpInputMatcher.boundary(SymbolRole.OUTER_CIRCLE),
                    OpInputMatcher.rawGroup(SymbolRole.OUTER_CIRCLE));
        }

        @Override
        public CompileResult<CompiledOp> compile(PositionedGlyph boundary,
                                                  List<OpInput> matchedInputs,
                                                  List<OpInput> inputs) {
            if (findSourceCircle(inputs).isEmpty()) {
                return new CompileResult.Failure<>(List.of(
                        new com.astune.gyromancy.array.compile.CompileDiagnostic(
                                "missing_projection_source",
                                "Projection requires a nested outer circle on a canvas")));
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
        Optional<SourceCircle> sourceInput = findSourceCircle(inputs);
        if (sourceInput.isEmpty()) {
            Gyromancy.LOGGER.warn(
                    "[Projection] Root glyph #{} has no outer-circle source input",
                    boundary.glyphId());
            return new RuntimeHandle(Map.of());
        }
        Optional<SourceCircle> source = sourceInput
                .map(circle -> new SourceCircle(context.liveGlyph(circle.circle()), null))
                .flatMap(circle -> resolveSource(level, circle));
        if (source.isEmpty()) {
            SourceCircle sourceCircle = sourceInput.get();
            String canvasDescription = sourceCircle.circle().sourceCanvasId()
                    .map(id -> {
                        var entity = level.getEntity(id);
                        return entity == null ? id + " (not loaded)"
                                : id + " (" + entity.getClass().getSimpleName() + ")";
                    })
                    .orElse("missing source canvas id");
            Gyromancy.LOGGER.warn(
                    "[Projection] Source outer circle #{} has no live source canvas: {}",
                    sourceCircle.circle().glyphId(), canvasDescription);
            return new RuntimeHandle(Map.of());
        }

        SourceCircle sourceCircle = source.get();
        MagicArrayManager manager = level.getData(ModAttachments.ARRAY_MANAGER);
        Set<UUID> selected = manager.getGlyphsForCanvas(sourceCircle.canvas().getUUID()).stream()
                .filter(glyph -> !glyph.glyphUuid().equals(sourceCircle.circle().glyphUuid()))
                .filter(glyph -> inside(sourceCircle.circle(), glyph))
                .map(PositionedGlyph::glyphUuid)
                .collect(Collectors.toSet());
        if (selected.isEmpty()) {
            Gyromancy.LOGGER.warn(
                    "[Projection] Source outer circle #{} contains no glyphs on canvas {}",
                    sourceCircle.circle().glyphId(), sourceCircle.canvas().getId());
            return new RuntimeHandle(Map.of());
        }

        SurfaceFrame sourceFrame = sourceCircle.canvas().surfaceFrame();
        CanvasDocument document = WandProjectionService.copySelectedGlyphsForProjection(
                sourceCircle.canvas().document(), selected,
                sourceCircle.circle(), sourceFrame);
        Vec3 offset = projectionOffset(context);
        SurfaceFrame targetFrame = new SurfaceFrame(
                sourceFrame.origin().add(offset),
                sourceFrame.axisU(), sourceFrame.axisV(), sourceFrame.normal());
        ProjectionCanvasEntity projection = ProjectionCanvasEntity.createFixed(
                level, targetFrame, document);

        EmitResult result = new EmitResult();
        if (!level.addFreshEntity(projection)) {
            Gyromancy.LOGGER.warn(
                    "[Projection] Failed to add fixed projection for root glyph #{}",
                    boundary.glyphId());
            return new RuntimeHandle(Map.of());
        }
        // The entity can be added from inside another canvas' onAdded callback,
        // before the tracker emits StartTracking. Mirror the wand placement
        // path so an already tracking client receives the raster immediately.
        projection.broadcastSnapshot();
        result.add(new EmittedObject(ID, ArrayObject.EntityRef.of(projection)));
        return result.toRuntimeHandle();
    }

    @Override
    public void deactivate(OpRuntimeContext context, Map<String, Object> scratchData) {
        // EmitResult ownership discards the fixed projection before this hook.
    }

    private Vec3 projectionOffset(OpRuntimeContext context) {
        PositionedGlyph liveBoundary = context.liveGlyph(boundary);
        Vec3 normal = liveBoundary.surface().normal();
        Vec3 offset = normal.normalize().scale(2.0 - (double) 1 /16);
        double arrowSizeSum = 0.0;
        for (OpInput input : inputs) {
            if (!(input instanceof OpInput.Rune rune)) continue;
            PositionedGlyph glyph = context.liveGlyph(rune.glyph());
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
                    offset = offset.add(normal.scale(size));
                }
                default -> {
                }
            }
        }
        double speed = max(0, offset.length() - 2);
        offset = offset.add(normal.scale(
                (arrowSizeSum - speed) + 0.2 * speed));
        return offset;
    }

    private static float arrayScale(PositionedGlyph circle) {
        double area = max(0.0, circle.length() * circle.width());
        return (float) max(0.1F, Math.sqrt(area) * 0.5);
    }

    private static boolean inside(PositionedGlyph outer, PositionedGlyph glyph) {
        SurfaceFrame.SurfaceBounds bounds = glyph.boundsOn(outer.surface());
        if (glyph.role() == SymbolRole.OUTER_CIRCLE) {
            return bounds.minU() >= outer.minWorldX()
                    && bounds.maxU() <= outer.maxWorldX()
                    && bounds.minV() >= outer.minWorldY()
                    && bounds.maxV() <= outer.maxWorldY();
        }
        double u = bounds.centerU();
        double v = bounds.centerV();
        return u >= outer.minWorldX() && u <= outer.maxWorldX()
                && v >= outer.minWorldY() && v <= outer.maxWorldY();
    }

    private static Optional<SourceCircle> findSourceCircle(List<OpInput> inputs) {
        for (OpInput input : inputs) {
            PositionedGlyph circle;
            if (input instanceof OpInput.Op op) {
                circle = op.operator().boundary();
            } else if (input instanceof OpInput.RawGroup raw) {
                circle = raw.boundary();
            } else {
                continue;
            }
            if (circle != null && circle.role() == SymbolRole.OUTER_CIRCLE) {
                return Optional.of(new SourceCircle(circle, null));
            }
        }
        return Optional.empty();
    }

    private static Optional<SourceCircle> resolveSource(ServerLevel level, SourceCircle source) {
        return source.circle().sourceCanvasId()
                .flatMap(id -> level.getEntity(id) instanceof CanvasEntity canvas
                        ? Optional.of(new SourceCircle(source.circle(), canvas))
                        : Optional.empty());
    }

    private record SourceCircle(PositionedGlyph circle, CanvasEntity canvas) {}
}
