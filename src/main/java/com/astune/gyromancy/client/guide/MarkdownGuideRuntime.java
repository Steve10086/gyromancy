package com.astune.gyromancy.client.guide;

import com.astune.gyromancy.Gyromancy;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.favouriteless.modopedia.api.book.BookContent;
import net.favouriteless.modopedia.api.book.Book;
import net.favouriteless.modopedia.api.book.Entry;
import net.favouriteless.modopedia.api.book.Page;
import net.favouriteless.modopedia.api.registries.common.BookRegistry;
import net.favouriteless.modopedia.api.registries.client.BookContentRegistry;
import net.favouriteless.modopedia.book.BookContentImpl;
import net.favouriteless.modopedia.book.EntryImpl;
import net.favouriteless.modopedia.book.PageComponentHolder;
import net.favouriteless.modopedia.book.PageImpl;
import net.favouriteless.modopedia.book.variables.ObjectVariable;
import net.favouriteless.modopedia.book.variables.VariableLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds Markdown-backed entries after Modopedia has loaded its book data.
 *
 * <p>The entry JSON remains the metadata source for Modopedia, but its static
 * page list is deliberately not used. The Markdown reader creates the native
 * Modopedia Page objects from the current resource contents at runtime.</p>
 */
public final class MarkdownGuideRuntime {

    private static final ResourceLocation BOOK_ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "guidebook");

    private static BookContent lastProcessedContent;

    private MarkdownGuideRuntime() {}

    /**
     * Called on the client tick. Modopedia loads content asynchronously during
     * resource reload, so this waits until its registry contains the book and
     * also naturally retries after a later resource reload replaces it.
     */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        BookContentRegistry registry = BookContentRegistry.get();
        BookContent current = registry.getContent(BOOK_ID);
        Book book = BookRegistry.get().getBook(BOOK_ID);
        if (current == null || book == null || current == lastProcessedContent) return;

        BookContent dynamic = rebuild(current, book, minecraft.getResourceManager());
        lastProcessedContent = current;
        if (dynamic != null) {
            registry.register(BOOK_ID, dynamic);
            lastProcessedContent = dynamic;
            Gyromancy.LOGGER.debug("[Gyromancy] Rebuilt Markdown guide pages from runtime resources");
        }
    }

    private static BookContent rebuild(BookContent source, Book book, ResourceManager resources) {
        Map<String, BookContent.LocalisedBookContent> localised = new LinkedHashMap<>();
        boolean changed = false;

        for (String language : source.getLanguages()) {
            BookContent.LocalisedBookContent sourceLanguage = source.getContent(language);
            if (sourceLanguage == null) continue;

            Map<String, net.favouriteless.modopedia.api.book.Category> categories = new LinkedHashMap<>();
            for (String categoryId : sourceLanguage.getCategoryIds()) {
                categories.put(categoryId, sourceLanguage.getCategory(categoryId));
            }

            Map<String, Entry> entries = new LinkedHashMap<>();
            for (String entryId : sourceLanguage.getEntryIds()) {
                Entry sourceEntry = sourceLanguage.getEntry(entryId);
                MarkdownSpec spec = findMarkdown(resources, BOOK_ID, language, entryId);
                if (sourceEntry == null || spec == null) {
                    entries.put(entryId, sourceEntry);
                    continue;
                }

                try {
                    int pageCount = MarkdownPageComponent.pageCount(
                            resources, ResourceLocation.parse(spec.file()), book,
                            language, spec.padding(), spec.gap());
                    EntryImpl entry = new EntryImpl(
                            sourceEntry.getTitle(),
                            sourceEntry.getIcon(),
                            sourceEntry.getAssignedItems(),
                            sourceEntry.getAdvancement());
                    entry.addPages(createPages(
                            spec, language, entryId, pageCount, book, Minecraft.getInstance().level));
                    entries.put(entryId, entry);
                    changed = true;
                } catch (RuntimeException exception) {
                    Gyromancy.LOGGER.error(
                            "[Gyromancy] Could not build Markdown pages for {}:{}",
                            language, entryId, exception);
                    entries.put(entryId, sourceEntry);
                }
            }

            localised.put(language, new BookContentImpl.LocalisedBookContentImpl(categories, entries));
        }

        return changed ? new BookContentImpl(localised) : null;
    }

    private static List<Page> createPages(MarkdownSpec spec, String language, String entryId, int pageCount,
                                          Book book, net.minecraft.world.level.Level level) {
        List<Page> pages = new ArrayList<>(pageCount);
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            PageComponentHolder holder = new PageComponentHolder();
            // Match Modopedia's loadPageComponentHolder: page-scoped variables
            // live on the holder, while component-scoped variables are passed to
            // the component itself.
            holder.set("page_num", ObjectVariable.of(pageIndex));
            holder.set("entry", ObjectVariable.of(entryId));

            VariableLookup componentLookup = new VariableLookup();
            componentLookup.set("page_num", ObjectVariable.of(pageIndex));
            componentLookup.set("entry", ObjectVariable.of(entryId));
            componentLookup.set("language", ObjectVariable.of(language));
            componentLookup.set("file", ObjectVariable.of(spec.file()));
            componentLookup.set("page", ObjectVariable.of(pageIndex));
            componentLookup.set("padding", ObjectVariable.of(spec.padding()));
            componentLookup.set("gap", ObjectVariable.of(spec.gap()));
            holder.addComponent(new MarkdownPageComponent(), componentLookup);

            // Modopedia initializes every Page before putting it into the
            // loaded Entry. Without this call the screen receives an empty
            // component list and the page renders as blank.
            PageImpl page = new PageImpl(holder);
            page.init(book, entryId, level);
            pages.add(page);
        }
        return pages;
    }

    private static MarkdownSpec findMarkdown(
            ResourceManager resources, ResourceLocation bookId, String language, String entryId) {
        ResourceLocation entryResource = ResourceLocation.fromNamespaceAndPath(
                bookId.getNamespace(),
                "modopedia/books/" + bookId.getPath() + "/" + language + "/entries/" + entryId + ".json");
        Resource resource = resources.getResource(entryResource).orElse(null);
        if (resource == null) return null;

        try (Reader reader = resource.openAsReader()) {
            JsonObject entry = JsonParser.parseReader(reader).getAsJsonObject();
            for (JsonElement page : entry.getAsJsonArray("pages")) {
                JsonObject pageObject = page.getAsJsonObject();
                for (JsonElement component : pageObject.getAsJsonArray("components")) {
                    JsonObject componentObject = component.getAsJsonObject();
                    if (componentObject.has("type")
                            && componentObject.has("file")
                            && "gyromancy:markdown".equals(componentObject.get("type").getAsString())) {
                        return new MarkdownSpec(
                                componentObject.get("file").getAsString(),
                                intValue(componentObject, "padding", 2),
                                intValue(componentObject, "gap", 3));
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            Gyromancy.LOGGER.error(
                    "[Gyromancy] Could not read guide entry resource {}", entryResource, exception);
        }
        return null;
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        return object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private record MarkdownSpec(String file, int padding, int gap) {}

}
