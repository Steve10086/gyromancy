package com.astune.gyromancy.client.guide;

import net.minecraft.network.chat.ClickEvent;
import net.favouriteless.modopedia.api.text.StyleStack;
import net.favouriteless.modopedia.api.text.TextFormatter;

/**
 * The single visual and interaction entry point for guide links.
 *
 * <p>The formatter deliberately owns both the click event and the visual
 * treatment, so Markdown links and JSON-authored menus cannot drift apart.</p>
 */
public final class GuideLinkFormatter implements TextFormatter {

    public static final String TAG = "gyromancy:link";
    private static final String PREFIX = TAG + ":";
    private static final int LINK_COLOR = 0x7B3F75;

    @Override
    public boolean matches(String tag) {
        return tag.equals(TAG) || tag.startsWith(PREFIX) || tag.equals("/" + TAG);
    }

    @Override
    public void apply(StyleStack stack, String tag) {
        if (tag.equals("/" + TAG)) {
            stack.pop();
            return;
        }

        String target = tag.substring(PREFIX.length());
        ClickEvent click = clickEvent(target);
        if (click == null) {
            stack.pop();
            return;
        }

        stack.modify(style -> style
                .withColor(LINK_COLOR)
                .withUnderlined(true)
                .withClickEvent(click));
    }

    public static String wrap(String target, String text) {
        return "$(" + PREFIX + target + ")"
                + text + "$(/" + TAG + ")";
    }

    public static String target(String destination) {
        if (destination == null || destination.isBlank()) return "";
        if (destination.startsWith("entries:")) {
            return "entry:" + normalizeId(destination.substring("entries:".length()));
        }
        if (destination.startsWith("categories:")) {
            return "category:" + normalizeId(destination.substring("categories:".length()));
        }
        if (destination.startsWith("entry:")) {
            return "entry:" + normalizeId(destination.substring("entry:".length()));
        }
        if (destination.startsWith("category:")) {
            return "category:" + normalizeId(destination.substring("category:".length()));
        }
        if (destination.startsWith("#")) return "entry:" + destination.substring(1);
        if (destination.startsWith("http://") || destination.startsWith("https://")
                || destination.startsWith("mailto:")) {
            return "url:" + destination;
        }
        return "entry:" + normalizeId(destination);
    }

    private static String normalizeId(String id) {
        return id.replace(':', '/');
    }

    private static ClickEvent clickEvent(String target) {
        if (target.startsWith("url:")) {
            return new ClickEvent(ClickEvent.Action.OPEN_URL, target.substring("url:".length()));
        }
        if (target.startsWith("entry:")) {
            return command("entry", target.substring("entry:".length()));
        }
        if (target.startsWith("category:")) {
            return command("category", target.substring("category:".length()));
        }
        return null;
    }

    private static ClickEvent command(String type, String id) {
        if (id.isBlank()) return null;
        return new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                String.format("/modopedia open %s \"%s\"", type, id));
    }
}
