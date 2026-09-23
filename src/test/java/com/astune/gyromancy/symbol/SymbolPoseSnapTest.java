package com.astune.gyromancy.symbol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SymbolPoseSnapTest {
    private static final double EPSILON = 1.0E-9;

    @Test
    void nearAxisDetectionSnapsToTheAxisWhenThatIsTheOnlyRepresentableDirection() {
        SymbolRecognizer.PixelBasis snapped = SymbolRecognizer.snapBasis(
                new SymbolRecognizer.PixelBasis(-0.011329111233075515, 0.9999358235600266),
                3, 4);

        assertEquals(0.0, snapped.frontX(), EPSILON);
        assertEquals(1.0, snapped.frontY(), EPSILON);
    }

    @Test
    void diagonalDetectionSnapsToThePixelGridDiagonal() {
        SymbolRecognizer.PixelBasis snapped = SymbolRecognizer.snapBasis(
                new SymbolRecognizer.PixelBasis(0.704, 0.710), 5, 5);

        assertEquals(1.0 / Math.sqrt(2.0), snapped.frontX(), EPSILON);
        assertEquals(1.0 / Math.sqrt(2.0), snapped.frontY(), EPSILON);
    }

    @Test
    void shallowDetectionSnapsToItsIntegerLatticeDirection() {
        SymbolRecognizer.PixelBasis snapped = SymbolRecognizer.snapBasis(
                new SymbolRecognizer.PixelBasis(1.01, 4.0), 3, 4);

        assertEquals(1.0 / Math.sqrt(17.0), snapped.frontX(), EPSILON);
        assertEquals(4.0 / Math.sqrt(17.0), snapped.frontY(), EPSILON);
    }

    @Test
    void singlePixelGlyphKeepsItsDetectedDirection() {
        SymbolRecognizer.PixelBasis basis = new SymbolRecognizer.PixelBasis(0.6, 0.8);

        assertEquals(basis, SymbolRecognizer.snapBasis(basis, 0, 0));
    }
}
