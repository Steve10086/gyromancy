package com.astune.gyromancy.array;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.array.MagicArrayManager;
import com.astune.gyromancy.array.runtime.ArrayEffectLifecycle;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolMatch;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.canvas.CanvasEntity;
import com.astune.gyromancy.registry.ModAttachments;
import com.astune.gyromancy.registry.ModSymbols;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractedGlyph;
import com.astune.gyromancy.symbol.FloodFillExtractor;
import com.astune.gyromancy.symbol.FloodFillScheduler;
import com.astune.gyromancy.symbol.GlyphChunkStorage;
import com.astune.gyromancy.symbol.GlyphMarker;
import com.astune.gyromancy.symbol.ManaPixelDetector;
import com.astune.gyromancy.symbol.SymbolRecognizer;
import com.astune.gyromancy.network.SyncArrayPacket;
import com.astune.gyromancy.network.SyncGlyphPacket;
import com.astune.painter.api.CanvasData;
import com.astune.painter.api.CanvasDataHolder;
import com.astune.painter.api.CanvasFace;
import com.astune.painter.block.CanvasBlockEntity;
import com.astune.painter.event.ServerCanvasUpdateEvent;
import com.astune.painter.network.SyncCanvasPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

/**
 * Bridge between canvas update events and the symbol recognition pipeline.
 */
@EventBusSubscriber(modid = Gyromancy.MODID)
public final class MagicArrayDetector {

    private static final Map<ServerLevel, Map<BlockPos, Integer>> RETRY_PLACED_CANVASES = new WeakHashMap<>();

    private MagicArrayDetector() {}

    static {
        FloodFillScheduler.onBatchExtracted(MagicArrayDetector::onGlyphBatchExtracted);
    }

    @SubscribeEvent
    static void onCanvasUpdatePre(ServerCanvasUpdateEvent.Pre event) {
        if (!(event.getPlayer().level() instanceof ServerLevel level)) return;

        CanvasData oldData = currentCanvasData(level, event.getPos());
        CanvasData newData = event.getCanvasData();

        if (oldData == null || newData == null) return;
        invalidateChangedGlyphs(level, event.getPos(), oldData, newData);
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;

        PacketDistributor.sendToPlayer(player, buildGlyphPacket(level));
        PacketDistributor.sendToPlayer(player, buildArrayPacket(level));
    }

    @SubscribeEvent
    static void onCanvasUpdate(ServerCanvasUpdateEvent event) {
        if (event instanceof ServerCanvasUpdateEvent.Pre) return;

        CanvasData data = event.getCanvasData();
        if (data == null || data.faces().isEmpty()) return;

        Gyromancy.LOGGER.debug("[MagicArrayDetector] Canvas updated at {} ({} faces)",
                event.getPos(), data.faces().size());

        if (event.getPlayer().level() instanceof ServerLevel sl) {
            scanCanvasAt(sl, event.getPos(), data);
        }
    }

