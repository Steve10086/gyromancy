package com.astune.gyromancy.symbol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SymbolCatalogTest {

    @Test
    void builtInSymbolsAreLoaded() {
        assertEquals(0.75, SymbolCatalog.thresholdsFor("arrow").length());
        assertEquals(0.95, SymbolCatalog.thresholdsFor("circle_outer").segment());
    }
}
