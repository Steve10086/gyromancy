package com.astune.gyromancy.client.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.favouriteless.modopedia.api.Lookup;
import net.favouriteless.modopedia.api.book.Book;
import net.favouriteless.modopedia.api.book.BookTexture;
import net.favouriteless.modopedia.api.registries.client.BookTextureRegistry;
import net.favouriteless.modopedia.book.text.Justify;
import net.favouriteless.modopedia.book.text.TextChunk;
import net.favouriteless.modopedia.book.text.TextParser;
import net.favouriteless.modopedia.book.variables.JsonVariable;
import net.favouriteless.modopedia.book.variables.VariableLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.Level;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads one CommonMark document and exposes one of its physical book pages.
 * The outer Modopedia page supplies page_num, so the normal double-sided book
 * screen renders consecutive Markdown sections on its left and right pages.
 */
public final class MarkdownPageComponent extends AutoLayoutPageComponent {

    private static final Field TEXT_CHUNK_Y = field(TextChunk.class, "y");
    private static final Field TEXT_CHUNK_HEIGHT = field(TextChunk.class, "height");

    private static final Pattern RECIPE_DIRECTIVE = Pattern.compile(
            "(?s)^:::\\s*recipe\\s+([^\\s]+)\\s*\\n\\s*:::$|^\\{\\{recipe\\s+([^}\\s]+)\\s*}}$");
    private static final Pattern BLOCK_DIRECTIVE = Pattern.compile(
            "(?s)^:::\\s*(image|showcase)\\s+([^\\n]+?)\\s*\\n\\s*:::$");
    private static final Pattern TEXT_DIRECTIVE = Pattern.compile(
            "(?s)^:::\\s*text(?:\\s+([^\\n]*))?\\s*\\n(.*?)\\n\\s*:::$");

    @Override
    public void init(Book book, Lookup lookup, Level level) {
        String file = lookup.get("file").asString();
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        String language = lookup.has("language") ? lookup.get("language").asString() : "en_us";
        int padding = Math.max(0, intValue(lookup, "padding", 2));
        int gap = Math.max(0, intValue(lookup, "gap", 3));
        List<JsonArray> pages = paginate(
                read(resources, ResourceLocation.parse(file)), book, language, padding, gap);
        int pageIndex = lookup.has("page")
                ? lookup.get("page").asInt()
                : (lookup.has("page_num") ? lookup.get("page_num").asInt() : 0);

        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        VariableLookup expanded = new VariableLookup();
        for (String key : lookup.keys()) {
            expanded.set(key, lookup.get(key));
        }
        JsonArray components = pageIndex >= 0 && pageIndex < pages.size()
                ? pages.get(pageIndex) : new JsonArray();
        expanded.set("components", JsonVariable.of(components, ops));
        super.init(book, expanded, level);
    }

    static int pageCount(ResourceManager resources, ResourceLocation location, Book book,
                         String language, int padding, int gap) {
        return paginate(read(resources, location), book, language, padding, gap).size();
    }

    private static List<String> splitPages(String markdown) {
        return Arrays.asList(markdown.split("(?m)^\\s*<!--\\s*page\\s*-->\\s*$", -1));
    }

