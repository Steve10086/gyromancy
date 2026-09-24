package com.astune.gyromancy.array.compile;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class ArrayCompileFeedbackTest {

    @Test
    void translationKeyUsesTheDiagnosticCode() {
        assertEquals("message.gyromancy.array.compile.missing_wind_direction",
                ArrayCompileFeedback.translationKeyFor("missing_wind_direction"));
    }

    @Test
    void failureMessageWrapsTheFirstDiagnosticWithItsCodeKey() {
        Component message = ArrayCompileFeedback.failureMessage(List.of(
                new CompileDiagnostic("missing_wind_direction",
                        "Wind fields require at least one arrow rune"),
                new CompileDiagnostic("missing_primary_element",
                        "Local direct inputs did not match an operator")));

        TranslatableContents outer = assertInstanceOf(
                TranslatableContents.class, message.getContents());
        assertEquals("message.gyromancy.array.compile.failed", outer.getKey());
        assertEquals(1, outer.getArgs().length);

        Component innerComponent = assertInstanceOf(Component.class, outer.getArgs()[0]);
        TranslatableContents inner = assertInstanceOf(
                TranslatableContents.class, innerComponent.getContents());
        assertEquals("message.gyromancy.array.compile.missing_wind_direction", inner.getKey());
        assertEquals("%s", inner.getFallback());
        assertArrayEquals(
                new Object[]{"Wind fields require at least one arrow rune"}, inner.getArgs());
    }

    @Test
    void unknownCodesKeepTheRawCompilerMessageAsFallback() {
        Component message = ArrayCompileFeedback.failureMessage(List.of(
                new CompileDiagnostic("brand_new_code", "raw detail")));

        TranslatableContents outer = assertInstanceOf(
                TranslatableContents.class, message.getContents());
        Component innerComponent = assertInstanceOf(Component.class, outer.getArgs()[0]);
        TranslatableContents inner = assertInstanceOf(
                TranslatableContents.class, innerComponent.getContents());
        assertEquals("message.gyromancy.array.compile.brand_new_code", inner.getKey());
        assertEquals("%s", inner.getFallback());
        assertArrayEquals(new Object[]{"raw detail"}, inner.getArgs());
    }

    @Test
    void emptyDiagnosticsStillProduceAFailureMessage() {
        Component message = ArrayCompileFeedback.failureMessage(List.of());

        TranslatableContents outer = assertInstanceOf(
                TranslatableContents.class, message.getContents());
        assertEquals("message.gyromancy.array.compile.failed", outer.getKey());
    }

    @Test
    void notRunnableMessageUsesItsOwnKey() {
        Component message = ArrayCompileFeedback.notRunnableMessage();

        TranslatableContents contents = assertInstanceOf(
                TranslatableContents.class, message.getContents());
        assertEquals("message.gyromancy.array.compile.not_runnable", contents.getKey());
    }

    @Test
    void aSingleIssueKeepsTheMessageUnchanged() {
        Component message = ArrayCompileFeedback.notRunnableMessage();

        assertSame(message, ArrayCompileFeedback.appendIssueCount(message, 1));
    }

    @Test
    void additionalIssuesAreAppendedAsACount() {
        Component message = ArrayCompileFeedback.appendIssueCount(
                ArrayCompileFeedback.notRunnableMessage(), 3);

        assertEquals(1, message.getSiblings().size());
        TranslatableContents suffix = assertInstanceOf(
                TranslatableContents.class, message.getSiblings().getFirst().getContents());
        assertEquals("message.gyromancy.array.compile.more", suffix.getKey());
        assertArrayEquals(new Object[]{3}, suffix.getArgs());
    }
}
