package com.astune.gyromancy.client.guide;

import com.astune.gyromancy.Gyromancy;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.favouriteless.modopedia.api.Lookup;
import net.favouriteless.modopedia.api.Variable;
import net.favouriteless.modopedia.api.book.Book;
import net.favouriteless.modopedia.api.book.page_components.BookRenderContext;
import net.favouriteless.modopedia.api.book.page_components.PageComponent;
import net.favouriteless.modopedia.api.book.page_components.PageWidgetHolder;
import net.favouriteless.modopedia.api.registries.client.PageComponentRegistry;
import net.favouriteless.modopedia.api.registries.client.TemplateRegistry;
import net.favouriteless.modopedia.book.PageComponentHolder;
import net.favouriteless.modopedia.book.Template;
import net.favouriteless.modopedia.book.text.TextChunk;
import net.favouriteless.modopedia.book.variables.JsonVariable;
import net.favouriteless.modopedia.book.variables.ObjectVariable;
import net.favouriteless.modopedia.book.variables.RemoteVariable;
import net.favouriteless.modopedia.book.variables.VariableLookup;
import net.favouriteless.modopedia.client.page_components.GalleryPageComponent;
import net.favouriteless.modopedia.client.page_components.TemplatePageComponent;
import net.favouriteless.modopedia.client.page_components.TextPageComponent;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A small vertical flow layout for Guide pages.
 *
 * Children are still ordinary Modopedia components, so templates keep their
 * native rendering and interaction. The layout only supplies the outer
 * component position and lets each child retain its own internal behavior.
 */
public class AutoLayoutPageComponent extends PageComponent {

    private static final Field JSON_INTERNAL = field(JsonVariable.class, "internal");
    private static final Field TEXT_CHUNKS = field(TextPageComponent.class, "textChunks");
    private static final Field CHUNK_Y = field(TextChunk.class, "y");
    private static final Field CHUNK_HEIGHT = field(TextChunk.class, "height");

    private final List<LayoutChild> children = new ArrayList<>();
    private int layoutHeight;

