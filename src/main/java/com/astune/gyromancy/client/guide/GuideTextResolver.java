package com.astune.gyromancy.client.guide;

import net.minecraft.locale.Language;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves embedded Minecraft translation keys using the active client language. */
public final class GuideTextResolver {

    private static final Pattern LANGUAGE_TOKEN = Pattern.compile(
            "\\{\\{\\s*(?:(?:lang|translate)\\s*:\\s*)?([A-Za-z0-9_.-]+)\\s*}}"
                    + "|\\$\\(\\s*(?:(?:lang|translate)\\s*:\\s*)?([A-Za-z0-9_.-]+)\\s*\\)"
                    + "|\\$\\{\\s*(?:(?:lang|translate)\\s*:\\s*)?([A-Za-z0-9_.-]+)\\s*}");

    private final Language language;

    private GuideTextResolver(Language language) {
        this.language = language;
    }

    public static GuideTextResolver current() {
        return new GuideTextResolver(Language.getInstance());
    }

    public String resolve(String text) {
        if (text == null || text.isEmpty()) return text;

        Matcher matcher = LANGUAGE_TOKEN.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1) != null ? matcher.group(1)
                    : (matcher.group(2) != null ? matcher.group(2) : matcher.group(3));
            if (!language.has(key)) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(language.getOrDefault(key)));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
