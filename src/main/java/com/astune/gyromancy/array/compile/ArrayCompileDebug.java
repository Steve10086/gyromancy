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

    public static void printRuntime(ServerLevel level, RuntimeModel model) {
        if (!enabled) return;
        printRuntimeModel(level, model, true);
    }

    /** Always logs the compiled model at debug level for live diagnosis. */
    public static void logRuntime(RuntimeModel model) {
        printRuntimeModel(null, model, false);
    }

    private static void printRuntimeModel(ServerLevel level, RuntimeModel model, boolean chat) {
        RuntimeVisitor visitor = chat
                ? (indent, text) -> send(level, indent + text)
                : (indent, text) -> com.astune.gyromancy.Gyromancy.LOGGER.debug(
                        "[ArrayCompile] {}{}", indent, text);
        visitor.line("", "runtime model");
        printRuntimeNode(model.root(), 0, visitor,
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    }

    @FunctionalInterface
    private interface RuntimeVisitor {
        void line(String indent, String text);
    }

    private static void printRuntimeNode(
            com.astune.gyromancy.compile.operator.CompiledOp op,
            int depth, RuntimeVisitor visitor,
            java.util.Set<com.astune.gyromancy.compile.operator.CompiledOp> seen) {
        if (op == null || !seen.add(op)) return;
        String indent = "  ".repeat(depth);
        String summary = op.getClass().getSimpleName();
        if (op instanceof com.astune.gyromancy.compile.operator.MomentumOp momentum) {
            summary += " phase=" + momentum.phase()
                    + " dynamic=" + momentum.dynamic()
                    + " velocity=" + momentum.velocityInputs().size()
                    + " acceleration=" + momentum.accelerationInputs().size();
            for (com.astune.gyromancy.compile.operator.MomentumOp.AccelerationInput input
                    : momentum.accelerationInputs()) {
                summary += " [accel " + input.updateMode() + " "
                        + input.vector().getClass().getSimpleName() + "]";
            }
        }
        visitor.line(indent, summary);
        for (OpInput input : op.inputs()) {
            if (input instanceof OpInput.Op child) {
                printRuntimeNode(child.operator(), depth + 1, visitor, seen);
            } else if (input instanceof OpInput.RawGroup raw) {
                visitor.line(indent + "  ", "RawGroup " + glyphName(raw.group().boundary())
                        + " id=" + raw.group().boundary().glyphId());
                printRuntimeAst(raw.group(), depth + 2, visitor);
            } else if (input instanceof OpInput.Rune rune) {
                visitor.line(indent + "  ", "Rune " + glyphName(rune.glyph()));
            }
        }
    }

    private static void printRuntimeAst(ArrayNode node, int depth, RuntimeVisitor visitor) {
        String indent = "  ".repeat(depth);
        switch (node) {
            case GroupNode group -> {
                visitor.line(indent, "GroupNode boundary=" + glyphName(group.boundary())
                        + " id=" + group.boundary().glyphId());
                printRuntimeAst(group.body(), depth + 1, visitor);
            }
            case SequenceNode sequence -> {
                for (ArrayNode child : sequence.children()) {
                    printRuntimeAst(child, depth, visitor);
                }
            }
            case SymbolNode symbol -> visitor.line(indent, "SymbolNode glyph="
                    + glyphName(symbol.glyph()) + " id=" + symbol.glyph().glyphId()
                    + " front=" + symbol.glyph().front()
                    + " length=" + symbol.glyph().length()
                    + " width=" + symbol.glyph().width());
            case ApplyNode apply -> printRuntimeAst(apply.target(), depth, visitor);
        }
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
