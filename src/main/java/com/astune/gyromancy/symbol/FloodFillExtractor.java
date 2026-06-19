package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.painter.api.CanvasData;
import com.astune.painter.api.CanvasDataHolder;
import com.astune.painter.api.CanvasFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

/**
 * Cross-block flood fill extractor for connected mana pixels.
 *
 * <p>When a mana pixel is detected on a CanvasFace, this extractor performs
 * an 8-connected BFS flood fill across block boundaries to extract the
 * complete glyph region. It handles:
 * <ul>
 *   <li>Within-face traversal (standard 8-connected BFS on 16×16 faces)</li>
 *   <li>Cross-block traversal (face edge → adjacent block's matching face)</li>
 *   <li>Tick budget enforcement (returns partial state when budget exhausted)</li>
 * </ul>
 */
public final class FloodFillExtractor {

    private FloodFillExtractor() {}

    // ═══════════════════════════════════════════════════════════════
    // Types
    // ═══════════════════════════════════════════════════════════════

    /**
     * The result of a completed flood fill extraction.
     */
    public record ExtractedGlyph(
            /** All mana pixels making up the extracted glyph */
            Set<PixelPos> pixels,
            /** World-space bounding box for normalization */
            double minWorldX, double maxWorldX,
            double minWorldY, double maxWorldY,
            /** Number of distinct blocks involved */
            int blockCount
    ) {}

    /**
     * Continuation state for an in-progress flood fill that exhausted its tick budget.
     * Stored by FloodFillScheduler and resumed on the next tick.
     */
    public static class FloodFillState {
        public final Deque<PixelPos> queue;
        public final Set<PixelPos> visited;
        public final Set<BlockPos> involvedBlocks;
        public double minWorldX = Double.MAX_VALUE;
        public double maxWorldX = Double.MIN_VALUE;
        public double minWorldY = Double.MAX_VALUE;
        public double maxWorldY = Double.MIN_VALUE;

        public FloodFillState(PixelPos seed) {
            this.queue = new ArrayDeque<>();
            this.queue.add(seed);
            this.visited = new HashSet<>();
            this.involvedBlocks = new HashSet<>();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Starts a new flood fill extraction from a seed pixel.
     *
     * @param level     the server world
     * @param seed      the starting mana pixel
     * @param maxBlocks maximum distinct blocks to visit this call (tick budget)
     * @return ExtractedGlyph if extraction completes within budget,
     *         or null if budget exhausted (state stored in the returned FloodFillState)
     */
    public static ExtractionResult extract(ServerLevel level, PixelPos seed, int maxBlocks) {
        FloodFillState state = new FloodFillState(seed);
        return continueExtract(level, state, maxBlocks);
    }

    /**
     * Continues a previously-budgeted flood fill.
     *
     * @param level     the server world
     * @param state     the saved state from a previous partial extraction
     * @param maxBlocks maximum distinct blocks to visit this call
     * @return ExtractedGlyph if extraction completes, or null with updated state
     */
    public static ExtractionResult continueExtract(ServerLevel level, FloodFillState state, int maxBlocks) {
        int blocksVisitedThisCall = 0;

        while (!state.queue.isEmpty()) {
            PixelPos curr = state.queue.poll();

            if (state.visited.contains(curr)) continue;

            // Get the face data for the current pixel's block
            CanvasFace face = getFaceAt(level, curr.pos(), curr.face());
            if (face == null) continue;

            // Stop at non-mana or already-marked pixels
            if (curr.equals(state.visited.stream().findFirst().orElse(null))) {
                // Always process the seed
            } else if (!ManaPixelDetector.isManaPixel(face, curr.x(), curr.y())) {
                continue;
            }
            if (ManaPixelDetector.isMarked(face, curr.x(), curr.y())) {
                continue;
            }

            state.visited.add(curr);
            state.involvedBlocks.add(curr.pos());

            // Update world-space bounding box
            double[] worldXY = pixelToWorldXY(curr.pos(), face, curr.x(), curr.y());
            if (worldXY != null) {
                state.minWorldX = Math.min(state.minWorldX, worldXY[0]);
                state.maxWorldX = Math.max(state.maxWorldX, worldXY[0]);
                state.minWorldY = Math.min(state.minWorldY, worldXY[1]);
                state.maxWorldY = Math.max(state.maxWorldY, worldXY[1]);
            }

            // Explore 8-connected neighbors
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;

                    int nx = curr.x() + dx;
                    int ny = curr.y() + dy;

                    if (nx >= 0 && nx < 16 && ny >= 0 && ny < 16) {
                        // Within same face
                        PixelPos neighbor = new PixelPos(curr.pos(), curr.face(), nx, ny, 0);
                        if (!state.visited.contains(neighbor)) {
                            state.queue.add(neighbor);
                        }
                    } else {
                        // Edge: find adjacent face on neighboring block
                        PixelPos adjacent = findAdjacentFace(level, curr, nx, ny);
                        if (adjacent != null && !state.visited.contains(adjacent)) {
                            // Track new block
                            if (!state.involvedBlocks.contains(adjacent.pos())) {
                                blocksVisitedThisCall++;
                                if (blocksVisitedThisCall > maxBlocks) {
                                    // Budget exhausted — put current pixel back and return partial
                                    state.queue.addFirst(curr);
                                    return new ExtractionResult(null, state);
                                }
                            }
                            state.queue.add(adjacent);
                        }
                    }
                }
            }
        }

