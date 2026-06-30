# Symbol Pipeline

This document covers the symbol flow from canvas pixels to server storage and client sync/rendering. It does not describe the internals of `SkeletonMatcher`.

## 1. Canvas Update Scan

The entry point is `MagicArrayDetector.onCanvasUpdate`.

1. The server receives `ServerCanvasUpdateEvent`.
2. It reads `CanvasData` from the event.
3. It calls `ManaPixelDetector.scanForMana(level, pos, data)`.
4. A pixel becomes a seed only when:
   - it has the `gyromancy:mana` effect layer;
   - it does not already have `gyromancy:symbol_id`.
5. If any seeds are found, `FloodFillScheduler.submitBatch(serverLevel, seeds)` is called.

Normal Painter brushes do not enter the symbol system. `ManaPixelDetector.isManaPixel` only checks `gyromancy:mana`; opaque pixels are not used as a fallback.

## 2. Flood Fill Extraction

`FloodFillScheduler` calls `FloodFillExtractor.continueExtract` during server ticks.

A batch can contain many seeds, but the extractor emits one 8-connected component at a time. Each component becomes an `ExtractedGlyph`:

- `pixels`: original `PixelPos` entries with block, face, x, and y.
- `worldX/worldY`: world-space coordinates flattened onto the canvas plane.
- `minWorldX/maxWorldX/minWorldY/maxWorldY`: the glyph bounding box.
- `blockCount`: number of blocks touched by the glyph.

When one component is complete, `FloodFillScheduler` fires the `onGlyphExtracted` callback into `MagicArrayDetector`. If disconnected seeds remain, the scheduler continues with the next component.

## 3. Match Invocation

`MagicArrayDetector.onGlyphExtracted` calls:

```java
List<SymbolMatch> matches = SymbolRecognizer.recognize(glyph);
```

`SymbolRecognizer` only prepares and delegates the match:

1. `FloodFillExtractor.rawGlyphMatrix(glyph)` converts the `ExtractedGlyph` into a binary matrix.
2. `SkeletonMatcher.getInstance().recognize(rawMatrix)` returns match results.

If there is no match, the flow stops. Pixels are not marked and no glyph object is created.

If matches exist, `matches.getFirst()` is used as the best match. The result is routed by `SymbolRole`:

- `CENTER_SYMBOL` / `PARAMETER_RUNE`: handled by `handleRuneMatch`.
- `OUTER_CIRCLE`: handled by `handleCircleMatch`; currently it only searches for inner glyphs and does not create a glyph object for the circle itself.

## 4. Glyph Id And Canvas Marks

`handleRuneMatch` first checks for raw mana inside the glyph. If valid:

1. `SymbolRegistry.symbolLayerValueFor(best.symbolId())` returns the symbol layer value.
2. `MagicArrayManager.nextGlyphId()` allocates a runtime glyph id.
3. `GlyphMarker.markConsumed(glyph, id, symbolLayerValue, level)` writes canvas effect layers.

`GlyphMarker` writes two effect layers for every `PixelPos` in the glyph:

- `gyromancy:glyph_id`: the runtime glyph id, stored in a byte-backed canvas effect layer.
- `gyromancy:symbol_id`: symbol registry id + 1.

`symbol_id == 0` means no glyph, so stored symbol values use `registryId + 1`.

`glyph_id` is only a runtime canvas mark. The stable persisted identity is `PositionedGlyph.glyphUuid`.

## 5. PositionedGlyph Storage

After marking pixels, the server creates a `PositionedGlyph`:

```java
new PositionedGlyph(
    UUID.randomUUID(),
    id,
    best.symbolId(),
    best.confidence(),
    best.role(),
    representativeBlockPos,
    glyph.minWorldX(),
    glyph.maxWorldX(),
    glyph.minWorldY(),
    glyph.maxWorldY(),
    Set.copyOf(glyph.pixels())
)
```

Then it stores the object in two places:

```java
mgr.registerGlyph(pg);
GlyphChunkStorage.store(level, pg);
```

`MagicArrayManager` is runtime-only. It keeps:

- `UUID -> PositionedGlyph`
- `glyphId -> UUID`

