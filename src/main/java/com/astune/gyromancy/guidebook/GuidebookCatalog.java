package com.astune.gyromancy.guidebook;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.astune.gyromancy.guidebook.GuidebookContent.*;

/** Loads the JSON manual and adapts it to vanilla's written-book component. */
public final class GuidebookCatalog {

    private static final String MODID = "gyromancy";
    private static final String RESOURCE_PATH = "/data/gyromancy/guidebook.json";
    private static final String DEFAULT_LANGUAGE = "zh_cn";
    private static final String BOOK_MARKER = "gyromancy:guidebook:";
    private static final int NATIVE_LINES_PER_PAGE = 14;
    private static final int NATIVE_CHARS_PER_LINE = 12;

    public static final Content CONTENT = loadContent();
    private static final Map<String, NativeBook> NATIVE_BOOKS = new HashMap<>();

    private GuidebookCatalog() {}

    /** Creates the vanilla component used by the registered item and by lecterns. */
    public static WrittenBookContent createWrittenBookContent() {
        return createWrittenBookContent(DEFAULT_LANGUAGE);
    }

    /** Creates a localized vanilla component without changing the item in the player's inventory. */
    public static WrittenBookContent createWrittenBookContent(String language) {
        String normalizedLanguage = normalizeLanguage(language);
        NativeBook book = nativeBook(normalizedLanguage);
        List<Filterable<Component>> pages = new ArrayList<>(book.pages().size());
        for (Component page : book.pages()) {
            pages.add(Filterable.passThrough(page));
        }
        return new WrittenBookContent(
                Filterable.passThrough(CONTENT.title().resolve(normalizedLanguage)),
                CONTENT.author(),
                0,
                List.copyOf(pages),
                true);
    }

    public static int nativePageCount(String language) {
        return nativeBook(language).pages().size();
    }

    public static List<Image> imagesOnNativePage(String language, int pageIndex) {
        List<List<Image>> images = nativeBook(language).images();
        return pageIndex >= 0 && pageIndex < images.size() ? images.get(pageIndex) : List.of();
    }

    /** Returns the language marker attached to a guidebook page, or null for another book. */
    public static String guidebookLanguage(Component firstPage) {
        if (firstPage == null) {
            return null;
        }
        String insertion = firstPage.getStyle().getInsertion();
        if (insertion == null || !insertion.startsWith(BOOK_MARKER)) {
            return null;
        }
        return normalizeLanguage(insertion.substring(BOOK_MARKER.length()));
    }

    /** Returns the one-based vanilla page number for the first page of a catalog page. */
    public static int nativePageNumber(String pageId) {
        return nativePageNumber(DEFAULT_LANGUAGE, pageId);
    }

    public static int nativePageNumber(String language, String pageId) {
        Integer page = nativeBook(language).firstPageById().get(pageId);
        return page == null ? -1 : page + 1;
    }

    public static List<Chapter> chapters() {
        return CONTENT.chapters();
    }

