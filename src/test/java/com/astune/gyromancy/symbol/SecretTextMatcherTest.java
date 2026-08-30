package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.ParameterRune;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.SymbolMatch;
import com.astune.gyromancy.api.symbol.SymbolRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretTextMatcherTest {

    @Test
    void loadsEveryRegisteredSecretTextResource() {
        assertEquals(13, SecretText.values().length);
        assertEquals(SecretText.values().length, SecretTextMatcher.INSTANCE.patternCount());
    }

    @Test
    void createsIndependentParameterSymbolsFromOneSharedClass() {
        SecretTextSymbol first = SecretText.SECRET_1.symbol();
        SecretTextSymbol second = SecretText.SECRET_2.symbol();

        assertNotSame(first, second);
        assertEquals(SecretTextSymbol.class, first.getClass());
        assertEquals(SecretTextSymbol.class, second.getClass());
        assertEquals(SecretText.SECRET_1, first.type());
        assertEquals(SymbolRole.PARAMETER_RUNE, first.role());
        assertEquals("secret_text_1", first.id().getPath());
        assertSame(first, SecretTextSymbol.fromId(first.id()));
        assertNull(SecretTextSymbol.fromId(null));

        ParameterRune parameterRune = first.toParameterRune(1.0f);
        assertEquals(first.id(), parameterRune.runeId());
        assertEquals("1", parameterRune.metadata());
    }

    @Test
    void matchesPatternsAfterClippingWhiteBorders() {
        int[][] verticalLine = {
                {1},
                {1},
                {1}
        };
        int[][] clippedTwo = {
                {1, 1},
                {0, 1},
                {0, 1}
        };

        assertHasMatch(SecretText.SECRET_1, verticalLine, 0);
        assertHasMatch(SecretText.SECRET_2, clippedTwo, 0);
    }

    @Test
    void supportsRotatedClippedPatternsWithoutChangingInput() {
        int[][] horizontalLine = {{1, 1, 1}};
        int[][] original = copy(horizontalLine);

        List<SecretTextMatcher.Match> matches = SecretTextMatcher.INSTANCE.recognize(horizontalLine);
        assertHasMatch(SecretText.SECRET_1, matches, 90);
        assertEquals(SecretText.SECRET_1.symbol().id(), matches.getFirst().parameterRune().runeId());
        assertMatrixEquals(original, horizontalLine);
    }

    @Test
    void recognizerExposesSecretAsAParameterRoleSymbolMatch() {
        SymbolRecognizer.RecognitionResult result = SymbolRecognizer.recognizeDetailed(
                verticalLineGlyph(), null, SymbolRecognizer.RecognizerConfig.CLIENT_PREVIEW);

        assertTrue(result.hasSecretText());
        assertEquals(SecretText.SECRET_1,
                result.secretTextMatches().getFirst().secretText());
        assertEquals(1, result.symbolMatches().size());

        SymbolMatch match = result.symbolMatches().getFirst();
        assertEquals(SecretText.SECRET_1.symbol().id(), match.symbolId());
        assertEquals(SymbolRole.PARAMETER_RUNE, match.role());
        assertEquals(1.0f, match.confidence());
    }

    @Test
    void rejectsMatricesLargerThanTheSecretTextCanvas() {
        assertTrue(SecretTextMatcher.INSTANCE.recognize(new int[3][4]).isEmpty());
        assertTrue(SecretTextMatcher.INSTANCE.recognize(new int[4][3]).isEmpty());
        assertTrue(SecretTextMatcher.INSTANCE.recognize(new int[0][0]).isEmpty());
    }

    private static void assertHasMatch(SecretText expected, int[][] image, int rotation) {
        assertHasMatch(expected, SecretTextMatcher.INSTANCE.recognize(image), rotation);
    }

    private static void assertHasMatch(SecretText expected,
                                       List<SecretTextMatcher.Match> matches,
                                       int rotation) {
        assertTrue(matches.stream().anyMatch(match ->
                match.secretText() == expected && match.rotationDegrees() == rotation),
                () -> "Expected " + expected + " at " + rotation + " degrees, got " + matches);
    }

    private static int[][] copy(int[][] source) {
        int[][] result = new int[source.length][];
        for (int y = 0; y < source.length; y++) result[y] = source[y].clone();
        return result;
    }

    private static FloodFillExtractor.ExtractedGlyph verticalLineGlyph() {
        Set<PixelPos> pixels = Set.of(
                new PixelPos(BlockPos.ZERO, Direction.NORTH, 0, 0, 0),
                new PixelPos(BlockPos.ZERO, Direction.NORTH, 0, 1, 0),
                new PixelPos(BlockPos.ZERO, Direction.NORTH, 0, 2, 0));
        return new FloodFillExtractor.ExtractedGlyph(
                pixels, new double[]{0, 0, 0}, new double[]{0, 1, 2},
                0, 0, 0, 2, 1);
    }

    private static void assertMatrixEquals(int[][] expected, int[][] actual) {
        assertEquals(expected.length, actual.length);
        for (int y = 0; y < expected.length; y++) assertArrayEquals(expected[y], actual[y]);
    }
}
