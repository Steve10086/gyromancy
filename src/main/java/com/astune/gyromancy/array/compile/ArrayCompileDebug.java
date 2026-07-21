package com.astune.gyromancy.array.compile;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public final class ArrayCompileDebug {
    private static volatile boolean enabled;

    private ArrayCompileDebug() {}

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void printAst(ServerLevel level, GroupNode ast) {
        if (!enabled) return;
        send(level, "[ArrayCompile] AST");
        printNode(level, ast, 0);
    }

    public static void printFailure(ServerLevel level, CompileResult.Failure<?> failure) {
        if (!enabled) return;
        send(level, "[ArrayCompile] failed");
        for (CompileDiagnostic diagnostic : failure.diagnostics()) {
            send(level, "  " + diagnostic.code() + ": " + diagnostic.message());
        }
    }

    private static void printNode(ServerLevel level, ArrayNode node, int depth) {
        String indent = "  ".repeat(depth);
        switch (node) {
            case GroupNode group -> {
                send(level, indent + "GroupNode boundary=" + glyphName(group.boundary())
                        + " id=" + group.boundary().glyphId());
                printNode(level, group.body(), depth + 1);
            }
            case SequenceNode sequence -> {
                send(level, indent + "SequenceNode children=" + sequence.children().size());
                for (ArrayNode child : sequence.children()) {
                    printNode(level, child, depth + 1);
                }
            }
            case SymbolNode symbol -> send(level, indent + "SymbolNode glyph=" + glyphName(symbol.glyph())
                    + " id=" + symbol.glyph().glyphId()
                    + " role=" + symbol.glyph().role());
            case ApplyNode apply -> {
                send(level, indent + "ApplyNode");
                printNode(level, apply.operator(), depth + 1);
                printNode(level, apply.target(), depth + 1);
            }
        }
    }

    private static String glyphName(com.astune.gyromancy.api.symbol.PositionedGlyph glyph) {
        return glyph.symbolId().getPath();
    }

    private static void send(ServerLevel level, String text) {
        Component message = Component.literal(text);
        for (var player : level.players()) {
            player.sendSystemMessage(message);
        }
    }
}
