package com.astune.gyromancy.symbol;

import com.astune.gyromancy.util.GeometryUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkeletonMatcherRotationTest {

    @Test
    void rotationComesFromMatchedEdges() {
        int[][] template = arrowUp();
        SkeletonMatcher.SkeletonStats tpl = SkeletonMatcher.computeStats(template);

        assertRotation(tpl, template, 0);
        assertRotation(tpl, template, 90);
        assertRotation(tpl, template, 180);
        assertRotation(tpl, template, 270);
    }

    private static void assertRotation(SkeletonMatcher.SkeletonStats tpl, int[][] template, float expected) {
        int[][] rotated = GeometryUtils.rotateImage(template, -expected);
        SkeletonMatcher.SkeletonStats target = SkeletonMatcher.computeStats(rotated);
        SkeletonMatcher.MatchScore score = SkeletonMatcher.minGraphEditMatch(tpl, target);

        assertEquals(expected, score.rotationDegrees(), 15.0,
                "rotation for " + expected + " degrees");
    }

    private static int[][] arrowUp() {
        int[][] img = new int[32][32];
        for (int y = 7; y <= 25; y++) img[y][16] = 1;
        for (int i = 0; i <= 6; i++) {
            img[7 + i][16 - i] = 1;
            img[7 + i][16 + i] = 1;
        }
        return img;
    }
}
