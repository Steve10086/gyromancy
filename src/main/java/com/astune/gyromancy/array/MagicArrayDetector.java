package com.astune.gyromancy.array;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.array.MagicArrayManager;
import com.astune.gyromancy.api.symbol.ParameterRune;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolMatch;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.entity.ball.MagicBallEntity;
import com.astune.gyromancy.registry.ModAttachments;
import com.astune.gyromancy.registry.ModSymbols;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractedGlyph;
import com.astune.gyromancy.symbol.FloodFillExtractor;
import com.astune.gyromancy.symbol.FloodFillScheduler;
import com.astune.gyromancy.symbol.GlyphChunkStorage;
import com.astune.gyromancy.symbol.GlyphMarker;
import com.astune.gyromancy.symbol.InteriorValidator;
import com.astune.gyromancy.symbol.ManaPixelDetector;
import com.astune.gyromancy.symbol.SymbolCatalog;
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
        FloodFillScheduler.onGlyphExtracted(MagicArrayDetector::onGlyphExtracted);
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
        if (invalidGlyphIds.isEmpty()) return;

        MagicArrayManager mgr = level.getData(ModAttachments.ARRAY_MANAGER);
        Set<PositionedGlyph> glyphs = new HashSet<>();
        for (int glyphId : invalidGlyphIds) {
            PositionedGlyph glyph = mgr.getGlyph(glyphId);
            if (glyph != null) glyphs.add(glyph);
        }
        glyphs.addAll(GlyphChunkStorage.touching(level.getChunkAt(pos), pos));
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

            // Array teardown: if this glyph is bound to an active array, destroy it
            ArrayObject arr = mgr.getArrayForGlyph(glyph.glyphUuid());
            if (arr != null && tornDownArrays.add(arr.arrayId())) {
                List<ParameterRune> runeParams = toRuneParams(arr.runeGlyphs());
                SymbolCatalog.EndEffect end = SymbolCatalog.getEndEffect(arr.centerGlyph().symbolId());
                end.execute(level, arr.circleGlyph().worldPos(), runeParams, arr.scratchData());
                Gyromancy.LOGGER.info("[MagicArrayDetector] Array deactivated: center={}",
                        arr.centerGlyph().symbolId());
                mgr.unregisterArrayObj(arr.arrayId());
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
            byte[] oldSymbols = oldFace.getEffectLayer(ManaPixelDetector.SYMBOL_ID_KEY);
            byte[] oldGlyphs = oldFace.getEffectLayer(ManaPixelDetector.GLYPH_ID_KEY);
            if (oldSymbols == null || oldGlyphs == null) continue;

            byte[] newSymbols = newFace != null
                    ? newFace.getEffectLayer(ManaPixelDetector.SYMBOL_ID_KEY)
                    : null;

            int w = oldFace.pixels().getWidth();
            int h = oldFace.pixels().getHeight();
            int count = Math.min(w * h, Math.min(oldSymbols.length, oldGlyphs.length));
            for (int i = 0; i < count; i++) {
                int oldSymbol = oldSymbols[i] & 0xFF;
                if (oldSymbol == 0) continue;

                int newSymbol = newSymbols != null && i < newSymbols.length ? newSymbols[i] & 0xFF : 0;
                if (newSymbol == oldSymbol) continue;

                int glyphId = oldGlyphs[i] & 0xFF;
                if (glyphId > 0) changed.add(glyphId);
            }
        }
        return changed;
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

    private static void onGlyphExtracted(ServerLevel level, ExtractedGlyph glyph) {
        Gyromancy.LOGGER.debug("[MagicArrayDetector] Glyph extracted: {} pixels across {} blocks",
                glyph.pixels().size(), glyph.blockCount());

        List<SymbolMatch> matches = SymbolRecognizer.recognize(glyph);
        if (matches.isEmpty()) {
            Gyromancy.LOGGER.debug("[MagicArrayDetector] No match - pixels NOT marked");
            return;
        }

        SymbolMatch best = matches.getFirst();
        SymbolRole role = best.role();
        Gyromancy.LOGGER.debug("[MagicArrayDetector] Best match: {} conf={} role={}",
                best.symbolId(), String.format("%.3f", best.confidence()), role);

        if (role == SymbolRole.CENTER_SYMBOL || role == SymbolRole.PARAMETER_RUNE) {
            handleRuneMatch(level, glyph, best);
        } else if (role == SymbolRole.OUTER_CIRCLE) {
            handleCircleMatch(level, glyph, best);
        }
    }

    private static void handleRuneMatch(ServerLevel level, ExtractedGlyph glyph, SymbolMatch best) {
        //if (InteriorValidator.hasRawManaInside(glyph, level)) {
        //    Gyromancy.LOGGER.debug("[MagicArrayDetector] Rune {} REJECTED: raw mana inside",
        //            best.symbolId());
        //    return;
        //}

        int symbolLayerValue = ModSymbols.symbolLayerValueFor(best.symbolId());
        if (symbolLayerValue <= 0) {
            Gyromancy.LOGGER.debug("[MagicArrayDetector] Rune {} REJECTED: missing symbol registry id",
                    best.symbolId());
            return;
        }

        MagicArrayManager mgr = level.getData(ModAttachments.ARRAY_MANAGER);
        int id = mgr.nextGlyphId();

        GlyphMarker.markConsumed(glyph, id, symbolLayerValue, level);
        syncAffectedCanvases(glyph, level);

        PositionedGlyph pg = new PositionedGlyph(
                UUID.randomUUID(), id, best.symbolId(), best.confidence(), best.role(),
                best.front(), best.length(), best.width(),
                glyph.pixels().iterator().next().pos(),
                glyph.minWorldX(), glyph.maxWorldX(),
                glyph.minWorldY(), glyph.maxWorldY(),
                Set.copyOf(glyph.pixels())
        );

        mgr.registerGlyph(pg);
        GlyphChunkStorage.store(level, pg);
        syncGlyphs(level);

        Gyromancy.LOGGER.debug("[MagicArrayDetector] Rune {} ACCEPTED - glyph #{} stored",
                best.symbolId(), id);
    }

    private static void handleCircleMatch(ServerLevel level, ExtractedGlyph glyph, SymbolMatch best) {
        //if (InteriorValidator.hasRawManaInside(glyph, level)) {
        //    Gyromancy.LOGGER.debug("[MagicArrayDetector] Circle REJECTED: raw mana inside");
        //    return;
        //}

        MagicArrayManager mgr = level.getData(ModAttachments.ARRAY_MANAGER);
        List<PositionedGlyph> innerGlyphs = InteriorValidator.findGlyphsInside(
                glyph, level, mgr.getGlyphIndex());

        if (innerGlyphs.isEmpty()) {
            Gyromancy.LOGGER.debug("[MagicArrayDetector] Circle found, no inner glyphs yet - waiting");
            return;
        }

        // Stage 1: structural validation — exactly 1 center symbol, 0+ runes
        List<PositionedGlyph> centers = new ArrayList<>();
        List<PositionedGlyph> runes = new ArrayList<>();
        for (PositionedGlyph pg : innerGlyphs) {
            if (pg.role() == SymbolRole.CENTER_SYMBOL) centers.add(pg);
            else if (pg.role() == SymbolRole.PARAMETER_RUNE) runes.add(pg);
        }

        if (centers.size() != 1) {
            Gyromancy.LOGGER.debug("[MagicArrayDetector] Array REJECTED: {} center symbol(s)",
                    centers.size());
            return;
        }

        // Store circle as a PositionedGlyph for invalidation binding
        int circleSymbolLayer = ModSymbols.symbolLayerValueFor(best.symbolId());
        int circleId = mgr.nextGlyphId();
        GlyphMarker.markConsumed(glyph, circleId, circleSymbolLayer, level);
        syncAffectedCanvases(glyph, level);

        PositionedGlyph circleGlyph = new PositionedGlyph(
                UUID.randomUUID(), circleId, best.symbolId(), best.confidence(), best.role(),
                best.front(), best.length(), best.width(),
                glyph.pixels().iterator().next().pos(),
                glyph.minWorldX(), glyph.maxWorldX(),
                glyph.minWorldY(), glyph.maxWorldY(),
                Set.copyOf(glyph.pixels())
        );
        mgr.registerGlyph(circleGlyph);
        GlyphChunkStorage.store(level, circleGlyph);

        PositionedGlyph centerGlyph = centers.getFirst();
        List<ParameterRune> runeParams = toRuneParams(runes);

        // Stage 2: create array object, dispatch centerEffect
        Map<String, Object> scratchData = Map.of();
        ArrayObject arr = new ArrayObject(
                UUID.randomUUID(), circleGlyph, centerGlyph, runes, scratchData);
        mgr.registerArrayObj(arr);

        SymbolCatalog.CenterEffect effect = SymbolCatalog.getCenterEffect(centerGlyph.symbolId());
        scratchData = effect.execute(level, glyph.pixels().iterator().next().pos(), circleGlyph, centerGlyph, runes);
        if (scratchData == null) scratchData = Map.of();
        mgr.setArrayScratchData(arr.arrayId(), scratchData);
        bindPersistentEntities(level, arr.arrayId(), scratchData);

        syncGlyphs(level);
        Gyromancy.LOGGER.info("[MagicArrayDetector] Array activated: center={}, runes={}",
                centerGlyph.symbolId(), runeParams.size());
    }

    private static List<ParameterRune> toRuneParams(List<PositionedGlyph> runes) {
        List<ParameterRune> params = new ArrayList<>(runes.size());
        for (PositionedGlyph pg : runes) {
            params.add(new ParameterRune(pg.symbolId(), pg.confidence(), ""));
        }
        return params;
    }

    private static void bindPersistentEntities(ServerLevel level, UUID arrayId, Map<String, Object> scratchData) {
        for (Map.Entry<String, Object> entry : scratchData.entrySet()) {
            if (!(entry.getValue() instanceof ArrayObject.EntityRef ref)) continue;
            if (ref.resolve(level) instanceof MagicBallEntity ball) {
                ball.bindToArray(arrayId);
            }
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
        for (PositionedGlyph glyph : arr.allBoundGlyphs()) {
            for (PixelPos pixel : glyph.pixels()) {
                pixelsByFace.computeIfAbsent(new BlockFace(pixel.pos(), pixel.face()), ignored -> new ArrayList<>())
                        .add(pixel);
            }
        }

        List<SyncArrayPacket.BlockData> parts = new ArrayList<>(pixelsByFace.size());
        for (Map.Entry<BlockFace, List<PixelPos>> entry : pixelsByFace.entrySet()) {
            SyncArrayPacket.BlockData part = blockData(level, entry.getKey(), entry.getValue());
            if (part != null) parts.add(part);
        }
        if (parts.isEmpty()) return null;
        return new SyncArrayPacket.ArrayData(arr.arrayId(),
                SymbolCatalog.glyphColorFor(arr.centerGlyph().symbolId()), parts);
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

    private record BlockFace(BlockPos pos, Direction face) {}

    private static SyncGlyphPacket buildGlyphPacket(ServerLevel level) {
        MagicArrayManager mgr = level.getData(ModAttachments.ARRAY_MANAGER);
        List<SyncGlyphPacket.GlyphData> glyphs = new ArrayList<>();
        for (PositionedGlyph glyph : mgr.getAllGlyphs()) {
            PixelPos sample = glyph.pixels().isEmpty() ? null : glyph.pixels().iterator().next();
            if (sample == null) continue;

            glyphs.add(new SyncGlyphPacket.GlyphData(
                    glyph.glyphId(),
                    glyph.symbolId(),
                    glyph.confidence(),
                    sample.pos(),
                    sample.face(),
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
