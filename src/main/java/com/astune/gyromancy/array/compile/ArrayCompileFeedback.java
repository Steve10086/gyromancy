package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reports spell-array compilation problems to players near the failed circle.
 * Every message is localized through {@code message.gyromancy.array.compile.*}
 * keys and falls back to the raw compiler message for unknown diagnostic codes.
 */
public final class ArrayCompileFeedback {
    public static final double NOTIFY_RADIUS = 16.0;
    static final String MESSAGE_PREFIX = "message.gyromancy.array.compile.";
    private static final double NOTIFY_RADIUS_SQR = NOTIFY_RADIUS * NOTIFY_RADIUS;

    private ArrayCompileFeedback() {}

    /**
     * One failed compilation unit: either a hard compile failure or a circle
     * whose compiled root is not a runnable persistent operator.
     */
    public record Issue(PositionedGlyph root, List<CompileDiagnostic> diagnostics,
                        boolean notRunnable) {
        public Issue {
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public static void reportFailure(ServerLevel level, PositionedGlyph root,
                                     List<CompileDiagnostic> diagnostics) {
        logFailure(root, diagnostics);
        broadcast(level, List.of(root), failureMessage(diagnostics));
    }

    public static void reportNotRunnable(ServerLevel level, PositionedGlyph root) {
        Gyromancy.LOGGER.debug(
                "[MagicArray] Circle glyph #{} ({}) compiled without a persistent root",
                root.glyphId(), root.symbolId().getPath());
        broadcast(level, List.of(root), notRunnableMessage());
    }

    /**
     * Aggregates every issue of one compile batch into a single action-bar
     * message so overlapping circles cannot overwrite each other's feedback.
     */
    public static void reportAll(ServerLevel level, List<Issue> issues) {
        if (issues.isEmpty()) return;
        for (Issue issue : issues) {
            if (issue.notRunnable()) {
                Gyromancy.LOGGER.debug(
                        "[MagicArray] Circle glyph #{} ({}) compiled without a persistent root",
                        issue.root().glyphId(), issue.root().symbolId().getPath());
            } else {
                logFailure(issue.root(), issue.diagnostics());
            }
        }

        Issue primary = issues.getFirst();
        Component message = primary.notRunnable()
                ? notRunnableMessage() : failureMessage(primary.diagnostics());
        broadcast(level, issues.stream().map(Issue::root).toList(),
                appendIssueCount(message, issues.size()));
    }

    static Component appendIssueCount(Component message, int issueCount) {
        return issueCount <= 1
                ? message
                : message.copy().append(Component.translatable(
                        MESSAGE_PREFIX + "more", issueCount));
    }

    static Component failureMessage(List<CompileDiagnostic> diagnostics) {
        boolean runtime = isRuntimeError(diagnostics);
        CompileDiagnostic primary = diagnostics.stream()
                .filter(diagnostic -> !CompileDiagnostic.RUNTIME_ERROR.equals(diagnostic.code()))
                .findFirst()
                .orElseGet(() -> diagnostics.isEmpty()
                        ? new CompileDiagnostic("unknown", "Compilation failed")
                        : diagnostics.getFirst());
        return Component.translatable(MESSAGE_PREFIX + (runtime ? "runtime_error" : "failed"),
                Component.translatableWithFallback(
                        translationKeyFor(primary.code()), "%s", primary.message()));
    }

    static Component notRunnableMessage() {
        return Component.translatable(MESSAGE_PREFIX + "not_runnable");
    }

    static String translationKeyFor(String code) {
        return MESSAGE_PREFIX + code;
    }

    private static void logFailure(PositionedGlyph root,
                                   List<CompileDiagnostic> diagnostics) {
        if (isRuntimeError(diagnostics)) {
            Gyromancy.LOGGER.warn("[MagicArrayRuntime] {} in {}: {}",
                    CompileDiagnostic.RUNTIME_ERROR,
                    root.symbolId().getPath() + "#" + root.glyphId(), diagnostics);
            return;
        }
        Gyromancy.LOGGER.warn("[MagicArray] Compile failed for glyph #{} ({}): {}",
                root.glyphId(), root.symbolId().getPath(), diagnostics);
    }

    private static boolean isRuntimeError(List<CompileDiagnostic> diagnostics) {
        return diagnostics.stream()
                .anyMatch(diagnostic -> CompileDiagnostic.RUNTIME_ERROR.equals(diagnostic.code()));
    }

    private static void broadcast(ServerLevel level, List<PositionedGlyph> roots,
                                  Component message) {
        Set<ServerPlayer> audience = new LinkedHashSet<>();
        for (ServerPlayer player : level.players()) {
            Vec3 position = player.position();
            for (PositionedGlyph root : roots) {
                if (position.distanceToSqr(root.center()) <= NOTIFY_RADIUS_SQR) {
                    audience.add(player);
                    break;
                }
            }
        }
        for (ServerPlayer player : audience) {
            player.displayClientMessage(message, true);
        }
    }
}