    @Override
    public void init(Book book, Lookup lookup, Level level) {
        super.init(book, lookup, level);
        children.clear();
        layoutHeight = 0;

        JsonArray componentData = jsonArray(lookup.get("components"));
        if (componentData == null) {
            throw new IllegalArgumentException("gyromancy:auto_layout requires a components array");
        }

        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        int padding = Math.max(0, intValue(lookup, "padding", 2));
        int gap = Math.max(0, intValue(lookup, "gap", 3));
        int cursor = padding;

        for (JsonElement element : componentData) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject raw = element.getAsJsonObject();
            LayoutChild child = createChild(raw, lookup, ops, false, padding, cursor);
            try {
                child.component.init(book, child.lookup, level);
                children.add(child);
                cursor += measureHeight(child.component, raw) + gap;
            } catch (RuntimeException exception) {
                Gyromancy.LOGGER.error("Failed to initialize Guide auto-layout child", exception);
                throw exception;
            }
        }
        layoutHeight = children.isEmpty() ? padding : cursor - gap;
    }

    public int getLayoutHeight() {
        return layoutHeight;
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, BookRenderContext context,
                       int mouseX, int mouseY, float partialTicks) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        for (LayoutChild child : children) {
            child.component.render(graphics, context, mouseX - x, mouseY - y, partialTicks);
        }
        graphics.pose().popPose();
    }

    @Override
    public void initWidgets(PageWidgetHolder widgetHolder, BookRenderContext context) {
        for (LayoutChild child : children) {
            child.component.initWidgets(widgetHolder, context);
        }
    }

    @Override
    public void tick(BookRenderContext context) {
        for (LayoutChild child : children) {
            child.component.tick(context);
        }
    }

    @Override
    public boolean mouseClicked(BookRenderContext context, double mouseX, double mouseY, int button) {
        return forEachEvent((child, x, y) -> child.component.mouseClicked(context, x, y, button), mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(BookRenderContext context, double mouseX, double mouseY, int button) {
        return forEachEvent((child, x, y) -> child.component.mouseReleased(context, x, y, button), mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(BookRenderContext context, double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        return forEachEvent((child, x, y) -> child.component.mouseDragged(context, x, y, button, dragX, dragY), mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(BookRenderContext context, double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        return forEachEvent((child, x, y) -> child.component.mouseScrolled(context, x, y, scrollX, scrollY), mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(BookRenderContext context, int keyCode, int scanCode, int modifiers) {
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).component.keyPressed(context, keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyReleased(BookRenderContext context, int keyCode, int scanCode, int modifiers) {
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).component.keyReleased(context, keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(BookRenderContext context, char codePoint, int modifiers) {
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).component.charTyped(context, codePoint, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        double localX = mouseX - x;
        double localY = mouseY - y;
        for (LayoutChild child : children) {
            if (child.component.isMouseOver(localX, localY)) {
                return true;
            }
        }
        return false;
    }

    private boolean forEachEvent(PageEvent event, double mouseX, double mouseY) {
        double localX = mouseX - x;
        double localY = mouseY - y;
        for (int i = children.size() - 1; i >= 0; i--) {
            LayoutChild child = children.get(i);
            if (event.handle(child, localX, localY)) {
                return true;
            }
        }
        return false;
    }

    private LayoutChild createChild(JsonObject raw, Lookup parentLookup, RegistryOps<JsonElement> ops,
                                    boolean explicitPosition, int childX, int childY) {
        PageComponent component;
        if (raw.has("type")) {
            ResourceLocation id = ResourceLocation.parse(raw.get("type").getAsString());
            Supplier<PageComponent> factory = PageComponentRegistry.get().get(id);
            if (factory == null) {
                throw new IllegalArgumentException("Unknown Modopedia page component: " + id);
            }
            component = factory.get();
        } else if (raw.has("template")) {
            ResourceLocation id = ResourceLocation.parse(raw.get("template").getAsString());
            Template template = TemplateRegistry.get().getTemplate(id);
            if (template == null) {
                throw new IllegalArgumentException("Unknown Modopedia template: " + id);
            }
            component = new TemplatePageComponent(buildHolder(template.getData(), ops));
        } else if (raw.has("components")) {
            component = new GalleryPageComponent(buildHolder(raw, ops));
        } else {
            throw new IllegalArgumentException("Guide component has neither type, template, nor components");
        }

        VariableLookup childLookup = new VariableLookup();
        childLookup.set("page_num", ObjectVariable.of(pageNum));
        childLookup.set("entry", ObjectVariable.of(entryId));
        childLookup.set("language", ObjectVariable.of(language));
        for (Map.Entry<String, JsonElement> entry : raw.entrySet()) {
            if (entry.getKey().equals("type")) {
                continue;
            }
            childLookup.set(entry.getKey(), variable(entry.getValue(), parentLookup, ops));
        }
        if (!explicitPosition) {
            childLookup.set("x", ObjectVariable.of(childX));
            childLookup.set("y", ObjectVariable.of(childY));
        }
        return new LayoutChild(component, childLookup);
    }

    private PageComponentHolder buildHolder(JsonObject source, RegistryOps<JsonElement> ops) {
        PageComponentHolder holder = new PageComponentHolder();
        holder.set("page_num", ObjectVariable.of(pageNum));
        holder.set("entry", ObjectVariable.of(entryId));
        holder.set("language", ObjectVariable.of(language));

        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            if (!entry.getKey().equals("components")) {
                holder.set(entry.getKey(), JsonVariable.of(entry.getValue(), ops));
            }
        }

        JsonArray nested = source.has("components") ? source.getAsJsonArray("components") : null;
        if (nested != null) {
            for (JsonElement element : nested) {
                if (element.isJsonObject()) {
                    LayoutChild child = createChild(element.getAsJsonObject(), holder, ops, true, 0, 0);
                    holder.addComponent(child.component, child.lookup);
                }
            }
        }
        return holder;
    }

    private static Variable variable(JsonElement value, Lookup parentLookup, RegistryOps<JsonElement> ops) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String string = value.getAsString();
            if (string.startsWith("#")) {
                return RemoteVariable.of(string.substring(1), parentLookup);
            }
        }
        return JsonVariable.of(value, ops);
    }

    private static JsonArray jsonArray(Variable variable) {
        if (!(variable instanceof JsonVariable)) {
            return null;
        }
        try {
            JsonElement element = (JsonElement) JSON_INTERNAL.get(variable);
            return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to read Modopedia component data", exception);
        }
    }

    private static int measureHeight(PageComponent component, JsonObject raw) {
        if (raw.has("layout_height")) {
            return Math.max(1, raw.get("layout_height").getAsInt());
        }
        if (component instanceof TextPageComponent) {
            int height = textHeight(component);
            return Math.max(9, height);
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

    private static int textHeight(PageComponent component) {
        try {
            Object chunksValue = TEXT_CHUNKS.get(component);
            if (!(chunksValue instanceof List<?> chunks)) {
                return 0;
            }
            int max = 0;
            for (Object chunk : chunks) {
                int chunkY = (int) CHUNK_Y.get(chunk);
                int chunkHeight = (int) CHUNK_HEIGHT.get(chunk);
                max = Math.max(max, chunkY + chunkHeight);
            }
            return max;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to measure Modopedia text", exception);
        }
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

    @FunctionalInterface
    private interface PageEvent {
        boolean handle(LayoutChild child, double mouseX, double mouseY);
    }

    private record LayoutChild(PageComponent component, Lookup lookup) {}
}