    public static PageRef findPage(String pageId) {
        for (int chapterIndex = 0; chapterIndex < CONTENT.chapters().size(); chapterIndex++) {
            List<Page> pages = CONTENT.chapters().get(chapterIndex).pages();
            for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
                if (pages.get(pageIndex).id().equals(pageId)) {
                    return new PageRef(chapterIndex, pageIndex);
                }
            }
        }
        return null;
    }

    private static NativeBook nativeBook(String language) {
        String normalizedLanguage = normalizeLanguage(language);
        synchronized (NATIVE_BOOKS) {
            return NATIVE_BOOKS.computeIfAbsent(normalizedLanguage, GuidebookCatalog::buildNativeBook);
        }
    }

    private static NativeBook buildNativeBook(String language) {
        List<NativeDraft> drafts = new ArrayList<>();
        Map<String, Integer> firstPageById = new LinkedHashMap<>();

        for (Chapter chapter : CONTENT.chapters()) {
            for (Page page : chapter.pages()) {
                firstPageById.put(page.id(), drafts.size());
                List<NativeLine> lines = nativeLines(page, language);
                if (lines.isEmpty()) {
                    lines = List.of(NativeLine.text(resolve(page.title(), language)));
                }

                for (int offset = 0; offset < lines.size(); offset += NATIVE_LINES_PER_PAGE) {
                    int end = Math.min(lines.size(), offset + NATIVE_LINES_PER_PAGE);
                    drafts.add(new NativeDraft(
                            List.copyOf(lines.subList(offset, end)),
                            offset == 0 ? imagesInPage(page) : List.of()));
                }
            }
        }

        List<Component> pages = new ArrayList<>(drafts.size());
        List<List<Image>> images = new ArrayList<>(drafts.size());
        for (NativeDraft draft : drafts) {
            MutableComponent page = Component.literal("");
            if (pages.isEmpty()) {
                page.withStyle(style -> style.withInsertion(BOOK_MARKER + language));
            }
            for (NativeLine line : draft.lines()) {
                Component lineComponent = line.link() == null
                        ? Component.literal(line.text())
                        : nativeLink(line.link(), language, firstPageById);
                page.append(lineComponent).append("\n");
            }
            pages.add(page);
            images.add(draft.images());
        }
        return new NativeBook(List.copyOf(pages), List.copyOf(images), Map.copyOf(firstPageById));
    }

    private static List<NativeLine> nativeLines(Page page, String language) {
        List<NativeLine> lines = new ArrayList<>();
        addWrapped(lines, resolve(page.title(), language));
        lines.add(NativeLine.text(""));

        for (Block block : page.blocks()) {
            if (block instanceof Paragraph paragraph) {
                addWrapped(lines, resolve(paragraph.text(), language));
                lines.add(NativeLine.text(""));
            } else if (block instanceof Bullets bullets) {
                for (TextValue item : bullets.items()) {
                    addWrapped(lines, "• " + resolve(item, language));
                }
                lines.add(NativeLine.text(""));
            } else if (block instanceof Code code) {
                for (String codeLine : resolve(code.text(), language).split("\\n", -1)) {
                    addWrapped(lines, codeLine);
                }
                lines.add(NativeLine.text(""));
            } else if (block instanceof Image image) {
                addWrapped(lines, "[图] " + resolve(image.caption(), language));
            } else if (block instanceof Link link) {
                lines.add(NativeLine.link(link));
            } else if (block instanceof Spacer) {
                lines.add(NativeLine.text(""));
            }
        }
        return lines;
    }

    private static void addWrapped(List<NativeLine> lines, String text) {
        if (text.isEmpty()) {
            lines.add(NativeLine.text(""));
            return;
        }
        for (String rawLine : text.split("\\n", -1)) {
            if (rawLine.isEmpty()) {
                lines.add(NativeLine.text(""));
                continue;
            }
            for (int offset = 0; offset < rawLine.length(); offset += NATIVE_CHARS_PER_LINE) {
                int end = Math.min(rawLine.length(), offset + NATIVE_CHARS_PER_LINE);
                lines.add(NativeLine.text(rawLine.substring(offset, end)));
            }
        }
    }

    private static MutableComponent nativeLink(Link link, String language,
                                                Map<String, Integer> firstPageById) {
        ClickEvent clickEvent = null;
        if (link.target() instanceof PageTarget pageTarget) {
            Integer targetPage = firstPageById.get(pageTarget.pageId());
            if (targetPage != null) {
                clickEvent = new ClickEvent(ClickEvent.Action.CHANGE_PAGE,
                        Integer.toString(targetPage + 1));
            }
        } else if (link.target() instanceof UrlTarget urlTarget) {
            clickEvent = new ClickEvent(ClickEvent.Action.OPEN_URL, urlTarget.url());
        }

        MutableComponent component = Component.literal(resolve(link.label(), language));
        if (clickEvent != null) {
            ClickEvent event = clickEvent;
            component.withStyle(style -> style
                    .withColor(ChatFormatting.DARK_AQUA)
                    .withUnderlined(true)
                    .withClickEvent(event));
        }
        return component;
    }

    private static List<Image> imagesInPage(Page page) {
        List<Image> images = new ArrayList<>();
        for (Block block : page.blocks()) {
            if (block instanceof Image image) {
                images.add(image);
            }
        }
        return List.copyOf(images);
    }

    private static String resolve(TextValue value, String language) {
        return value.resolve(language);
    }

    private static String normalizeLanguage(String language) {
        return language == null
                ? DEFAULT_LANGUAGE
                : language.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static Content loadContent() {
        try (InputStream stream = GuidebookCatalog.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                throw new IllegalStateException("Missing guidebook resource: " + RESOURCE_PATH);
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                List<Chapter> chapters = new ArrayList<>();
                for (JsonElement element : requiredArray(root, "chapters")) {
                    chapters.add(parseChapter(element.getAsJsonObject()));
                }
                return new Content(parseText(root.get("title")), string(root, "author"), chapters);
            }
        } catch (IOException | RuntimeException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Chapter parseChapter(JsonObject object) {
        List<Page> pages = new ArrayList<>();
        for (JsonElement element : requiredArray(object, "pages")) {
            pages.add(parsePage(element.getAsJsonObject()));
        }
        return new Chapter(string(object, "id"), parseText(object.get("title")), pages);
    }

    private static Page parsePage(JsonObject object) {
        List<Block> blocks = new ArrayList<>();
        for (JsonElement element : requiredArray(object, "blocks")) {
            blocks.add(parseBlock(element.getAsJsonObject()));
        }
        return new Page(string(object, "id"), parseText(object.get("title")), blocks);
    }

    private static Block parseBlock(JsonObject object) {
        return switch (string(object, "type")) {
            case "paragraph" -> new Paragraph(parseText(object.get("text")));
            case "bullets" -> {
                List<TextValue> items = new ArrayList<>();
                for (JsonElement element : requiredArray(object, "items")) {
                    items.add(parseText(element));
                }
                yield new Bullets(items);
            }
            case "code" -> new Code(parseText(object.get("text")));
            case "image" -> new Image(
                    texture(object.get("texture").getAsString()),
                    object.get("width").getAsInt(),
                    object.get("height").getAsInt(),
                    object.get("source_width").getAsInt(),
                    object.get("source_height").getAsInt(),
                    parseText(object.get("caption")));
            case "link" -> new Link(parseText(object.get("label")), parseTarget(object.getAsJsonObject("target")));
            case "spacer" -> new Spacer();
            default -> throw new IllegalArgumentException("Unknown guidebook block type: " + object);
        };
    }

    private static Target parseTarget(JsonObject object) {
        return switch (string(object, "type")) {
            case "page" -> new PageTarget(string(object, "id"));
            case "url" -> new UrlTarget(string(object, "url"));
            default -> throw new IllegalArgumentException("Unknown guidebook link target: " + object);
        };
    }

    private static TextValue parseText(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("Missing localized guidebook text");
        }
        if (element.isJsonPrimitive()) {
            return new TextValue(Map.of(DEFAULT_LANGUAGE, element.getAsString()));
        }
        JsonObject object = element.getAsJsonObject();
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            values.put(entry.getKey(), entry.getValue().getAsString());
        }
        return new TextValue(values);
    }

    private static JsonArray requiredArray(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("Missing guidebook array '" + name + "'");
        }
        return element.getAsJsonArray();
    }

    private static String string(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing guidebook string '" + name + "'");
        }
        return element.getAsString();
    }

    private static ResourceLocation texture(String raw) {
        return raw.indexOf(':') >= 0
                ? ResourceLocation.parse(raw)
                : ResourceLocation.fromNamespaceAndPath(MODID, raw);
    }

    private record NativeLine(String text, Link link) {
        private static NativeLine text(String text) {
            return new NativeLine(text, null);
        }

        private static NativeLine link(Link link) {
            return new NativeLine("", link);
        }
    }

    private record NativeDraft(List<NativeLine> lines, List<Image> images) {}

    private record NativeBook(List<Component> pages, List<List<Image>> images,
                              Map<String, Integer> firstPageById) {}
}