`GlyphChunkStorage` persists glyphs in `ModAttachments.CHUNK_GLYPHS` on every chunk touched by the glyph. Cross-chunk glyphs are duplicated by `glyphUuid`; chunk load deduplicates them into the runtime manager. Chunk load does not rewrite canvas data.

## 6. Block Replacement And Piston Moves

`LevelChunkMixin` injects into `LevelChunk.setBlockState`.

When a block is replaced:

1. `MagicArrayDetector.onBlockReplaced` invalidates any persisted glyph touching that block.
2. If the new block state has a block entity, the position is added to a short retry queue.
3. On server ticks, `MagicArrayDetector.onServerTick` checks queued positions.
4. Once the block entity is a `CanvasDataHolder` and `CanvasData.faces()` is non-empty:
   - `gyromancy:glyph_id` and `gyromancy:symbol_id` are cleared from the placed canvas data;
   - `gyromancy:mana` is left intact;
   - the canvas is scanned through the normal `scanCanvasAt` path.

This handles piston moves where Painter restores the canvas block entity before its faces are available. The retry is intentionally short-lived and only applies to block positions seen through `setBlockState`.

## 7. Canvas Render Sync

`GlyphMarker` modifies server-side `CanvasFace` effect layers. To make clients see the glyph overlay, `MagicArrayDetector.syncAffectedCanvases` calls `syncCanvasAt` for every block touched by the glyph.

`syncCanvasAt` sends Painter's `SyncCanvasPacket` to players tracking the chunk:

```java
PacketDistributor.sendToPlayersTrackingChunk(
    level,
    new ChunkPos(pos),
    new SyncCanvasPacket(pos, data, Optional.ofNullable(mimicked), false)
);
```

On the client, `GlyphImageProvider` reads the `gyromancy:symbol_id` effect layer and creates the glyph overlay texture:

1. Read each pixel's `symbolValue`.
2. Skip `symbolValue == 0`.
3. Compute `registryId = symbolValue - 1`.
4. Resolve `GyromancyRegistries.SYMBOL.byId(registryId)`.
5. Write `template.glyphColor()` into the `NativeImage`.

`GlyphRenderer` renders that provider texture as a full-bright translucent quad on the canvas face.

The official glyph overlay comes from canvas effect layers. It does not depend on the glyph debug packet.

## 8. Glyph List Sync Packet

Besides Painter's `SyncCanvasPacket`, the server also syncs a recognized glyph list for client debug labels.

After glyph registration or invalidation, `MagicArrayDetector.syncGlyphs` sends:

```java
PacketDistributor.sendToAllPlayers(buildGlyphPacket(level));
```

`buildGlyphPacket` reads `MagicArrayManager.getAllGlyphs()` and creates a `SyncGlyphPacket`. Each `GlyphData` contains:

- `glyphId`
- `symbolId`
- `confidence`
- sample `BlockPos`
- sample `Direction`
- bounding box: `minWorldX/maxWorldX/minWorldY/maxWorldY`

`ModNetwork` registers `SyncGlyphPacket` as a play-to-client payload:

```java
registrar.playToClient(
    SyncGlyphPacket.TYPE,
    SyncGlyphPacket.STREAM_CODEC,
    SyncGlyphPacket::handleClient
);
```

On the client, `SyncGlyphPacket.handleClient` calls:

```java
ElementDebugRenderer.replaceGlyphData(packet.glyphs());
```

`ElementDebugRenderer` stores the latest glyph list as a client-side `glyphId -> GlyphData` mirror.

## 9. Debug Label Rendering

When `/gyromancy debug true` is enabled, `ElementDebugRenderer.onRenderLevelStage` renders both:

- element concentration debug quads;
- glyph debug labels.

Glyph labels use `SyncGlyphPacket.GlyphData`. They are debug-only and do not affect the official glyph overlay.

`renderGlyphLabels`:

1. Computes the label position from the glyph bounding box center.
2. Offsets the label outward from the sample canvas face using the face normal and `LABEL_FACE_OFFSET`.
3. Skips labels farther than `RADIUS + 4` from the player.
4. Renders billboard text:

```text
<symbol path> <confidence>
```

Example:

```text
fire 0.842
```

Element debug data is stored as an immutable snapshot through `replaceDebugData(Map.copyOf(...))`. Glyph debug data is refreshed through `replaceGlyphData`.
