package com.astune.gyromancy.guidebook;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Data-only records used by the JSON-backed in-game manual. */
public final class GuidebookContent {

    private GuidebookContent() {}

    public record TextValue(Map<String, String> values) {
        public TextValue {
            values = Map.copyOf(values);
        }

        public TextValue(String chinese, String english) {
            this(Map.of("zh_cn", chinese, "en_us", english));
        }

        /** Resolves an exact locale, then its language-only fallback, then English. */
        public String resolve(String languageCode) {
            String normalized = normalize(languageCode);
            String exact = values.get(normalized);
            if (exact != null) {
                return exact;
            }

            int separator = normalized.indexOf('_');
            if (separator > 0) {
                String languageOnly = values.get(normalized.substring(0, separator));
                if (languageOnly != null) {
                    return languageOnly;
                }
            }

            String english = values.get("en_us");
            if (english != null) {
                return english;
            }
            return values.values().stream().findFirst().orElse("");
        }

        public String resolve(boolean chineseLocale) {
            return resolve(chineseLocale ? "zh_cn" : "en_us");
        }

        private static String normalize(String languageCode) {
            return languageCode == null
                    ? "en_us"
                    : languageCode.toLowerCase(Locale.ROOT).replace('-', '_');
        }
    }

    public record Chapter(String id, TextValue title, List<Page> pages) {
        public Chapter {
            pages = List.copyOf(pages);
        }
    }

    public record Page(String id, TextValue title, List<Block> blocks) {
        public Page {
            blocks = List.copyOf(blocks);
        }
    }

    public sealed interface Block permits Paragraph, Bullets, Code, Image, Link, Spacer {}

    public record Paragraph(TextValue text) implements Block {}

    public record Bullets(List<TextValue> items) implements Block {
        public Bullets {
            items = List.copyOf(items);
        }
    }

    public record Code(TextValue text) implements Block {}

    public record Image(ResourceLocation texture, int width, int height,
                        int sourceWidth, int sourceHeight, TextValue caption) implements Block {}

    public record Link(TextValue label, Target target) implements Block {}

    public record Spacer() implements Block {}

    public sealed interface Target permits PageTarget, UrlTarget {}

    public record PageTarget(String pageId) implements Target {}

    public record UrlTarget(String url) implements Target {}

    public record PageRef(int chapterIndex, int pageIndex) {}

    public record Content(TextValue title, String author, List<Chapter> chapters) {
        public Content {
            chapters = List.copyOf(chapters);
        }
    }
}