    public static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        GlyphChunkStorage.load(level, chunk);
    }

    public static void onBlockReplaced(ServerLevel level, LevelChunk chunk, BlockPos pos,
                                       BlockState oldState, BlockState newState) {
        if (oldState == newState || oldState.equals(newState)) return;

        invalidateGlyphs(level, GlyphChunkStorage.touching(chunk, pos), null, null);
        if (newState.hasBlockEntity()) retryPlacedCanvas(level, pos);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            Map<BlockPos, Integer> retries = RETRY_PLACED_CANVASES.get(level);
            if (retries == null || retries.isEmpty()) continue;

            var it = retries.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                BlockPos pos = entry.getKey();
                int remaining = entry.getValue();
                BlockEntity be = level.getBlockEntity(pos);

                if (be instanceof CanvasDataHolder holder && handlePlacedCanvas(level, pos, holder)) {
                    it.remove();
                } else if (remaining <= 1) {
                    it.remove();
                } else {
                    entry.setValue(remaining - 1);
                }
            }
        }
    }

    private static boolean handlePlacedCanvas(ServerLevel level, BlockPos pos, CanvasDataHolder holder) {
        CanvasData data = holder.painter$getCanvasData();
        int cleared = clearGlyphMarks(data);
        if (data == null || data.faces().isEmpty()) return false;
        if (cleared > 0) syncCanvasAt(level, pos);
        scanCanvasAt(level, pos, data);
        return true;
    }

    private static void retryPlacedCanvas(ServerLevel level, BlockPos pos) {
        RETRY_PLACED_CANVASES.computeIfAbsent(level, ignored -> new java.util.HashMap<>())
                .put(pos.immutable(), 20);
    }

    public static void scanCanvasAt(ServerLevel level, BlockPos pos, CanvasData data) {
        if (data == null || data.faces().isEmpty()) return;

        List<PixelPos> seeds = ManaPixelDetector.scanForMana(level, pos, data);
        if (seeds.isEmpty()) return;

        Gyromancy.LOGGER.debug("[MagicArrayDetector] {} mana seed(s) at {}", seeds.size(), pos);
        FloodFillScheduler.submitBatch(level, seeds);
    }

    private static void invalidateChangedGlyphs(ServerLevel level, BlockPos pos,
                                                CanvasData oldData, CanvasData newData) {
        Set<Integer> invalidGlyphIds = changedGlyphIds(oldData, newData);
        restoreRetainedGlyphMarks(oldData, newData, invalidGlyphIds);
        if (invalidGlyphIds.isEmpty()) return;

        MagicArrayManager mgr = level.getData(ModAttachments.ARRAY_MANAGER);
        Set<PositionedGlyph> glyphs = new HashSet<>();
        for (int glyphId : invalidGlyphIds) {
            PositionedGlyph glyph = mgr.getGlyph(glyphId);
            if (glyph != null) glyphs.add(glyph);
        }
        invalidateGlyphs(level, glyphs, newData, pos);
    }

    private static void invalidateGlyphs(ServerLevel level, Set<PositionedGlyph> glyphs,
                                         CanvasData changedData, BlockPos changedPos) {
        if (glyphs.isEmpty()) return;

        MagicArrayManager mgr = level.getData(ModAttachments.ARRAY_MANAGER);
        Set<BlockPos> syncPositions = new HashSet<>();
        Set<UUID> tornDownArrays = new HashSet<>();

        for (PositionedGlyph glyph : glyphs) {
            glyph = Optional.ofNullable(mgr.getGlyph(glyph.glyphUuid())).orElse(glyph);
            if (glyph == null) continue;

            // Destroy every array lifecycle which captured this glyph. A glyph
            // may be shared by nested arrays, so the single-value index is not
            // sufficient here.
            for (ArrayObject arr : mgr.getArrayObjsForGlyph(glyph.glyphUuid())) {
                if (tornDownArrays.add(arr.arrayId())) {
                    ArrayEffectLifecycle.deactivate(level, arr);
                }
            }

            mgr.unregisterGlyph(glyph.glyphUuid());
            GlyphChunkStorage.remove(level, glyph);
            GlyphMarker.clearMarks(glyph, level);
            if (changedData != null && changedPos != null) {
                clearGlyphPixelsInData(changedData, changedPos, glyph);
            }
            glyph.pixels().stream().map(PixelPos::pos).forEach(syncPositions::add);
            Gyromancy.LOGGER.debug("[MagicArrayDetector] Glyph #{} invalidated by symbol_id change at {}",
                    glyph.glyphId(), glyph.worldPos());
        }

        for (BlockPos syncPos : syncPositions) syncCanvasAt(level, syncPos);
        syncGlyphs(level);
    }

    private static CanvasData currentCanvasData(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CanvasDataHolder holder)) return null;
        return holder.painter$getCanvasData();
    }

    private static void clearGlyphPixelsInData(CanvasData data, BlockPos pos, PositionedGlyph glyph) {
        for (PixelPos pixel : glyph.pixels()) {
            if (!pixel.pos().equals(pos)) continue;

            CanvasFace face = faceByDirection(data, pixel.face());
            if (face == null) continue;

            face.setEffectValue(ManaPixelDetector.GLYPH_ID_KEY, pixel.x(), pixel.y(), 0);
            face.setEffectValue(ManaPixelDetector.SYMBOL_ID_KEY, pixel.x(), pixel.y(), 0);
        }
    }

    private static int clearGlyphMarks(CanvasData data) {
        if (data == null || data.faces().isEmpty()) return 0;

        int changed = 0;
        for (CanvasFace face : data.faces()) {
            int w = face.pixels().getWidth();
            int h = face.pixels().getHeight();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (face.getEffectValue(ManaPixelDetector.GLYPH_ID_KEY, x, y) == 0
                            && face.getEffectValue(ManaPixelDetector.SYMBOL_ID_KEY, x, y) == 0) {
                        continue;
                    }
                    face.setEffectValue(ManaPixelDetector.GLYPH_ID_KEY, x, y, 0);
                    face.setEffectValue(ManaPixelDetector.SYMBOL_ID_KEY, x, y, 0);
                    changed++;
                }
            }
        }
        return changed;
    }

    private static Set<Integer> changedGlyphIds(CanvasData oldData, CanvasData newData) {
        Set<Integer> changed = new HashSet<>();
        for (CanvasFace oldFace : oldData.faces()) {
            CanvasFace newFace = matchingFace(newData, oldFace);
            byte[] oldGlyphs = oldFace.getEffectLayer(ManaPixelDetector.GLYPH_ID_KEY);
            if (oldGlyphs == null) continue;

            int w = oldFace.pixels().getWidth();
            int h = oldFace.pixels().getHeight();
            int count = Math.min(w * h, oldGlyphs.length);
            if (newFace == null) {
                for (int i = 0; i < count; i++) {
                    int glyphId = oldGlyphs[i] & 0xFF;
                    if (glyphId > 0) changed.add(glyphId);
                }
                continue;
            }

            for (int i = 0; i < count; i++) {
                int x = i % w;
                int y = i / w;
                int oldMana = oldFace.getEffectValue(ManaPixelDetector.MANA_EFFECT_KEY, x, y);
                int newMana = newFace.getEffectValue(ManaPixelDetector.MANA_EFFECT_KEY, x, y);
                if (!manaPresenceChanged(oldMana, newMana)) continue;

                // Removing a claimed pixel changes its glyph. Adding a pixel next
                // to a claimed stroke changes that connected glyph as well.
                collectNeighborGlyphIds(oldGlyphs, w, h, x, y, changed);
            }
        }
        return changed;
    }

    static boolean manaPresenceChanged(int oldMana, int newMana) {
        return (oldMana > 0) != (newMana > 0);
    }

    private static void collectNeighborGlyphIds(
            byte[] glyphIds, int width, int height, int centerX, int centerY,
            Set<Integer> result) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int x = centerX + dx;
                int y = centerY + dy;
                if (x < 0 || x >= width || y < 0 || y >= height) continue;
                int index = y * width + x;
                if (index >= glyphIds.length) continue;
                int glyphId = glyphIds[index] & 0xFF;
                if (glyphId > 0) result.add(glyphId);
            }
        }
    }

    private static void restoreRetainedGlyphMarks(
            CanvasData oldData, CanvasData newData, Set<Integer> invalidGlyphIds) {
        for (CanvasFace oldFace : oldData.faces()) {
            CanvasFace newFace = matchingFace(newData, oldFace);
            if (newFace == null) continue;

            byte[] oldGlyphs = oldFace.getEffectLayer(ManaPixelDetector.GLYPH_ID_KEY);
            if (oldGlyphs == null) continue;
            byte[] oldSymbols = oldFace.getEffectLayer(ManaPixelDetector.SYMBOL_ID_KEY);
            int width = oldFace.pixels().getWidth();
            int height = oldFace.pixels().getHeight();
            int count = Math.min(width * height, oldGlyphs.length);
            for (int i = 0; i < count; i++) {
                int glyphId = oldGlyphs[i] & 0xFF;
                if (glyphId == 0 || invalidGlyphIds.contains(glyphId)) continue;
                int symbolId = oldSymbols != null && i < oldSymbols.length
                        ? oldSymbols[i] & 0xFF
                        : 0;
                int x = i % width;
                int y = i / width;
                newFace.setEffectValue(ManaPixelDetector.GLYPH_ID_KEY, x, y, glyphId);
                newFace.setEffectValue(ManaPixelDetector.SYMBOL_ID_KEY, x, y, symbolId);
            }
        }
    }

    private static CanvasFace matchingFace(CanvasData data, CanvasFace oldFace) {
        for (CanvasFace face : data.faces()) {
            if (face.primaryFace() == oldFace.primaryFace()
                    && face.pixels().getWidth() == oldFace.pixels().getWidth()
                    && face.pixels().getHeight() == oldFace.pixels().getHeight()) {
                return face;
            }
        }
        return null;
    }

    private static CanvasFace faceByDirection(CanvasData data, net.minecraft.core.Direction direction) {
        for (CanvasFace face : data.faces()) {
            if (face.primaryFace() == direction) return face;
        }
        return null;
    }

    private static void onGlyphBatchExtracted(
            ServerLevel level, List<ExtractedGlyph> extractedBatch) {
        List<RecognizedGlyph> recognized = new ArrayList<>();
        for (ExtractedGlyph glyph : extractedBatch) {
            Gyromancy.LOGGER.debug(
                    "[MagicArrayDetector] Glyph extracted: {} pixels across {} blocks",
                    glyph.pixels().size(), glyph.blockCount());

            List<SymbolMatch> matches = SymbolRecognizer.recognize(glyph);
            if (matches.isEmpty()) {
                Gyromancy.LOGGER.debug("[MagicArrayDetector] No match - pixels NOT marked");
                continue;
            }

            SymbolMatch best = matches.getFirst();
            if (best.role() != SymbolRole.CENTER_SYMBOL
                    && best.role() != SymbolRole.PARAMETER_RUNE
                    && best.role() != SymbolRole.OUTER_CIRCLE) {
                continue;
            }
            Gyromancy.LOGGER.debug("[MagicArrayDetector] Best match: {} conf={} role={}",
                    best.symbolId(), String.format("%.3f", best.confidence()), best.role());
            recognized.add(new RecognizedGlyph(glyph, best));
        }
        if (recognized.isEmpty()) return;

        // Register the full update before any circle is allowed to compile. Circles
        // are deliberately registered last, but compilation sees every rune and
        // nested circle produced by this same flood-fill batch.
        List<RecognizedGlyph> ordered = circlesLast(
                recognized, candidate -> candidate.match().role() == SymbolRole.OUTER_CIRCLE);
        List<PositionedGlyph> registered = new ArrayList<>();
        for (RecognizedGlyph candidate : ordered) {
            PositionedGlyph glyph = registerMatch(level, candidate);
            if (glyph != null) registered.add(glyph);
        }

        for (PositionedGlyph glyph : registered) {
            invalidateCompiledAncestors(level, glyph);
        }
        for (PositionedGlyph glyph : registered) {
            if (glyph.role() == SymbolRole.OUTER_CIRCLE) {
                ArrayEffectLifecycle.compileNew(level, glyph);
            }
        }
        syncGlyphs(level);
    }

    static <T> List<T> circlesLast(
            List<T> values, java.util.function.Predicate<T> isCircle) {
        List<T> ordered = new ArrayList<>(values.size());
        values.stream().filter(isCircle.negate()).forEach(ordered::add);
        values.stream().filter(isCircle).forEach(ordered::add);
        return List.copyOf(ordered);
    }

    private static PositionedGlyph registerMatch(
            ServerLevel level, RecognizedGlyph candidate) {
        ExtractedGlyph glyph = candidate.glyph();
        SymbolMatch best = candidate.match();
        int symbolLayerValue = ModSymbols.symbolLayerValueFor(best.symbolId());
        if (symbolLayerValue <= 0) {
            Gyromancy.LOGGER.debug(
                    "[MagicArrayDetector] Glyph {} REJECTED: missing symbol registry id",
                    best.symbolId());
            return null;
        }

        MagicArrayManager manager = level.getData(ModAttachments.ARRAY_MANAGER);
        int id = manager.nextGlyphId();
        GlyphMarker.markConsumed(glyph, id, symbolLayerValue, level);
        syncAffectedCanvases(glyph, level);

        PositionedGlyph positioned = new PositionedGlyph(
                UUID.randomUUID(), id, best.symbolId(), best.confidence(), best.role(),
                best.front(), best.length(), best.width(),
                glyph.pixels().iterator().next().pos(),
                glyph.minWorldX(), glyph.maxWorldX(),
                glyph.minWorldY(), glyph.maxWorldY(),
                Set.copyOf(glyph.pixels()));
        manager.registerGlyph(positioned);
        GlyphChunkStorage.store(level, positioned);
        Gyromancy.LOGGER.debug("[MagicArrayDetector] {} {} ACCEPTED - glyph #{} stored",
                best.role() == SymbolRole.OUTER_CIRCLE ? "Circle" : "Rune",
                best.symbolId(), id);
        return positioned;
    }

    private record RecognizedGlyph(ExtractedGlyph glyph, SymbolMatch match) {}

    private static void invalidateCompiledAncestors(
            ServerLevel level, PositionedGlyph glyph) {
        MagicArrayManager mgr = level.getData(ModAttachments.ARRAY_MANAGER);
        PositionedGlyph parent = mgr.parentCircle(glyph);
        while (parent != null) {
            ArrayEffectLifecycle.deactivateForRootGlyph(
                    level, parent.glyphUuid());
            parent = mgr.parentCircle(parent);
        }
    }

    private static void syncAffectedCanvases(ExtractedGlyph glyph, ServerLevel level) {
        Set<BlockPos> uniquePositions = glyph.pixels().stream()
                .map(PixelPos::pos)
                .collect(Collectors.toSet());

        for (BlockPos pos : uniquePositions) {
            syncCanvasAt(level, pos);
        }
    }

    private static void syncCanvasAt(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CanvasDataHolder holder)) return;

        CanvasData data = holder.painter$getCanvasData();
        BlockState state = level.getBlockState(pos);
        BlockState mimicked = be instanceof CanvasBlockEntity canvasBE
                ? canvasBE.getMimickedState()
                : null;

        PacketDistributor.sendToPlayersTrackingChunk(
                level, new ChunkPos(pos),
                new SyncCanvasPacket(pos, data, Optional.ofNullable(mimicked), false)
        );
        level.setBlocksDirty(pos, state, state);
    }

    private static void syncGlyphs(ServerLevel level) {
        PacketDistributor.sendToAllPlayers(buildGlyphPacket(level));
        PacketDistributor.sendToAllPlayers(buildArrayPacket(level));
    }

    /** Publishes glyph and array snapshots after an entity-backed canvas changes. */
    public static void syncWorldState(ServerLevel level) {
        syncGlyphs(level);
    }

    private static SyncArrayPacket buildArrayPacket(ServerLevel level) {
        MagicArrayManager mgr = level.getData(ModAttachments.ARRAY_MANAGER);
        List<SyncArrayPacket.ArrayData> arrays = new ArrayList<>();
        for (ArrayObject arr : mgr.getAllArrayObjs()) {
            SyncArrayPacket.ArrayData data = arrayData(level, arr);
            if (data != null) arrays.add(data);
        }
        return new SyncArrayPacket(arrays);
    }

    private static SyncArrayPacket.ArrayData arrayData(ServerLevel level, ArrayObject arr) {
        Map<BlockFace, List<PixelPos>> pixelsByFace = new HashMap<>();
        Map<UUID, List<PixelPos>> pixelsByCanvas = new HashMap<>();
        for (PositionedGlyph glyph : arr.allBoundGlyphs()) {
            for (PixelPos pixel : glyph.pixels()) {
                if (glyph.sourceCanvasId().isPresent()) {
                    pixelsByCanvas.computeIfAbsent(glyph.sourceCanvasId().get(), ignored -> new ArrayList<>())
                            .add(pixel);
                } else {
                    pixelsByFace.computeIfAbsent(
                                    new BlockFace(pixel.pos(), pixel.face()), ignored -> new ArrayList<>())
                            .add(pixel);
                }
            }
        }

        List<SyncArrayPacket.BlockData> parts =
                new ArrayList<>(pixelsByFace.size() + pixelsByCanvas.size());
        for (Map.Entry<BlockFace, List<PixelPos>> entry : pixelsByFace.entrySet()) {
            SyncArrayPacket.BlockData part = blockData(level, entry.getKey(), entry.getValue());
            if (part != null) parts.add(part);
        }
        for (Map.Entry<UUID, List<PixelPos>> entry : pixelsByCanvas.entrySet()) {
            if (level.getEntity(entry.getKey()) instanceof CanvasEntity canvas) {
                SyncArrayPacket.BlockData part = canvasBlockData(canvas, entry.getValue());
                if (part != null) parts.add(part);
            }
        }
        if (parts.isEmpty()) return null;
        int color = arr.scratchData().get("__array_color") instanceof Integer c ? c : 0xFFFFFFFF;
        return new SyncArrayPacket.ArrayData(
                arr.arrayId(),
                color,
                arr.remainingCompilationEffectTicks(level.getGameTime()),
                parts);
    }

    private static SyncArrayPacket.BlockData blockData(ServerLevel level, BlockFace key, List<PixelPos> pixels) {
        CanvasFace face = FloodFillExtractor.getFaceAt(level, key.pos, key.face);
        if (face == null) return null;

        int width = face.pixels().getWidth();
        int height = face.pixels().getHeight();
        byte[] mask = new byte[width * height];
        for (PixelPos pixel : pixels) {
            if (pixel.x() >= 0 && pixel.x() < width && pixel.y() >= 0 && pixel.y() < height) {
                mask[pixel.y() * width + pixel.x()] = 1;
            }
        }

        Vec3[] corners = face.cornerWithOffset();
        Vec3 sourceU = corners[1].subtract(corners[0]);
        Vec3 sourceV = corners[0].subtract(corners[3]);
        Vec3 center = Vec3.atCenterOf(key.pos)
                .add(corners[0].add(corners[1]).add(corners[2]).add(corners[3]).scale(0.25));
        return new SyncArrayPacket.BlockData(center, key.face, sourceU, sourceV, width, height, mask);
    }

    private static SyncArrayPacket.BlockData canvasBlockData(
            CanvasEntity canvas, List<PixelPos> pixels) {
        int width = canvas.document().resolutionWidth();
        int height = canvas.document().resolutionHeight();
        byte[] mask = new byte[width * height];
        for (PixelPos pixel : pixels) {
            if (pixel.x() >= 0 && pixel.x() < width
                    && pixel.y() >= 0 && pixel.y() < height) {
                mask[pixel.y() * width + pixel.x()] = 1;
            }
        }
        Vec3 center = canvas.localToWorld(0.5, 0.5);
        Vec3 sourceU = canvas.localToWorld(1.0, 0.5)
                .subtract(canvas.localToWorld(0.0, 0.5));
        Vec3 sourceV = canvas.localToWorld(0.5, 0.0)
                .subtract(canvas.localToWorld(0.5, 1.0));
        return new SyncArrayPacket.BlockData(
                center, canvas.getDirection(), sourceU, sourceV, width, height, mask);
    }

    private record BlockFace(BlockPos pos, Direction face) {}

    private static SyncGlyphPacket buildGlyphPacket(ServerLevel level) {
        MagicArrayManager mgr = level.getData(ModAttachments.ARRAY_MANAGER);
        List<SyncGlyphPacket.GlyphData> glyphs = new ArrayList<>();
        for (PositionedGlyph glyph : mgr.getAllGlyphs()) {
            PixelPos sample = glyph.pixels().isEmpty() ? null : glyph.pixels().iterator().next();
            if (sample == null) continue;

            Vec3 center = glyph.sourceCanvasId()
                    .flatMap(id -> level.getEntity(id) instanceof CanvasEntity canvas
                            ? canvas.localGlyph(glyph.glyphUuid()).map(local ->
                                    canvas.localToWorld(
                                            (local.minX() + local.maxX()) * 0.5,
                                            (local.minY() + local.maxY()) * 0.5))
                            : Optional.empty())
                    .orElseGet(() -> FloodFillExtractor.worldCenter(level, glyph.pixels(),
                                    glyph.minWorldX(), glyph.maxWorldX(),
                                    glyph.minWorldY(), glyph.maxWorldY())
                            .orElseGet(() -> Vec3.atCenterOf(glyph.worldPos())));

            glyphs.add(new SyncGlyphPacket.GlyphData(
                    glyph.glyphId(),
                    glyph.symbolId(),
                    glyph.confidence(),
                    sample.pos(),
                    sample.face(),
                    center,
                    glyph.front(),
                    glyph.length(),
                    glyph.width(),
                    glyph.minWorldX(),
                    glyph.maxWorldX(),
                    glyph.minWorldY(),
                    glyph.maxWorldY()
            ));
        }
        return new SyncGlyphPacket(glyphs);
    }
}
