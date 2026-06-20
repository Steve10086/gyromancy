package com.astune.gyromancy.symbol;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.painter.api.CanvasData;
import com.astune.painter.api.CanvasDataHolder;
import com.astune.painter.api.CanvasFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Cross-block flood fill extractor for connected mana pixels.
 *
 * <p>Coordinate system: all world-space mapping uses CanvasFace.corner0-3
 * with dot-product projection. This is the single source of truth for
 * pixel↔world conversion, compatible with partial faces, overlapping
 * faces, and rotated quads.
 */
public final class FloodFillExtractor {

    private static final double EPSILON = 1e-6;

    private FloodFillExtractor() {}

    // ═══════════════════════════════════════════════════════════════
    // Types
    // ═══════════════════════════════════════════════════════════════

    public record ExtractedGlyph(
            Set<PixelPos> pixels,
            double[] worldX, double[] worldY,  // per-pixel 2D coords (same index order as above)
            double minWorldX, double maxWorldX,
            double minWorldY, double maxWorldY,
            int blockCount
    ) {}

    public static class FloodFillState {
        public final Deque<PixelPos> queue;
        public final Set<PixelPos> visited;
        public final Set<BlockPos> involvedBlocks;
        public final Set<PixelPos> initialSeeds;
        public final List<Double> worldXs = new ArrayList<>();
        public final List<Double> worldYs = new ArrayList<>();
        public double minWorldX = Double.MAX_VALUE;
        public double maxWorldX = -Double.MAX_VALUE;
        public double minWorldY = Double.MAX_VALUE;
        public double maxWorldY = -Double.MAX_VALUE;

        public FloodFillState(PixelPos seed) {
            this.queue = new ArrayDeque<>();
            this.queue.add(seed);
            this.visited = new HashSet<>();
            this.involvedBlocks = new HashSet<>();
            this.initialSeeds = new HashSet<>();
            this.initialSeeds.add(seed);
        }

        void resetGeometry() {
            worldXs.clear();
            worldYs.clear();
            minWorldX = Double.MAX_VALUE;
            maxWorldX = -Double.MAX_VALUE;
            minWorldY = Double.MAX_VALUE;
            maxWorldY = -Double.MAX_VALUE;
            involvedBlocks.clear();
        }
    }

    public record ExtractionResult(ExtractedGlyph glyph, FloodFillState continuation) {}

    // ═══════════════════════════════════════════════════════════════
    // Core: pixel ↔ world coordinate mapping
    // ═══════════════════════════════════════════════════════════════

    /**
     * Maps a canvas pixel to its 3D world position.
     * Based on Pigmentum's calculatePixelFromHit, using corner0 + dot-product projection.
     */
    static Vec3 worldFromPixel(BlockPos pos, CanvasFace face, int px, int py) {
        Vec3 c0 = face.corner0();
        Vec3 sideW = face.corner1().subtract(c0);
        Vec3 sideH = face.corner3().subtract(c0);
        double u = (px + 0.5) / face.pixels().getWidth();
        double v = (py + 0.5) / face.pixels().getHeight();
        return Vec3.atCenterOf(pos).add(c0).add(sideW.scale(u)).add(sideH.scale(v));
    }

    /**
     * Inverse projection: world position → pixel on a CanvasFace.
     * Returns null if the world point does not project onto this face.
     */
    static PixelPos pixelFromWorld(Vec3 worldPos, BlockPos pos, CanvasFace face) {
        Vec3 local = worldPos.subtract(Vec3.atCenterOf(pos));
        Vec3 c0 = face.corner0();
        Vec3 sideW = face.corner1().subtract(c0);
        Vec3 sideH = face.corner3().subtract(c0);
        double wLen = sideW.lengthSqr();
        double hLen = sideH.lengthSqr();
        if (wLen < EPSILON || hLen < EPSILON) return null;

        Vec3 relative = local.subtract(c0);
        double u = relative.dot(sideW) / wLen;
        double v = relative.dot(sideH) / hLen;

        int pw = face.pixels().getWidth();
        int ph = face.pixels().getHeight();
        int px = (int) Math.floor(u * pw);
        int py = (int) Math.floor(v * ph);
        if (px < 0 || px >= pw || py < 0 || py >= ph) return null;

        return new PixelPos(pos, face.primaryFace(), px, py, face.pixels().getPixel(px, py));
    }