    private static String read(ResourceManager resources, ResourceLocation location) {
        Resource resource = resources.getResource(location).orElse(null);
        if (resource == null) {
            throw new IllegalArgumentException("Missing Guide Markdown resource: " + location);
        }
        try (var reader = resource.openAsReader()) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (content.length() > 0) content.append('\n');
                content.append(line);
            }
            return content.toString();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read Guide Markdown resource: " + location, exception);
        }
    }

    /**
     * Converts the CommonMark blocks into native Modopedia component JSON and
     * packs those components into the actual texture page height. Explicit
     * page markers flush the current page, while ordinary content only wraps
     * when its measured native component height no longer fits.
     */
    private static List<JsonArray> paginate(String markdown, Book book, String language,
                                            int padding, int gap) {
        int pageHeight = pageHeight(book);
        PageBuilder builder = new PageBuilder(book, language, padding, gap, pageHeight);
        List<String> sections = splitPages(markdown);

        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            if (sectionIndex > 0) {
                builder.flush();
            }
            JsonArray components = parse(sections.get(sectionIndex));
            for (JsonElement element : components) {
                if (element.isJsonObject()) {
                    builder.add(element.getAsJsonObject());
                }
            }
        }

        builder.finish();
        return builder.pages;
    }

    private static int pageHeight(Book book) {
        if (book != null) {
            BookTexture texture = BookTextureRegistry.get().getTexture(book.getTexture());
            if (texture != null && !texture.pages().isEmpty()) {
                return texture.pages().get(0).height();
            }
        }
        return 150;
    }

    private static JsonArray parse(String markdown) {
        JsonArray components = new JsonArray();
        Node document = Parser.builder().build().parse(markdown);
        for (Node block = document.getFirstChild(); block != null; block = block.getNext()) {
            addBlock(components, block);
        }
        return components;
    }

    private static void addBlock(JsonArray components, Node block) {
        if (block instanceof Heading heading) {
            components.add(header(inlineText(heading).trim(), heading.getLevel() == 1));
        } else if (block instanceof Paragraph paragraph) {
            String raw = rawText(paragraph).trim();
            Matcher recipe = RECIPE_DIRECTIVE.matcher(raw);
            Matcher directive = BLOCK_DIRECTIVE.matcher(raw);
            Matcher textDirective = TEXT_DIRECTIVE.matcher(raw);
            if (recipe.matches()) {
                String recipeId = recipe.group(1) != null ? recipe.group(1) : recipe.group(2);
                components.add(recipe(recipeId));
            } else if (textDirective.matches()) {
                addText(components, textDirective.group(2), textDirective.group(1));
            } else if (directive.matches()) {
                String payload = directive.group(2).trim();
                components.add(directive.group(1).equals("image") ? image(payload) : showcase(payload));
            } else {
                addText(components, inlineText(paragraph));
            }
        } else if (block instanceof BulletList bulletList) {
            addList(components, bulletList, false);
        } else if (block instanceof OrderedList orderedList) {
            addList(components, orderedList, true);
        } else if (block instanceof BlockQuote) {
            addText(components, prefixLines(inlineText(block), "┃ "));
        } else if (block instanceof FencedCodeBlock fencedCode) {
            addText(components, fencedCode.getLiteral());
        } else if (block instanceof IndentedCodeBlock indentedCode) {
            addText(components, indentedCode.getLiteral());
        } else if (block instanceof ThematicBreak) {
            JsonObject separator = new JsonObject();
            separator.addProperty("type", "modopedia:separator");
            components.add(separator);
        } else if (block instanceof HtmlBlock htmlBlock) {
            addText(components, htmlBlock.getLiteral());
        } else {
            addText(components, inlineText(block));
        }
    }

    private static void addList(JsonArray components, Node list, boolean ordered) {
        int index = ordered ? 1 : 0;
        for (Node child = list.getFirstChild(); child != null; child = child.getNext()) {
            if (!(child instanceof ListItem)) continue;
            String prefix = ordered ? index++ + ". " : "• ";
            addText(components, prefix + inlineText(child).trim());
        }
    }

    private static String rawText(Node node) {
        if (node instanceof Text text) return text.getLiteral();
        if (node instanceof Code code) return code.getLiteral();
        if (node instanceof HtmlInline html) return html.getLiteral();
        if (node instanceof FencedCodeBlock fencedCode) return fencedCode.getLiteral();
        if (node instanceof IndentedCodeBlock indentedCode) return indentedCode.getLiteral();
        if (node instanceof SoftLineBreak || node instanceof HardLineBreak) return "\n";
        return childText(node, false);
    }

    private static String inlineText(Node node) {
        if (node instanceof Text text) return text.getLiteral();
        if (node instanceof Code code) return code.getLiteral();
        if (node instanceof HtmlInline html) return html.getLiteral();
        if (node instanceof Image image) return childText(image, false);
        if (node instanceof Link link) {
            String target = link.getDestination();
            String formatter = target.startsWith("http://") || target.startsWith("https://")
                    ? "l:" + target
                    : "el:" + target;
            return "$(" + formatter + ")" + childText(link, true) + "$(/l)";
        }
        if (node instanceof StrongEmphasis) return "$(b)" + childText(node, true) + "$(/b)";
        if (node instanceof Emphasis) return "$(i)" + childText(node, true) + "$(/i)";
        if (node instanceof SoftLineBreak || node instanceof HardLineBreak) return "\n";
        return childText(node, true);
    }

    private static String childText(Node node, boolean formatted) {
        StringBuilder result = new StringBuilder();
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            result.append(formatted ? inlineText(child) : rawText(child));
        }
        return result.toString();
    }

    private static String prefixLines(String text, String prefix) {
        return prefix + text.replace("\n", "\n" + prefix);
    }

    private static void addText(JsonArray components, String text) {
        addText(components, text, null);
    }

    private static void addText(JsonArray components, String text, String options) {
        if (text == null || text.isBlank()) return;
        JsonObject component = new JsonObject();
        component.addProperty("type", "modopedia:text");
        component.addProperty("width", 96);
        component.addProperty("line_height", 9);
        component.addProperty("text", text);
        if (options != null) {
            String justify = option(options.split("\\s+"), "justify");
            if (justify != null) component.addProperty("justify", justify);
        }
        components.add(component);
    }

    private static JsonObject header(String text, boolean centered) {
        JsonObject component = new JsonObject();
        component.addProperty("type", "modopedia:text");
        component.addProperty("width", 96);
        component.addProperty("line_height", centered ? 12 : 10);
        component.addProperty("justify", centered ? "center" : "left");
        component.addProperty("text", "$(b)" + text + "$(/b)");
        return component;
    }

    private static JsonObject recipe(String recipe) {
        JsonObject component = new JsonObject();
        component.addProperty("template", "modopedia:recipe/crafting");
        component.addProperty("processor", "modopedia:crafting_recipe");
        component.addProperty("recipe", recipe);
        return component;
    }

    private static JsonObject image(String payload) {
        String[] tokens = payload.split("\\s+");
        JsonObject component = new JsonObject();
        component.addProperty("type", "modopedia:image");
        JsonArray images = new JsonArray();
        for (String image : tokens[0].split(",")) images.add(image);
        component.add("images", images);
        component.addProperty("width", optionInt(tokens, "width", 36));
        component.addProperty("height", optionInt(tokens, "height", 36));
        return component;
    }

    private static JsonObject showcase(String payload) {
        String[] tokens = payload.split("\\s+");
        JsonObject component = new JsonObject();
        component.addProperty("type", "modopedia:showcase");
        JsonArray items = new JsonArray();
        for (String item : tokens[0].split(",")) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", item);
            items.add(entry);
        }
        component.add("items", items);
        component.addProperty("width", optionInt(tokens, "width", 80));
        component.addProperty("height", optionInt(tokens, "height", 32));
        component.addProperty("scale", optionFloat(tokens, "scale", 0.8F));
        return component;
    }

    private static int estimateHeight(Book book, String language, JsonObject raw) {
        if (raw.has("layout_height")) {
            return Math.max(1, raw.get("layout_height").getAsInt());
        }
        if (isText(raw)) {
            return Math.max(9, nativeTextHeight(book, language, raw));
        }
        if (raw.has("type")) {
            String type = raw.get("type").getAsString();
            if (type.equals("modopedia:showcase")) {
                return Math.max(1, intValue(raw, "height", 32));
            }
            if (type.equals("modopedia:header")) {
                return 12;
            }
            if (type.equals("modopedia:image")) {
                return Math.max(1, intValue(raw, "height", 16));
            }
            if (type.equals("modopedia:separator")) {
                return 4;
            }
            if (type.equals("modopedia:item") || type.equals("modopedia:item_gallery")) {
                return 24;
            }
        }
        if (raw.has("template")) {
            String template = raw.get("template").getAsString();
            if (template.startsWith("modopedia:recipe/")) {
                return 52;
            }
            if (template.contains("grid_framed_item_gallery")) {
                return 52;
            }
        }
        return 20;
    }

    private static int nativeTextHeight(Book book, String language, JsonObject raw) {
        String text = raw.has("text") ? raw.get("text").getAsString() : "";
        if (text.isBlank()) return 0;

        ResourceLocation font = book == null
                ? ResourceLocation.fromNamespaceAndPath("modopedia", "default")
                : book.getFont();
        int colour = book == null ? 0x2D1F2A : book.getTextColour();
        Style style = Style.EMPTY.withFont(font).withColor(colour);
        int width = intValue(raw, "width", book == null ? 96 : book.getLineWidth());
        int lineHeight = intValue(raw, "line_height", 9);
        Justify justify = justify(raw);

        List<TextChunk> chunks = TextParser.parse(text, style, width, lineHeight, language, justify);
        int height = 0;
        try {
            for (TextChunk chunk : chunks) {
                height = Math.max(height, TEXT_CHUNK_Y.getInt(chunk) + TEXT_CHUNK_HEIGHT.getInt(chunk));
            }
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to measure native Modopedia text", exception);
        }
        return height;
    }

    private static Justify justify(JsonObject raw) {
        if (!raw.has("justify")) return Justify.LEFT;
        try {
            return Justify.valueOf(raw.get("justify").getAsString().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Justify.LEFT;
        }
    }

    private static boolean isText(JsonObject raw) {
        return raw.has("type") && "modopedia:text".equals(raw.get("type").getAsString());
    }

    /**
     * Splits a text component at native line capacity without breaking
     * Modopedia formatter scopes. Open formatters are reopened on the next
     * fragment and temporarily closed at the end of the current fragment.
     */
    private static List<JsonObject> splitTextComponent(JsonObject raw, Book book,
                                                        String language, int maxHeight) {
        if (!isText(raw) || maxHeight < 9
                || estimateHeight(book, language, raw) <= maxHeight) {
            return List.of(raw);
        }

        String text = raw.get("text").getAsString();
        List<String> tokens = tokenize(text);
        List<JsonObject> fragments = new ArrayList<>();
        List<String> active = new ArrayList<>();
        int start = 0;

        while (start < tokens.size()) {
            StringBuilder body = new StringBuilder();
            List<String> state = new ArrayList<>(active);
            int bestEnd = -1;
            List<String> bestState = null;
            int boundaryEnd = -1;
            List<String> boundaryState = null;

            for (int end = start; end < tokens.size(); end++) {
                String token = tokens.get(end);
                body.append(token);
                applyFormatter(state, token);

                JsonObject candidate = copyText(raw, wrapFragment(active, body.toString(), state));
                if (nativeTextHeight(book, language, candidate) > maxHeight) {
                    break;
                }

                bestEnd = end + 1;
                bestState = new ArrayList<>(state);
                if (isTextBoundary(token)) {
                    boundaryEnd = bestEnd;
                    boundaryState = new ArrayList<>(state);
                }
            }

            if (boundaryEnd > start) {
                bestEnd = boundaryEnd;
                bestState = boundaryState;
            }
            if (bestEnd < 0) {
                // A single unbreakable glyph/token must still make progress.
                bestEnd = Math.min(start + 1, tokens.size());
                bestState = new ArrayList<>(active);
                applyFormatter(bestState, tokens.get(start));
            }

            StringBuilder fragmentBody = new StringBuilder();
            for (int index = start; index < bestEnd; index++) {
                fragmentBody.append(tokens.get(index));
            }
            fragments.add(copyText(raw, wrapFragment(active, fragmentBody.toString(), bestState)));
            active = bestState;
            start = bestEnd;
        }
        return fragments.isEmpty() ? List.of(raw) : fragments;
    }

    private static JsonObject copyText(JsonObject source, String text) {
        JsonObject copy = source.deepCopy();
        copy.addProperty("text", text);
        return copy;
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher formatter = FORMATTER_TOKEN.matcher(text);
        int index = 0;
        while (index < text.length()) {
            formatter.region(index, text.length());
            if (formatter.lookingAt()) {
                tokens.add(formatter.group());
                index = formatter.end();
            } else {
                int codePoint = text.codePointAt(index);
                tokens.add(new String(Character.toChars(codePoint)));
                index += Character.charCount(codePoint);
            }
        }
        return tokens;
    }

    private static String wrapFragment(List<String> active, String body, List<String> state) {
        StringBuilder result = new StringBuilder();
        for (String formatter : active) result.append(formatter);
        result.append(body);
        for (int index = state.size() - 1; index >= 0; index--) {
            result.append(closeFormatter(state.get(index)));
        }
        return result.toString();
    }

    private static void applyFormatter(List<String> active, String token) {
        if (!FORMATTER_TOKEN.matcher(token).matches()) return;
        String body = token.substring(2, token.length() - 1);
        if (body.isEmpty()) {
            active.clear();
        } else if (body.startsWith("/")) {
            String name = body.substring(1).split(":", 2)[0];
            for (int index = active.size() - 1; index >= 0; index--) {
                if (formatterName(active.get(index)).equals(name)) {
                    active.remove(index);
                    break;
                }
            }
        } else {
            active.add(token);
        }
    }

    private static String formatterName(String token) {
        String body = token.substring(2, token.length() - 1);
        int separator = body.indexOf(':');
        return separator < 0 ? body : body.substring(0, separator);
    }

    private static String closeFormatter(String token) {
        String name = formatterName(token);
        if (name.equals("b") || name.equals("i") || name.equals("l") || name.equals("el")) {
            return "$(/" + (name.equals("el") ? "l" : name) + ")";
        }
        return "$()";
    }

    private static boolean isTextBoundary(String token) {
        return token.equals("\n") || (!token.isEmpty() && token.codePoints().allMatch(Character::isWhitespace));
    }

    private static int intValue(Lookup lookup, String key, int fallback) {
        return lookup.has(key) ? lookup.get(key).asInt() : fallback;
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive()
                ? object.get(key).getAsInt()
                : fallback;
    }

    private static Field field(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static final Pattern FORMATTER_TOKEN = Pattern.compile("\\$\\([^$()]*\\)");

    private static final class PageBuilder {
        private final List<JsonArray> pages = new ArrayList<>();
        private final Book book;
        private final String language;
        private final int padding;
        private final int gap;
        private final int limit;
        private JsonArray current = new JsonArray();
        private int used;

        private PageBuilder(Book book, String language, int padding, int gap, int pageHeight) {
            this.book = book;
            this.language = language;
            this.padding = padding;
            this.gap = gap;
            this.limit = Math.max(padding + 9, pageHeight - padding);
            this.used = padding;
        }

        private void add(JsonObject raw) {
            int height = estimateHeight(book, language, raw);
            if (isText(raw)) {
                int remaining = limit - used;
                if (!current.isEmpty() && height > remaining && remaining >= 9) {
                    List<JsonObject> fragments = splitTextComponent(raw, book, language, remaining);
                    if (fragments.size() > 1) {
                        for (JsonObject fragment : fragments) add(fragment);
                        return;
                    }
                }

                int fullPageContent = limit - padding;
                if (current.isEmpty() && height > fullPageContent) {
                    List<JsonObject> fragments = splitTextComponent(raw, book, language, fullPageContent);
                    if (fragments.size() > 1) {
                        for (JsonObject fragment : fragments) add(fragment);
                        return;
                    }
                }
            }

            if (!current.isEmpty() && used + height > limit) {
                flush();
            }
            current.add(raw);
            used += height + gap;
        }

        private void flush() {
            pages.add(current);
            current = new JsonArray();
            used = padding;
        }

        private void finish() {
            pages.add(current);
        }
    }

    private static int optionInt(String[] tokens, String name, int fallback) {
        String value = option(tokens, name);
        return value == null ? fallback : Integer.parseInt(value);
    }

    private static float optionFloat(String[] tokens, String name, float fallback) {
        String value = option(tokens, name);
        return value == null ? fallback : Float.parseFloat(value);
    }

    private static String option(String[] tokens, String name) {
        String prefix = name + "=";
        for (String token : tokens) {
            if (token.startsWith(prefix)) return token.substring(prefix.length());
        }
        return null;
    }
}
