package com.astune.gyromancy.client.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.favouriteless.modopedia.api.Lookup;
import net.favouriteless.modopedia.api.book.Book;
import net.favouriteless.modopedia.api.book.BookTexture;
import net.favouriteless.modopedia.api.book.page_components.BookRenderContext;
import net.favouriteless.modopedia.api.book.page_components.PageComponent;
import net.favouriteless.modopedia.api.book.page_components.PageWidgetHolder;
import net.favouriteless.modopedia.api.registries.client.BookTextureRegistry;
import net.favouriteless.modopedia.book.variables.JsonVariable;
import net.favouriteless.modopedia.book.variables.VariableLookup;
import net.favouriteless.modopedia.client.page_widgets.PageImageButton;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads one CommonMark document and presents it as a pageable collection of
 * native Modopedia components. A line containing {@code <!-- page -->} keeps
 * an intentional page break; long sections are split automatically when the
 * flow layout exceeds the current book page.
 */
public final class MarkdownPageComponent extends PageComponent {

    private static final Pattern RECIPE_DIRECTIVE = Pattern.compile(
            "(?s)^:::\\s*recipe\\s+([^\\s]+)\\s*\\n\\s*:::$|^\\{\\{recipe\\s+([^}\\s]+)\\s*}}$");
    private static final Pattern BLOCK_DIRECTIVE = Pattern.compile(
            "(?s)^:::\\s*(image|showcase)\\s+([^\\n]+?)\\s*\\n\\s*:::$");
    private static final Pattern TEXT_DIRECTIVE = Pattern.compile(
            "(?s)^:::\\s*text(?:\\s+([^\\n]*))?\\s*\\n(.*?)\\n\\s*:::$");

    private final List<AutoLayoutPageComponent> pages = new ArrayList<>();
    private int selectedPage;
    private int width;
    private int height;
    private PageImageButton previousButton;
    private PageImageButton nextButton;

    @Override
    public void init(Book book, Lookup lookup, Level level) {
        super.init(book, lookup, level);
        pages.clear();
        selectedPage = 0;

        String file = lookup.get("file").asString();
        String markdown = read(ResourceLocation.parse(file));
        BookTexture texture = BookTextureRegistry.get().getTexture(book.getTexture());
        BookTexture.Rectangle page = texture.pages().get(Math.floorMod(pageNum, texture.pages().size()));
        width = intValue(lookup, "width", page.width());
        height = intValue(lookup, "height", page.height());

        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        for (String source : splitPages(markdown)) {
            addPaginatedSource(book, lookup, level, ops, parse(source));
        }
        if (pages.isEmpty()) {
            pages.add(createPage(book, lookup, level, ops, new JsonArray()));
        }
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, BookRenderContext context,
                       int mouseX, int mouseY, float partialTicks) {
        pages.get(selectedPage).render(graphics, context, mouseX, mouseY, partialTicks);
    }

    @Override
    public void initWidgets(PageWidgetHolder widgetHolder, BookRenderContext context) {
        for (AutoLayoutPageComponent page : pages) {
            page.initWidgets(widgetHolder, context);
        }
        if (pages.size() < 2 || previousButton != null) {
            return;
        }

        BookTexture texture = context.getBookTexture();
        BookTexture.FixedRectangle left = texture.left();
        BookTexture.FixedRectangle right = texture.right();
        previousButton = widgetHolder.addRenderableWidget(new PageImageButton(
                texture.location(), x, y + height - left.height(), left.width(), left.height(),
                left.u(), left.v(), texture.texWidth(), texture.texHeight(), button -> changePage(-1)));
        nextButton = widgetHolder.addRenderableWidget(new PageImageButton(
                texture.location(), x + width - right.width(), y + height - right.height(),
                right.width(), right.height(), right.u(), right.v(), texture.texWidth(), texture.texHeight(),
                button -> changePage(1)));
        updateButtons();
    }

    @Override
    public void tick(BookRenderContext context) {
        pages.get(selectedPage).tick(context);
    }

    @Override
    public boolean mouseClicked(BookRenderContext context, double mouseX, double mouseY, int button) {
        return pages.get(selectedPage).mouseClicked(context, mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(BookRenderContext context, double mouseX, double mouseY, int button) {
        return pages.get(selectedPage).mouseReleased(context, mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(BookRenderContext context, double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        return pages.get(selectedPage).mouseDragged(context, mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(BookRenderContext context, double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        return pages.get(selectedPage).mouseScrolled(context, mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(BookRenderContext context, int keyCode, int scanCode, int modifiers) {
        return pages.get(selectedPage).keyPressed(context, keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(BookRenderContext context, int keyCode, int scanCode, int modifiers) {
        return pages.get(selectedPage).keyReleased(context, keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(BookRenderContext context, char codePoint, int modifiers) {
        return pages.get(selectedPage).charTyped(context, codePoint, modifiers);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return pages.get(selectedPage).isMouseOver(mouseX, mouseY);
    }

    private void addPaginatedSource(Book book, Lookup lookup, Level level, RegistryOps<JsonElement> ops,
                                    JsonArray components) {
        JsonArray current = new JsonArray();
        for (JsonElement component : components) {
            current.add(component);
            AutoLayoutPageComponent candidate = createPage(book, lookup, level, ops, current);
            if (candidate.getLayoutHeight() > height && current.size() > 1) {
                current.remove(current.size() - 1);
                pages.add(createPage(book, lookup, level, ops, current));
                current = new JsonArray();
                current.add(component);
            }
        }
        if (current.size() > 0) {
            pages.add(createPage(book, lookup, level, ops, current));
        }
    }

    private AutoLayoutPageComponent createPage(Book book, Lookup lookup, Level level,
                                                RegistryOps<JsonElement> ops, JsonArray components) {
        VariableLookup pageLookup = new VariableLookup();
        for (String key : lookup.keys()) {
            pageLookup.set(key, lookup.get(key));
        }
        pageLookup.set("components", JsonVariable.of(components, ops));
        AutoLayoutPageComponent page = new AutoLayoutPageComponent();
        page.init(book, pageLookup, level);
        return page;
    }

    private void changePage(int amount) {
        selectedPage = Math.max(0, Math.min(pages.size() - 1, selectedPage + amount));
        updateButtons();
    }

    private void updateButtons() {
        if (previousButton != null) {
            previousButton.active = selectedPage > 0;
        }
        if (nextButton != null) {
            nextButton.active = selectedPage < pages.size() - 1;
        }
    }

    private static List<String> splitPages(String markdown) {
        return Arrays.asList(markdown.split("(?m)^\\s*<!--\\s*page\\s*-->\\s*$", -1));
    }

    private static String read(ResourceLocation location) {
        Resource resource = Minecraft.getInstance().getResourceManager().getResource(location).orElse(null);
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
            components.add(header(rawText(heading).trim(), heading.getLevel() == 1));
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
                if (directive.group(1).equals("image")) {
                    components.add(image(payload));
                } else {
                    components.add(showcase(payload));
                }
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
        component.addProperty("type", "modopedia:header");
        component.addProperty("text", text);
        component.addProperty("centered", centered);
        component.addProperty("bold", true);
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
        for (String image : tokens[0].split(",")) {
            images.add(image);
        }
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

    private static int intValue(Lookup lookup, String key, int fallback) {
        return lookup.has(key) ? lookup.get(key).asInt() : fallback;
    }
}