        // Extraction complete
        ExtractedGlyph glyph = new ExtractedGlyph(
                Set.copyOf(state.visited),
                state.minWorldX, state.maxWorldX,
                state.minWorldY, state.maxWorldY,
                state.involvedBlocks.size()
        );
        return new ExtractionResult(glyph, null);
    }

    /**
     * Result wrapper: either a completed glyph or a continuation state.
     */
    public record ExtractionResult(
            /** Non-null if extraction completed */
            ExtractedGlyph glyph,
            /** Non-null if budget exhausted and needs continuation */
            FloodFillState continuation
    ) {}

    // ═══════════════════════════════════════════════════════════════
    // Normalization
    // ═══════════════════════════════════════════════════════════════

    /**
     * Normalizes an extracted glyph into a 32×32 binary grid for template matching.
     * Maps world-space pixel coordinates to a canonical 2D plane.
     */
    public static int[][] normalizeGlyph(ExtractedGlyph glyph) {
        double w = glyph.maxWorldX - glyph.minWorldX;
        double h = glyph.maxWorldY - glyph.minWorldY;

        if (w <= 0 || h <= 0) {
            // Single pixel or line — create minimal representation
            return createFallbackNormalized(glyph);
        }

        // Determine target dimensions maintaining aspect ratio, padded to square
        double size = Math.max(w, h) * 1.1;
        double padX = (size - w) / 2.0;
        double padY = (size - h) / 2.0;

        double srcMinX = glyph.minWorldX - padX;
        double srcMinY = glyph.minWorldY - padY;
        double scale = 32.0 / size;

        int[][] result = new int[32][32];

        for (PixelPos p : glyph.pixels) {
            // Map world position to normalized grid
            double[] worldXY = pixelToWorldXY(p.pos(), p.face(), p.x(), p.y());
            if (worldXY == null) continue;

            int tx = (int) ((worldXY[0] - srcMinX) * scale);
            int ty = (int) ((worldXY[1] - srcMinY) * scale);

            if (tx >= 0 && tx < 32 && ty >= 0 && ty < 32) {
                result[ty][tx] = 1;
            }
        }

        return result;
    }

