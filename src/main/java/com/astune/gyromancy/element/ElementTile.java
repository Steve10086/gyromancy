package com.astune.gyromancy.element;

import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.api.element.ElementType;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Dense, block-accurate storage for an 8x8x8 part of the world.
 *
 * <p>Only allocated tiles carry storage overhead. Within a tile, primitive
 * structure-of-arrays storage replaces BlockPos keys and per-cell objects.</p>
 */
public final class ElementTile {

    public static final int SHIFT = 3;
    public static final int EDGE = 1 << SHIFT;
    public static final int MASK = EDGE - 1;
    public static final int CELL_COUNT = EDGE * EDGE * EDGE;
    private static final int WORD_COUNT = CELL_COUNT / Long.SIZE;

    private final int[][] values = new int[ElementType.COUNT][CELL_COUNT];
    private int[][] derivatives;
    private final long[][] pendingFlux = new long[ElementType.COUNT][];
    private final long[] pendingFluxCells = new long[WORD_COUNT];
    private final long[] activeCells = new long[WORD_COUNT];
    private int activeCount;

    /** Six direction masks, populated lazily from world block state. */
    private long[][] blockedFaces;
    private boolean barriersDirty = true;

    public static int index(int x, int y, int z) {
        return (x & MASK) | ((z & MASK) << SHIFT) | ((y & MASK) << (SHIFT * 2));
    }

    public static int localX(int index) {
        return index & MASK;
    }

    public static int localZ(int index) {
        return (index >> SHIFT) & MASK;
    }

    public static int localY(int index) {
        return (index >> (SHIFT * 2)) & MASK;
    }

    public boolean isActive(int index) {
        return (activeCells[index >>> 6] & (1L << (index & 63))) != 0L;
    }

    public int activeCount() {
        return activeCount;
    }

    public boolean isEmpty() {
        return activeCount == 0;
    }

    public long[] activeWords() {
        return activeCells;
    }

    public int value(int element, int index) {
        return values[element][index];
    }

    public int derivative(int element, int index) {
        return derivatives == null ? 0 : derivatives[element][index];
    }

    public void setDerivative(int element, int index, long derivative) {
        if (derivative == 0L && derivatives == null) return;
        if (derivatives == null) derivatives = new int[ElementType.COUNT][CELL_COUNT];
        derivatives[element][index] = clampToInt(derivative);
    }

    public void addDerivative(int element, int index, long derivative) {
        if (derivative == 0L) return;
        setDerivative(element, index, (long) derivative(element, index) + derivative);
    }

    public void clearDerivatives() {
        if (derivatives == null) return;
        for (int[] elementDerivatives : derivatives) {
            Arrays.fill(elementDerivatives, 0);
        }
    }

    public void clearPendingFlux() {
        Arrays.fill(pendingFluxCells, 0L);
        for (long[] elementFlux : pendingFlux) {
            if (elementFlux != null) Arrays.fill(elementFlux, 0L);
        }
    }

    public void addPendingFlux(int element, int index, long amount) {
        if (pendingFlux[element] == null) pendingFlux[element] = new long[CELL_COUNT];
        pendingFlux[element][index] += amount;
        pendingFluxCells[index >>> 6] |= 1L << (index & 63);
    }

    public long pendingFlux(int element, int index) {
        return pendingFlux[element] == null ? 0L : pendingFlux[element][index];
    }

    public long[] pendingFluxWords() {
        return pendingFluxCells;
    }

    public void set(int index, long[] newValues, long[] newDerivatives) {
        activate(index);
        for (int element = 0; element < ElementType.COUNT; element++) {
            values[element][index] = clampToInt(
                    element < newValues.length ? newValues[element] : 0L);
            long derivative = element < newDerivatives.length ? newDerivatives[element] : 0L;
            setDerivative(element, index, derivative);
        }
    }

    public void setValue(int element, int index, long value) {
        activate(index);
        values[element][index] = clampToInt(value);
    }

    public ElementConcentrations concentrations(int index) {
        long[] resultValues = new long[ElementType.COUNT];
        long[] resultDerivatives = new long[ElementType.COUNT];
        for (int element = 0; element < ElementType.COUNT; element++) {
            resultValues[element] = values[element][index];
            resultDerivatives[element] = derivative(element, index);
        }
        return new ElementConcentrations(resultValues, resultDerivatives);
    }

    public void remove(int index) {
        if (!isActive(index)) return;
        activeCells[index >>> 6] &= ~(1L << (index & 63));
        activeCount--;
        for (int element = 0; element < ElementType.COUNT; element++) {
            values[element][index] = 0;
            if (derivatives != null) derivatives[element][index] = 0;
        }
    }

    private void activate(int index) {
        if (isActive(index)) return;
        activeCells[index >>> 6] |= 1L << (index & 63);
        activeCount++;
    }

    public void markBarriersDirty() {
        barriersDirty = true;
    }

    public boolean barriersDirty() {
        return barriersDirty;
    }

    public void clearBlockedFaces() {
        if (blockedFaces != null) {
            for (long[] direction : blockedFaces) Arrays.fill(direction, 0L);
        }
    }

    public void setBlocked(int index, Direction direction, boolean blocked) {
        if (blockedFaces == null) blockedFaces = new long[Direction.values().length][WORD_COUNT];
        int ordinal = direction.ordinal();
        long bit = 1L << (index & 63);
        if (blocked) blockedFaces[ordinal][index >>> 6] |= bit;
        else blockedFaces[ordinal][index >>> 6] &= ~bit;
    }

    public boolean isBlocked(int index, Direction direction) {
        return blockedFaces != null
                && (blockedFaces[direction.ordinal()][index >>> 6] & (1L << (index & 63))) != 0L;
    }

    public void finishBarrierRefresh() {
        barriersDirty = false;
    }

    public List<CellData> serializedCells() {
        List<CellData> cells = new ArrayList<>(activeCount);
        for (int wordIndex = 0; wordIndex < activeCells.length; wordIndex++) {
            long word = activeCells[wordIndex];
            while (word != 0L) {
                int bit = Long.numberOfTrailingZeros(word);
                int index = (wordIndex << 6) + bit;
                List<Integer> cellValues = new ArrayList<>(ElementType.COUNT);
                for (int element = 0; element < ElementType.COUNT; element++) {
                    cellValues.add(values[element][index]);
                }
                cells.add(new CellData(index, cellValues));
                word &= word - 1L;
            }
        }
        return cells;
    }

    public static ElementTile fromSerializedCells(List<CellData> cells) {
        ElementTile tile = new ElementTile();
        for (CellData cell : cells) {
            if (cell.index() < 0 || cell.index() >= CELL_COUNT) continue;
            long[] values = new long[ElementType.COUNT];
            for (int i = 0; i < Math.min(values.length, cell.values().size()); i++) {
                values[i] = cell.values().get(i);
            }
            tile.set(cell.index(), values, new long[ElementType.COUNT]);
        }
        return tile;
    }

    public record CellData(int index, List<Integer> values) {}

    private static int clampToInt(long value) {
        return (int) Math.max(ElementConcentrations.MIN_VALUE,
                Math.min(value, ElementConcentrations.MAX_VALUE));
    }
}
