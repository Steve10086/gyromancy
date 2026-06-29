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
            double[] worldX, double[] worldY,
            double minWorldX, double maxWorldX,
            double minWorldY, double maxWorldY,
            int blockCount
    ) {}

    public static class FloodFillState {
        public final int stateId;
        public final PixelPos origin;
        public final Deque<PixelPos> queue;
        public final Set<PixelPos> visited;
        public final Set<PixelPos> processed;
        public final Set<BlockPos> involvedBlocks;
        public final Set<PixelPos> initialSeeds;
        public final List<Double> worldXs = new ArrayList<>();
        public final List<Double> worldYs = new ArrayList<>();
        public Direction dominantFace = null;
        public double minWorldX = Double.MAX_VALUE;
        public double maxWorldX = -Double.MAX_VALUE;
        public double minWorldY = Double.MAX_VALUE;
        public double maxWorldY = -Double.MAX_VALUE;

        public FloodFillState(int id, PixelPos origin) {
            this.stateId = id;
            this.origin = origin;
            this.queue = new ArrayDeque<>();
            this.queue.add(origin);
            this.visited = new HashSet<>();
            this.processed = new HashSet<>();
            this.involvedBlocks = new HashSet<>();
            this.initialSeeds = new HashSet<>();
            this.initialSeeds.add(origin);
        }

        /** Absorb another state's progress. Used when BFS reaches another state's origin. */
        void absorb(FloodFillState other) {
            this.visited.addAll(other.visited);
            this.processed.addAll(other.processed);
            this.worldXs.addAll(other.worldXs);
            this.worldYs.addAll(other.worldYs);
            this.involvedBlocks.addAll(other.involvedBlocks);
            this.initialSeeds.addAll(other.initialSeeds);
            this.minWorldX = Math.min(this.minWorldX, other.minWorldX);
            this.maxWorldX = Math.max(this.maxWorldX, other.maxWorldX);
            this.minWorldY = Math.min(this.minWorldY, other.minWorldY);
            this.maxWorldY = Math.max(this.maxWorldY, other.maxWorldY);
            // Preserve only the absorbed state's current component frontier.
            for (PixelPos s : other.queue) {
                if (!this.visited.contains(s) && !this.processed.contains(s) && !this.queue.contains(s)) {
                    this.queue.add(s);
                }
            }
        }

        void resetGeometry() {
            visited.clear();
            queue.clear();
            worldXs.clear(); worldYs.clear();
            minWorldX = Double.MAX_VALUE; maxWorldX = -Double.MAX_VALUE;
            minWorldY = Double.MAX_VALUE; maxWorldY = -Double.MAX_VALUE;
            involvedBlocks.clear();
            dominantFace = null;
        }
    }

    public record ExtractionResult(ExtractedGlyph glyph, FloodFillState continuation) {}

    // ═══════════════════════════════════════════════════════════════
    // Core: pixel ↔ world coordinate mapping
    // ═══════════════════════════════════════════════════════════════

    static Vec3 worldFromPixel(BlockPos pos, CanvasFace face, int px, int py) {
        Vec3 c0 = face.corner0();
        Vec3 sideW = face.corner1().subtract(c0);
        Vec3 sideH = face.corner3().subtract(c0);
        double u = (px + 0.5) / face.pixels().getWidth();
        double v = (py + 0.5) / face.pixels().getHeight();
        return Vec3.atCenterOf(pos).add(c0).add(sideW.scale(u)).add(sideH.scale(v));
    }

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

    /** Map of all active states (passed from FloodFillScheduler) for origin-based merging */
    private static Map<Integer, FloodFillState> allSeeds = null;

    static void setAllSeedsRef(Map<Integer, FloodFillState> ref) {
        allSeeds = ref;
    }

    public static ExtractionResult continueExtract(ServerLevel level, FloodFillState state, int maxBlocks) {
        int blocksVisitedThisCall = 0;
        int visitedBefore = state.visited.size();

        while (!state.queue.isEmpty()) {
            PixelPos curr = state.queue.poll();
            if (state.visited.contains(curr) || state.processed.contains(curr)) continue;

            CanvasFace face = getFacesAt(level, curr.pos(), curr.face()).stream()
                    .findFirst().orElse(null);
            if (face == null) {
                state.processed.add(curr);
                continue;
            }

            if (!ManaPixelDetector.isManaPixel(face, curr.x(), curr.y())) {
                state.processed.add(curr);
                continue;
            }

            if (state.dominantFace == null) state.dominantFace = face.primaryFace();

            state.visited.add(curr);
            state.involvedBlocks.add(curr.pos());

            // Origin merge: check if this pixel is another state's origin
            checkOriginMerge(state, curr);

            // Compute and store world position
            Vec3 w3d = worldFromPixel(curr.pos(), face, curr.x(), curr.y());
            double[] w2d = flatten(state.dominantFace, w3d);
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
                        if (!state.visited.contains(nb) && !state.processed.contains(nb)) state.queue.add(nb);
                    } else {
                        List<PixelPos> adjacents = findAdjacentByCorner(level, curr, face, nx, ny);
                        if (!adjacents.isEmpty()) {
                            Gyromancy.LOGGER.debug("[FloodFill] edge ({},{})→({},{}) from {} → {} adj",
                                    curr.x(), curr.y(), nx, ny, curr.pos(), adjacents.size());
                        }
                        for (PixelPos adj : adjacents) {
                            if (!state.visited.contains(adj) && !state.processed.contains(adj)) {
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

        // Dead loop guard: no progress → cancel
        if (state.visited.size() == visitedBefore) {
            Gyromancy.LOGGER.debug("[FloodFill] No progress this round — canceling state #{}", state.stateId);
            return enqueueNextComponent(level, state)
                    ? new ExtractionResult(null, state)
                    : new ExtractionResult(null, null);
        }

        ExtractedGlyph glyph = buildGlyph(state);
        state.processed.addAll(state.visited);

        int added = 0;
        for (PixelPos s : state.initialSeeds) {
            if (!state.processed.contains(s)) added++;
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

        boolean hasNext = enqueueNextComponent(level, state);
        if (glyph.pixels().isEmpty() && !hasNext) return new ExtractionResult(null, null);
        return new ExtractionResult(glyph, hasNext ? state : null);
    }

    private static void checkOriginMerge(FloodFillState state, PixelPos curr) {
        if (allSeeds == null) return;
        List<FloodFillState> toRemove = null;
        for (FloodFillState other : allSeeds.values()) {
            if (other.stateId == state.stateId) continue;
            if (other.origin.equals(curr)) {
                if (toRemove == null) toRemove = new ArrayList<>();
                toRemove.add(other);
                state.absorb(other);
                Gyromancy.LOGGER.debug("[FloodFill] State #{} absorbed #{} (origin hit: {})",
                        state.stateId, other.stateId, curr);
            }
        }
        if (toRemove != null) {
            for (FloodFillState r : toRemove) allSeeds.remove(r.stateId);
        }
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

    private static boolean enqueueNextComponent(ServerLevel level, FloodFillState state) {
        state.resetGeometry();
        for (PixelPos seed : state.initialSeeds) {
            if (state.processed.contains(seed)) continue;

            CanvasFace face = getFacesAt(level, seed.pos(), seed.face()).stream()
                    .findFirst().orElse(null);
            if (face == null || !ManaPixelDetector.isManaPixel(face, seed.x(), seed.y())) {
                state.processed.add(seed);
                continue;
            }

            state.queue.add(seed);
            return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // Adjacent face lookup
    // ═══════════════════════════════════════════════════════════════

    private static List<PixelPos> findAdjacentByCorner(
            ServerLevel level, PixelPos curr, CanvasFace face, int nx, int ny) {

        Vec3 worldNeighbor = worldFromPixel(curr.pos(), face, nx, ny);

        Vec3 c0 = face.corner0();
        Vec3 sideW = face.corner1().subtract(c0);
        Vec3 sideH = face.corner3().subtract(c0);

        int pw = face.pixels().getWidth();
        int ph = face.pixels().getHeight();

        Vec3 edgeDirection = Vec3.ZERO;
        if (nx < 0)       edgeDirection = sideW.scale(-1);
        else if (nx >= pw) edgeDirection = sideW;
        if (ny < 0)       edgeDirection = edgeDirection.add(sideH.scale(-1));
        else if (ny >= ph) edgeDirection = edgeDirection.add(sideH);

        if (edgeDirection.lengthSqr() < EPSILON) return List.of();

        edgeDirection = edgeDirection.normalize();
        Direction adjDir = Direction.getNearest(edgeDirection.x, edgeDirection.y, edgeDirection.z);
        BlockPos adjPos = curr.pos().relative(adjDir);

        if (adjPos.equals(curr.pos())) return List.of();

        List<PixelPos> results = new ArrayList<>();
        for (CanvasFace adjFace : getFacesAt(level, adjPos)) {
            PixelPos mapped = pixelFromWorld(worldNeighbor, adjPos, adjFace);
            if (mapped != null
                    && ManaPixelDetector.isManaPixel(adjFace, mapped.x(), mapped.y())) {
                results.add(mapped);
            }
        }
        return results;
    }

    // ═══════════════════════════════════════════════════════════════
    // Normalization
    // ═══════════════════════════════════════════════════════════════

    public static int[][] rawGlyphMatrix(ExtractedGlyph glyph) {
        int n = glyph.worldX.length;
        if (n == 0) return createFallbackRawMatrix(glyph);

        double step = inferGridStep(glyph.worldX, glyph.worldY);
        if (!(step > EPSILON) || !Double.isFinite(step)) step = 1.0 / 16.0;

        int width = Math.max(1, (int) Math.round((glyph.maxWorldX - glyph.minWorldX) / step) + 1);
        int height = Math.max(1, (int) Math.round((glyph.maxWorldY - glyph.minWorldY) / step) + 1);
        int[][] result = new int[height][width];

        for (int i = 0; i < n; i++) {
            int x = (int) Math.round((glyph.worldX[i] - glyph.minWorldX) / step);
            int y = (int) Math.round((glyph.worldY[i] - glyph.minWorldY) / step);
            if (x >= 0 && x < width && y >= 0 && y < height) result[y][x] = 1;
        }
        return result;
    }

    private static double inferGridStep(double[] xs, double[] ys) {
        double dx = minPositiveDelta(xs);
        double dy = minPositiveDelta(ys);
        if (dx > EPSILON && dy > EPSILON) return Math.min(dx, dy);
        if (dx > EPSILON) return dx;
        if (dy > EPSILON) return dy;
        return 1.0 / 16.0;
    }

    private static double minPositiveDelta(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);

        double best = Double.MAX_VALUE;
        double last = sorted.length == 0 ? 0.0 : sorted[0];
        for (int i = 1; i < sorted.length; i++) {
            double delta = sorted[i] - last;
            if (delta > EPSILON) {
                best = Math.min(best, delta);
                last = sorted[i];
            }
        }
        return best == Double.MAX_VALUE ? 0.0 : best;
    }

    private static int[][] createFallbackRawMatrix(ExtractedGlyph glyph) {
        if (glyph.pixels.isEmpty()) return new int[1][1];

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (PixelPos p : glyph.pixels) {
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
        }

        int[][] result = new int[maxY - minY + 1][maxX - minX + 1];
        for (PixelPos p : glyph.pixels) result[p.y() - minY][p.x() - minX] = 1;
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