    private static int[][] createFallbackNormalized(ExtractedGlyph glyph) {
        int[][] result = new int[32][32];
        for (PixelPos p : glyph.pixels) {
            // Place at center of 32×32
            int tx = 16 + p.x();
            int ty = 16 + p.y();
            if (tx >= 0 && tx < 32 && ty >= 0 && ty < 32) {
                result[ty][tx] = 1;
            }
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // Face adjacency
    // ═══════════════════════════════════════════════════════════════

    /**
     * Finds the adjacent CanvasFace pixel when flood fill reaches a face edge.
     *
     * @param level current world
     * @param curr  current pixel position
     * @param nx    neighbor x (may be out of 0–15 bounds)
     * @param ny    neighbor y (may be out of 0–15 bounds)
     * @return the adjacent PixelPos, or null if no connecting mana face exists
     */
    private static PixelPos findAdjacentFace(ServerLevel level, PixelPos curr, int nx, int ny) {
        Direction face = curr.face();
        Direction rightDir = getRightDirection(face);
        Direction upDir = getUpDirection(face);

        BlockPos adjacentPos = curr.pos();
        int adjacentPx = nx;
        int adjacentPy = ny;

        // Determine which edge we crossed and compute the adjacent block + pixel coords
        if (nx < 0) {
            adjacentPos = curr.pos().relative(rightDir.getOpposite());
            adjacentPx = 15;
        } else if (nx >= 16) {
            adjacentPos = curr.pos().relative(rightDir);
            adjacentPx = 0;
        }

        if (ny < 0) {
            adjacentPos = curr.pos().relative(upDir.getOpposite());
            adjacentPy = 15;
        } else if (ny >= 16) {
            adjacentPos = curr.pos().relative(upDir);
            adjacentPy = 0;
        }

        // Clamp to valid range
        adjacentPx = Math.clamp(adjacentPx, 0, 15);
        adjacentPy = Math.clamp(adjacentPy, 0, 15);

        if (adjacentPos.equals(curr.pos())) {
            return null; // no adjacent block to check
        }

        // Get the adjacent block's canvas face
        CanvasFace adjFace = getFaceAt(level, adjacentPos, face);
        if (adjFace == null) return null;

        // Verify the target pixel is a mana pixel
        if (!ManaPixelDetector.isManaPixel(adjFace, adjacentPx, adjacentPy)) return null;
        if (ManaPixelDetector.isMarked(adjFace, adjacentPx, adjacentPy)) return null;

        int color = adjFace.pixels().getPixel(adjacentPx, adjacentPy);
        return new PixelPos(adjacentPos, face, adjacentPx, adjacentPy, color);
    }

    // ═══════════════════════════════════════════════════════════════
    // Canvas access helpers
    // ═══════════════════════════════════════════════════════════════

    /**
     * Gets the CanvasFace on a block for a given direction.
     * Returns the first matching face, or null if the block has no canvas data
     * or no face in that direction.
     */
    static CanvasFace getFaceAt(ServerLevel level, BlockPos pos, Direction face) {
        if (!level.isLoaded(pos)) return null;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CanvasDataHolder holder)) return null;

        CanvasData data = holder.painter$getCanvasData();
        if (data == null) return null;

        for (CanvasFace f : data.faces()) {
            if (f.primaryFace() == face) {
                return f;
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // Coordinate mapping
    // ═══════════════════════════════════════════════════════════════

    /**
     * Maps a pixel position on a CanvasFace to approximate world-space XY coordinates.
     * Uses the block position + face corner interpolation for the 2D plane of the face.
     *
     * @return [worldX, worldY] in the plane of the face, or null
     */
    private static double[] pixelToWorldXY(BlockPos pos, CanvasFace face, int px, int py) {
        return pixelToWorldXY(pos, face.primaryFace(), px, py);
    }

    /**
     * Approximates world-space XY for a pixel on a face direction.
     * For horizontal faces, maps to world XZ plane. For vertical faces, maps to world XY.
     */
    static double[] pixelToWorldXY(BlockPos pos, Direction face, int px, int py) {
        double u = (px - 7.5) / 16.0;
        double v = (py - 7.5) / 16.0;

        Direction right = getRightDirection(face);
        Direction up = getUpDirection(face);

        double worldX = pos.getX() + 0.5 + u * right.getStepX() + v * up.getStepX();
        double worldY = pos.getY() + 0.5 + u * right.getStepY() + v * up.getStepY();
        double worldZ = pos.getZ() + 0.5 + u * right.getStepZ() + v * up.getStepZ();

        // Flatten to 2D based on face direction
        return switch (face) {
            case NORTH, SOUTH -> new double[]{worldX, worldY};  // X = world X, Y = world Y
            case EAST, WEST   -> new double[]{worldZ, worldY};  // X = world Z, Y = world Y
            case UP, DOWN     -> new double[]{worldX, worldZ};  // X = world X, Y = world Z
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // Face geometry
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns the world direction corresponding to the +X axis of a CanvasFace
     * (from corner0 to corner1), based on Pigmentum's buildCornersFromOffset logic.
     */
    static Direction getRightDirection(Direction face) {
        return switch (face) {
            case NORTH -> Direction.EAST;
            case SOUTH -> Direction.WEST;
            case EAST  -> Direction.SOUTH;
            case WEST  -> Direction.NORTH;
            case UP    -> Direction.EAST;
            case DOWN  -> Direction.EAST;
        };
    }

    /**
     * Returns the world direction corresponding to the +Y axis of a CanvasFace
     * (from corner0 to corner3), based on Pigmentum's buildCornersFromOffset logic.
     */
    static Direction getUpDirection(Direction face) {
        return switch (face) {
            case NORTH -> Direction.UP;
            case SOUTH -> Direction.UP;
            case EAST  -> Direction.UP;
            case WEST  -> Direction.UP;
            case UP    -> Direction.SOUTH;
            case DOWN  -> Direction.NORTH;
        };
    }
}
