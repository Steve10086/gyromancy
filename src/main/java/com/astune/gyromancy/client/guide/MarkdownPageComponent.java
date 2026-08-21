package com.astune.gyromancy.client.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.favouriteless.modopedia.api.Lookup;
import net.favouriteless.modopedia.api.book.Book;
import net.favouriteless.modopedia.book.variables.JsonVariable;
import net.favouriteless.modopedia.book.variables.VariableLookup;
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
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads one CommonMark document and exposes one of its physical book pages.
 * The outer Modopedia page supplies page_num, so the normal double-sided book
 * screen renders consecutive Markdown sections on its left and right pages.
 */
public final class MarkdownPageComponent extends AutoLayoutPageComponent {

    private static final Pattern RECIPE_DIRECTIVE = Pattern.compile(
            "(?s)^:::\\s*recipe\\s+([^\\s]+)\\s*\\n\\s*:::$|^\\{\\{recipe\\s+([^}\\s]+)\\s*}}$");
    private static final Pattern BLOCK_DIRECTIVE = Pattern.compile(
            "(?s)^:::\\s*(image|showcase)\\s+([^\\n]+?)\\s*\\n\\s*:::$");
    private static final Pattern TEXT_DIRECTIVE = Pattern.compile(
            "(?s)^:::\\s*text(?:\\s+([^\\n]*))?\\s*\\n(.*?)\\n\\s*:::$");

    @Override
    public void init(Book book, Lookup lookup, Level level) {
        String file = lookup.get("file").asString();
        List<String> pages = splitPages(read(ResourceLocation.parse(file)));
        int pageIndex = lookup.has("page")
                ? lookup.get("page").asInt()
                : (lookup.has("page_num") ? lookup.get("page_num").asInt() : 0);
        String source = pageIndex >= 0 && pageIndex < pages.size() ? pages.get(pageIndex) : "";

        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        VariableLookup expanded = new VariableLookup();
        for (String key : lookup.keys()) {
            expanded.set(key, lookup.get(key));
        }
        expanded.set("components", JsonVariable.of(parse(source), ops));
        super.init(book, expanded, level);
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