    /**
     * Flatten a 3D world position to 2D based on face direction.
     * All pixels in a glyph share the same dominant face direction.
     */
    static double[] flatten(Direction face, Vec3 w) {
        return switch (face) {
            case NORTH, SOUTH -> new double[]{w.x, w.y};
            case EAST, WEST   -> new double[]{w.z, w.y};
            case UP, DOWN     -> new double[]{w.x, w.z};
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════

    public static ExtractionResult extract(ServerLevel level, PixelPos seed, int maxBlocks) {
        FloodFillState state = new FloodFillState(seed);
        return continueExtract(level, state, maxBlocks);
    }

    public static ExtractionResult continueExtract(ServerLevel level, FloodFillState state, int maxBlocks) {
        int blocksVisitedThisCall = 0;
        Direction dominantFace = null;

        while (!state.queue.isEmpty()) {
            PixelPos curr = state.queue.poll();
            if (state.visited.contains(curr)) continue;

            CanvasFace face = getFacesAt(level, curr.pos(), curr.face()).stream()
                    .findFirst().orElse(null);
            if (face == null) continue;

            if (!ManaPixelDetector.isManaPixel(face, curr.x(), curr.y())) continue;
            if (ManaPixelDetector.isMarked(face, curr.x(), curr.y())) continue;

            if (dominantFace == null) dominantFace = face.primaryFace();

            state.visited.add(curr);
            state.involvedBlocks.add(curr.pos());

            // Compute and store world position
            Vec3 w3d = worldFromPixel(curr.pos(), face, curr.x(), curr.y());
            double[] w2d = flatten(dominantFace, w3d);
            state.worldXs.add(w2d[0]);
            state.worldYs.add(w2d[1]);
            state.minWorldX = Math.min(state.minWorldX, w2d[0]);
            state.maxWorldX = Math.max(state.maxWorldX, w2d[0]);
            state.minWorldY = Math.min(state.minWorldY, w2d[1]);
            state.maxWorldY = Math.max(state.maxWorldY, w2d[1]);

            // 8-connected neighbors
            int pw = face.pixels().getWidth();
            int ph = face.pixels().getHeight();
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = curr.x() + dx;
                    int ny = curr.y() + dy;

                    if (nx >= 0 && nx < pw && ny >= 0 && ny < ph) {
                        PixelPos nb = new PixelPos(curr.pos(), curr.face(), nx, ny, 0);
                        if (!state.visited.contains(nb)) state.queue.add(nb);
                    } else {
                        // Edge crossing — corner-based lookup
                        List<PixelPos> adjacents = findAdjacentByCorner(level, curr, face, nx, ny);
                        if (!adjacents.isEmpty()) {
                            Gyromancy.LOGGER.debug("[FloodFill] edge ({},{})→({},{}) from {} → {} adj",
                                    curr.x(), curr.y(), nx, ny, curr.pos(), adjacents.size());
                        }
                        for (PixelPos adj : adjacents) {
                            if (!state.visited.contains(adj)) {
                                if (!state.involvedBlocks.contains(adj.pos())) {
                                    blocksVisitedThisCall++;
                                    Gyromancy.LOGGER.debug("[FloodFill] + block {} from {} → {} total",
                                            adj.pos(), curr.pos(), state.involvedBlocks.size() + 1);
                                    if (blocksVisitedThisCall > maxBlocks) {
                                        state.queue.addFirst(curr);
                                        Gyromancy.LOGGER.debug("[FloodFill] budget exhausted, deferring");
                                        return new ExtractionResult(null, state);
                                    }
                                }
                                state.queue.add(adj);
                            }
                        }
                    }
                }
            }
        }

        ExtractedGlyph glyph = buildGlyph(state);

        int added = 0;
        for (PixelPos s : state.initialSeeds) {
            if (!state.visited.contains(s)) { state.queue.add(s); added++; }
        }
        if (added > 0) {
            Gyromancy.LOGGER.debug("[FloodFill] Glyph: {} pixels {} blocks — {} disconnected seeds remain",
                    glyph.pixels().size(), glyph.blockCount(), added);
        } else {
            Gyromancy.LOGGER.debug("[FloodFill] DONE: {} pixels across {} blocks (bbox: {},{} → {},{})",
                    glyph.pixels().size(), glyph.blockCount(),
                    String.format("%.1f", glyph.minWorldX()), String.format("%.1f", glyph.minWorldY()),
                    String.format("%.1f", glyph.maxWorldX()), String.format("%.1f", glyph.maxWorldY()));
        }

        if (glyph.pixels().isEmpty() && added == 0) return new ExtractionResult(null, null);
        return new ExtractionResult(glyph, added > 0 ? resetForNextGroup(state) : null);
    }

    private static ExtractedGlyph buildGlyph(FloodFillState state) {
        int n = state.worldXs.size();
        double[] wx = new double[n], wy = new double[n];
        for (int i = 0; i < n; i++) { wx[i] = state.worldXs.get(i); wy[i] = state.worldYs.get(i); }
        return new ExtractedGlyph(Set.copyOf(state.visited), wx, wy,
                state.minWorldX, state.maxWorldX, state.minWorldY, state.maxWorldY,
                state.involvedBlocks.size());
    }

