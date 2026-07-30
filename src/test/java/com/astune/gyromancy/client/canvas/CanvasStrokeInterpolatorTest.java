package com.astune.gyromancy.client.canvas;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasStrokeInterpolatorTest {

    @Test
    void fastHorizontalDragFillsEverySkippedPixel() {
        List<Point> points = line(1, 3, 7, 3);

        assertEquals(List.of(
                new Point(1, 3),
                new Point(2, 3),
                new Point(3, 3),
                new Point(4, 3),
                new Point(5, 3),
                new Point(6, 3),
                new Point(7, 3)), points);
    }

    @Test
    void diagonalInterpolationIsContinuousAndIncludesBothSamples() {
        List<Point> points = line(1, 1, 7, 4);

        assertEquals(new Point(1, 1), points.getFirst());
        assertEquals(new Point(7, 4), points.getLast());
        assertEquals(points.size(), new HashSet<>(points).size());
        for (int index = 1; index < points.size(); index++) {
            Point previous = points.get(index - 1);
            Point current = points.get(index);
            assertTrue(Math.abs(current.x() - previous.x()) <= 1);
            assertTrue(Math.abs(current.y() - previous.y()) <= 1);
        }
    }

    @Test
    void interpolationWorksInReverseDirection() {
        Set<Point> forward = Set.copyOf(line(2, 1, 8, 5));
        Set<Point> reverse = Set.copyOf(line(8, 5, 2, 1));

        assertEquals(forward, reverse);
    }

    private static List<Point> line(int startX, int startY, int endX, int endY) {
        List<Point> points = new ArrayList<>();
        CanvasStrokeInterpolator.visitLine(
                startX, startY, endX, endY,
                (x, y) -> points.add(new Point(x, y)));
        return points;
    }

    private record Point(int x, int y) {}
}