    private static FloodFillState resetForNextGroup(FloodFillState state) {
        state.resetGeometry();
        return state;
    }

    // ═══════════════════════════════════════════════════════════════
    // Adjacent face lookup
    // ═══════════════════════════════════════════════════════════════

    private static List<PixelPos> findAdjacentByCorner(
            ServerLevel level, PixelPos curr, CanvasFace face, int nx, int ny) {

        // Compute world position of the neighbor pixel beyond the edge
        Vec3 worldNeighbor = worldFromPixel(curr.pos(), face, nx, ny);

        // Find adjacent block: use corner-based direction
        Vec3 c0 = face.corner0();
        Vec3 sideW = face.corner1().subtract(c0);
        Vec3 sideH = face.corner3().subtract(c0);
        Vec3 normal = sideW.cross(sideH);

        int pw = face.pixels().getWidth();
        int ph = face.pixels().getHeight();
        double u = (nx + 0.5) / pw;
        double v = (ny + 0.5) / ph;

        // Determine which side of the face we crossed
        Vec3 edgeDirection = Vec3.ZERO;
        if (nx < 0)      edgeDirection = sideW.scale(-1);
        else if (nx >= pw) edgeDirection = sideW;
        if (ny < 0)      edgeDirection = edgeDirection.add(sideH.scale(-1));
        else if (ny >= ph) edgeDirection = edgeDirection.add(sideH);

        if (edgeDirection.lengthSqr() < EPSILON) return List.of();

        // Normalize and get approximate block direction
        edgeDirection = edgeDirection.normalize();
        Direction adjDir = Direction.getNearest(edgeDirection.x, edgeDirection.y, edgeDirection.z);
        BlockPos adjPos = curr.pos().relative(adjDir);

        if (adjPos.equals(curr.pos())) return List.of();

        // Try all faces on the adjacent block
        List<PixelPos> results = new ArrayList<>();
        for (CanvasFace adjFace : getFacesAt(level, adjPos)) {
            PixelPos mapped = pixelFromWorld(worldNeighbor, adjPos, adjFace);
            if (mapped != null
                    && ManaPixelDetector.isManaPixel(adjFace, mapped.x(), mapped.y())
                    && !ManaPixelDetector.isMarked(adjFace, mapped.x(), mapped.y())) {
                results.add(mapped);
            }
        }
        return results;
    }

    // ═══════════════════════════════════════════════════════════════
    // Normalization: stored world coords → 32×32 binary grid
    // ═══════════════════════════════════════════════════════════════

    public static int[][] normalizeGlyph(ExtractedGlyph glyph) {
        double w = glyph.maxWorldX - glyph.minWorldX;
        double h = glyph.maxWorldY - glyph.minWorldY;
        if (w <= 0 || h <= 0) {
            return createFallbackNormalized(glyph);
        }

        double size = Math.max(w, h) * 1.1;
        double padX = (size - w) / 2.0;
        double padY = (size - h) / 2.0;
        double srcMinX = glyph.minWorldX - padX;
        double srcMinY = glyph.minWorldY - padY;
        double scale = 32.0 / size;
        int[][] result = new int[32][32];

        for (int i = 0; i < glyph.worldX.length; i++) {
            int tx = (int) ((glyph.worldX[i] - srcMinX) * scale);
            int ty = (int) ((glyph.worldY[i] - srcMinY) * scale);
            if (tx >= 0 && tx < 32 && ty >= 0 && ty < 32) {
                result[ty][tx] = 1;
            }
        }
        return result;
    }

    private static int[][] createFallbackNormalized(ExtractedGlyph glyph) {
        int[][] result = new int[32][32];
        int i = 0;
        for (PixelPos p : glyph.pixels) {
            int tx = 16 + p.x(), ty = 16 + p.y();
            if (tx >= 0 && tx < 32 && ty >= 0 && ty < 32) result[ty][tx] = 1;
            i++;
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // Canvas access
    // ═══════════════════════════════════════════════════════════════

    static List<CanvasFace> getFacesAt(ServerLevel level, BlockPos pos, Direction dir) {
        List<CanvasFace> result = new ArrayList<>();
        for (CanvasFace f : getFacesAt(level, pos)) {
            if (f.primaryFace() == dir) result.add(f);
        }
        return result;
    }

    static List<CanvasFace> getFacesAt(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return Collections.emptyList();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CanvasDataHolder holder)) return Collections.emptyList();
        CanvasData data = holder.painter$getCanvasData();
        if (data == null) return Collections.emptyList();
        return data.faces();
    }

    static CanvasFace getFaceAt(ServerLevel level, BlockPos pos, Direction dir) {
        var faces = getFacesAt(level, pos, dir);
        return faces.isEmpty() ? null : faces.getFirst();
    }
}
