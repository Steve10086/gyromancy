# Gyromancy Mod Architecture

> Minecraft NeoForge 1.21.1 mod — magical circles, glyph symbols, and elemental concentrations on a multi-block canvas system.

---

## 1. Project Overview

**Mod ID:** `gyromancy`  
**Group:** `com.astune.gyromancy`  
**Main Class:** `Gyromancy.java`  
**Client Class:** `GyromancyClient.java`  
**External Canvas Library:** Pigmentum (`com.astune.painter`) — provides the canvas/drawing infrastructure  
**External Rendering:** Veil — provides the post-processing bloom pipeline  
**ML Inference:** ONNX Runtime — optional siamese-network symbol matcher  

### Core Concepts

| Concept | Description |
|---------|-------------|
| **Magic Array** | A drawn magical circle on a multi-block canvas face, consisting of an outer circle + center symbol + parameter runes |
| **Glyph / Symbol** | A recognized drawn pattern on canvas (arrow, circle, figure-8, star, fire, water, earth, wind, revert) |
| **Element Concentrations** | 9-element energy values (WIND, FIRE, WATER, EARTH, LIGHT, DARK, SPACE, TIME, MANA) per block position, with diffusion & decay |
| **Mana Pixels** | Individual colored pixels drawn on canvas faces that form symbols — detected via the `gyromancy:mana` effect layer |

---

## 2. Full Package Tree

```
com.astune.gyromancy/
├── Gyromancy.java                    ← main mod entry point (@Mod)
├── GyromancyClient.java              ← client-side entry point
├── Config.java                       ← NeoForge config spec
│
├── api/                              ← shared API (no Minecraft-world coupling)
│   ├── array/
│   │   ├── ArrayActivationResult.java
│   │   ├── ArrayObject.java
│   │   ├── IArrayEffect.java
│   │   ├── MagicArrayComponents.java
│   │   ├── MagicArrayManager.java
│   │   └── MagicArrayState.java
│   ├── element/
│   │   ├── ElementConcentrations.java
│   │   ├── ElementType.java
│   │   └── IElementStorage.java
│   ├── entity/
│   │   ├── PseudoEntity.java
│   │   └── PseudoEntityType.java
│   ├── ink/
│   │   ├── InkType.java
│   │   └── PenProperties.java
│   └── symbol/
│       ├── ParameterRune.java
│       ├── PixelPos.java
│       ├── PositionedGlyph.java
│       ├── SymbolMatch.java
│       ├── SymbolRole.java
│       └── SymbolTemplate.java
│
├── array/                            ← magic array detection & lifecycle
│   ├── MagicArrayDetector.java
│   └── effect/                       (empty — future effect implementations)
│
├── client/                           ← client-side rendering & setup
│   ├── ClientSetup.java
│   ├── ElementDebugRenderer.java
│   ├── glyph/
│   │   ├── GlyphImageProvider.java
│   │   └── GlyphRenderer.java
│   └── screen/                       (empty)
│
├── command/
│   └── DebugCommands.java
│
├── element/                          ← element concentration engine
│   ├── ElementBiomeProvider.java
│   ├── ElementChunkEventHandler.java
│   ├── ElementChunkProcessor.java
│   ├── ElementRegressionLogic.java   (deprecated placeholder)
│   ├── ElementStorageManager.java
│   ├── ElementTickProcessor.java
│   ├── IElementChunkAccessor.java
│   ├── IElementTickProcessor.java
│   └── event/
│       ├── ElementActivatedEvent.java
│       ├── ElementChangeEvent.java
│       ├── ElementCleanedUpEvent.java
│       ├── ElementEventBus.java
│       ├── ElementEventSubscription.java
│       ├── ElementThresholdEvent.java
│       └── ThresholdDirection.java
│
├── ink/
│   └── InkRegistry.java
│
├── item/
│   ├── DebugBrushItem.java
│   ├── InkBottleItem.java
│   └── PenItem.java
│
├── mixin/
│   └── LevelChunkMixin.java
│
├── network/
│   ├── ModNetwork.java
│   ├── SyncDebugElementPacket.java
│   └── SyncGlyphPacket.java
│
├── registry/
│   ├── GyromancyRegistries.java
│   ├── ModAttachments.java
│   ├── ModBlockEntities.java
│   ├── ModBlocks.java
│   ├── ModCreativeTabs.java
│   ├── ModDataComponents.java
│   ├── ModEntities.java
│   └── ModItems.java
│
├── symbol/                           ← symbol recognition pipeline
│   ├── FloodFillExtractor.java
│   ├── FloodFillScheduler.java
│   ├── GeometricMatcher.java
│   ├── GlyphChunkStorage.java
│   ├── GlyphMarker.java
│   ├── InteriorValidator.java
│   ├── ManaPixelDetector.java
│   ├── MLSymbolMatcher.java
│   ├── SkeletonMatcher.java
│   ├── SymbolRecognizer.java
│   └── SymbolRegistry.java
│
└── util/                             ← geometry & image utilities
    ├── CanvasScanUtils.java
    ├── GeometryPreprocessUtils.java
    ├── GeometryUtils.java
    └── TemplateLoader.java
```

---

## 3. Module & Class Documentation

### 3.1 Root Package

#### `Gyromancy.java`
**Role:** Main mod entry point — annotated `@Mod(Gyromancy.MODID)`.  
**Function:**
- Registers all `DeferredRegister` instances (blocks, items, creative tabs, entities, block entities, attachments, data components) on the mod event bus
- Subscribes to `ServerTickEvent.Post` → delegates to `ElementTickProcessor.onServerTick()`
- Subscribes to `ServerTickEvent.Post` → delegates to `MagicArrayDetector.onServerTick()` for delayed canvas placement checks
- Subscribes to chunk load/unload events → delegates to `ElementChunkEventHandler`
- Subscribes to `RegisterCommandsEvent` → registers `DebugCommands`
- Registers the common config

**Calls/Depends on:**
```
Config
registry.*                   (ModBlocks, ModItems, ModCreativeTabs, ModEntities,
                              ModBlockEntities, ModAttachments, ModDataComponents)
command.DebugCommands
element.ElementChunkEventHandler
element.ElementTickProcessor
array.MagicArrayDetector
```

---

#### `GyromancyClient.java`
**Role:** Client-only mod entry point — annotated `@Mod(..., dist = Dist.CLIENT)`.  
**Function:**
- Registers NeoForge config screen
- Registers `/gyromancy debug` client command → toggles `ElementDebugRenderer.setEnabled()`
- Hooks `ElementDebugRenderer.onRenderLevelStage()` on `RenderLevelStageEvent` (AFTER_PARTICLES)

**Calls/Depends on:**
```
client.ElementDebugRenderer
```

---

#### `Config.java`
**Role:** NeoForge configuration spec (COMMON type).  
**Fields:** `LOG_DIRT_BLOCK`, `MAGIC_NUMBER`, `MAGIC_NUMBER_INTRODUCTION`, `ITEM_STRINGS` — example/template values.

**Calls/Depends on:** *(none internal)*

---

### 3.2 API Subpackages

#### 3.2.1 `api.element`

##### `ElementType.java` (enum)
**Role:** The 9 elemental types.  
**Values:** `WIND`, `FIRE`, `WATER`, `EARTH`, `LIGHT`, `DARK`, `SPACE`, `TIME`, `MANA`  
**Methods:** `byIndex(int)` → enum constant; `COUNT = 9`

##### `IElementStorage.java` (interface)
**Role:** Abstraction over element data persistence.  
**Methods:** `get()`, `set()`, `remove()`, `isOverridden()` — all take `Level` + `BlockPos`

##### `ElementConcentrations.java` (record)
**Role:** Stores `long[] values` and `long[] derivatives` (size=9, clamped to ±Int.MAX_VALUE).  
**Key methods:**
- `diffuse()` — 3×3×3 neighbor average + derivative
- `decayAndRecover()` — bell-shaped decay above biome default, 25% recovery below
- `excess()` / `sharesFromExcess()` — excess-redistribution helpers
- Codec serialization for `DataResult<ElementConcentrations>`
- Nested `PosEntry` record with `mapCodec()` for `HashMap<BlockPos, ElementConcentrations>` serialization

**Calls/Depends on:** `ElementType`

---

#### 3.2.2 `api.array`

##### `MagicArrayComponents.java` (record)
**Role:** Recognized components of a magic array on a canvas.  
**Fields:** `outerCircle` (SymbolMatch), `centerSymbol` (SymbolMatch), `parameterRunes` (List<SymbolMatch>), `canvasPos` (BlockPos), `level` (Level)  
**Methods:** `isValid(float minConfidence)` — checks all confidence thresholds

##### `IArrayEffect.java` (interface)
**Role:** Lifecycle contract for magic array effects.  
**Methods:** `onActivate(MagicArrayState, ServerLevel, Map params)`, `onTick(...)`, `onDeactivate(...)`

##### `ArrayActivationResult.java` (record)
**Role:** Result of activation attempt.  
**Fields:** `success`, `centerSymbol`, `parameterRunes`, `errorMessage`  
**Static factories:** `success(...)`, `failure(...)`

##### `MagicArrayState.java`
**Role:** Runtime state for one activated array.  
**Tracks:** UUID, canvas position, effect, params, tick counter, active flag, runtime data map.  
**Methods:** `activate()`, `tick()`, `deactivate()` — fire hooks on `IArrayEffect`

##### `ArrayObject.java` (record)
**Role:** A validated magic array bound to its constituent glyphs.  
**Fields:** `arrayId` (UUID), `circleGlyph` (PositionedGlyph), `centerGlyph` (PositionedGlyph), `runeGlyphs` (List<PositionedGlyph>), `scratchData` (Map<String,Object>)  
**Methods:** `allBoundGlyphs()` — returns circle + center + runes for bulk invalidation lookup. When any bound glyph is invalidated, the array's `EndEffect` fires with scratchData and the array is destroyed.

##### `MagicArrayManager.java`
**Role:** Tracks all active arrays + runtime glyph indexes in one dimension (stored as a NeoForge `AttachmentType` on `Level`).  
**Data structures:**
- `Map<UUID, MagicArrayState>` — legacy active arrays (unused, pending cleanup)
- `Map<BlockPos, UUID>` — legacy position→array index
- `LinkedHashMap<UUID, PositionedGlyph>` — runtime recognized glyphs (includes circle glyphs)
- `Map<Integer, UUID>` — runtime canvas glyph id→persistent glyph UUID
- `Map<UUID, ArrayObject>` — phrase5 active array objects
- `Map<UUID, UUID>` — glyph UUID→array ID reverse index for teardown lookup
**Methods:** runtime glyph-id allocation, glyph registration/lookup, array-object registration (`registerArrayObj`/`unregisterArrayObj`), `getArrayForGlyph(UUID)` → reverse lookup, `setArrayScratchData()`

**Calls/Depends on:** `PositionedGlyph`, `ArrayObject`, `SymbolRole`, `FloodFillExtractor.ExtractedGlyph`

---

#### 3.2.3 `api.symbol`

##### `SymbolRole.java` (enum, implements StringRepresentable)
**Role:** Categorizes what a symbol IS in the context of a magic array.  
**Values:** `OUTER_CIRCLE`, `CENTER_SYMBOL`, `PARAMETER_RUNE`, `UNKNOWN`

##### `SymbolMatch.java` (record)
**Role:** Result of matching a drawn glyph against a `SymbolTemplate`.  
**Fields:** `symbolId`, `confidence`, `rotationDegrees`, `mirrored`, `scale`, `front`, `length`, `width`, `centerX`, `centerY`, `role`  
**Methods:** `isConfident(float threshold)`

##### `PixelPos.java` (record)
**Role:** A single mana pixel on a multi-block canvas face.  
**Fields:** `pos` (BlockPos), `face` (Direction), `x` (0–15), `y` (0–15), `color` (ARGB)

##### `PositionedGlyph.java` (record)
**Role:** A recognized glyph with world-space bounding info.  
**Fields:** `glyphUuid` (persistent identity), `glyphId` (runtime canvas mark), `symbolId`, `confidence`, `role`, `front`, `length`, `width`, `worldPos`, bounding box, `pixels` (Set<PixelPos>)  
**Serialization:** Codec support for chunk attachment storage, including `UUID -> PositionedGlyph` map entries. Pose fields default to zero for old saved glyphs.

##### `ParameterRune.java` (record)
**Role:** A parameter rune with value.  
**Fields:** `runeId`, `value`, `metadata`  
**Nested enum `RuneCategory`:** `MAGNITUDE`, `ELEMENT`, `DIRECTION`, `DURATION`, `AREA`, `MODIFIER`

##### `SymbolTemplate.java` (record)
**Role:** Canonical reference pattern for a symbol.  
**Fields:** `id`, `pattern` (int[][]), `featurePoints`, `allowRotation`, `allowMirror`, `defaultRole`, `glyphColor`  
**Cached computations (ConcurrentHashMap):** contour, turning function, centroid distance function, curvature, corners, skeleton graph, edge weights, PCA  
**Has codec** for registry serialization

**Calls/Depends on:** `GeometryUtils`, `GeometryPreprocessUtils`

---

#### 3.2.4 `api.entity`

##### `PseudoEntity.java` (abstract, extends Entity)
**Role:** Lightweight entity bound to a magic array (no physics, no collision, no AI).  
**Fields:** `boundArrayPos`, `boundArrayId`  
**Stub hooks:** `onElementThreshold()`, `onElementChange()`

##### `PseudoEntityType.java` (record)
**Role:** Lightweight type identifier: `ResourceLocation` id → factory `(Level, BlockPos, UUID, Map) → PseudoEntity`

---

#### 3.2.5 `api.ink`

##### `InkType.java`
**Role:** Ink type definition with builder — determines color, mana value, and effect layers written to canvas.  
**Fields:** `id`, `color` (ARGB), `magicalAffinity` (0–2), `primaryElement` (ElementType), `elementBoost` (0–2), `manaValue` (int, written to `gyromancy:mana`), `effectKeys` (Map<String,Integer>, additional effect layers)  
**Builder:** `color()`, `magicalAffinity()`, `primaryElement()`, `elementBoost()`, `manaValue()`, `effectKey(key, value)`

##### `PenProperties.java` (record)
**Role:** Pen/brush properties.  
**Fields:** `brushSize`, `inkCapacity`, `precision`, `drawingSpeed`; codec support.

---

### 3.3 Registry Package

| Class | Registers | Status |
|-------|-----------|--------|
| `ModBlocks.java` | `DeferredRegister.Blocks` | Empty (placeholder) |
| `ModItems.java` | `DeferredRegister.Items` — `DEBUG_BRUSH`, `PEN`, `INK_BOTTLE` | Active |
| `ModCreativeTabs.java` | Creative tab "gyromancy" (icon = Debug Brush) | Active |
| `ModEntities.java` | `DeferredRegister<EntityType<?>>` | Empty (placeholder) |
| `ModBlockEntities.java` | `DeferredRegister<BlockEntityType<?>>` | Empty (placeholder) |
| `ModAttachments.java` | `ELEMENT_OVERRIDES` (HashMap<BlockPos,ElementConcentrations> on LevelChunk), `CHUNK_GLYPHS` (HashMap<UUID,PositionedGlyph> on LevelChunk), `ARRAY_MANAGER` (MagicArrayManager on Level) | Active |
| `ModDataComponents.java` | `INK_TYPE`, `PEN_PROPERTIES`, `INK_REMAINING` data component types | Active |
| `GyromancyRegistries.java` | Custom NeoForge registries: `ARRAY_EFFECT`, `SYMBOL`, `INK` | Active |

---

### 3.4 Element Package

#### Core Processing Pipeline

```
ElementTickProcessor (every 10 ticks)
  │
  ├── ElementChunkProcessor.processChunk()
  │     ├── Step 1: decayAndRecover() all tracked positions
  │     ├── Step 2: classify sharers vs carriers
  │     ├── Step 3: distribute excess/27 to self + 26 neighbors
  │     ├── Step 4: reconstruct values
  │     └── ElementEventBus.fireThreshold/fireChange/fireActivation/fireCleanup
  │
  └── SyncDebugElementPacket → all players (debug snapshot)
```

#### Individual Classes

##### `ElementTickProcessor.java` (implements IElementTickProcessor)
**Role:** Server tick handler — delegates to `ElementChunkProcessor.processChunk()` every 10 ticks per active chunk; sends debug sync to clients.

**Calls/Depends on:** `ElementChunkProcessor`, `ElementChunkEventHandler`, `SyncDebugElementPacket`

---

##### `ElementChunkProcessor.java`
**Role:** Core per-chunk, per-tick element diffusion & decay.  
**Algorithm (4-step):**
1. **Decay:** `decayAndRecover()` toward biome baseline for all tracked positions
2. **Excess distribute:** classify positions, compute `sharesFromExcess()`, distribute to self + 26 neighbor positions (cross-chunk boundary handling via `persistByChunk()`)
3. **Reconstruct:** accumulate distributed shares into values
4. **Fire events:** threshold crossings, significant changes, first-write activation, cleanup

**Calls/Depends on:** `ElementConcentrations`, `ElementType`, `ElementEventBus`, `ElementChunkEventHandler`

---

##### `ElementChunkEventHandler.java`
**Role:** Tracks which chunks (per dimension) have active element overrides.  
**Data:** `ConcurrentHashMap<ResourceKey<Level>, Set<ChunkPos>>`  
**Methods:** `markActive()`, `markInactive()`, `getActiveChunkPositions()`

---

##### `ElementStorageManager.java` (implements IElementStorage)
**Role:** Singleton storage backend.  
- `get()` → checks overrides (via `IElementChunkAccessor`) or falls back to `ElementBiomeProvider`
- `set()` → writes override + fires `ElementChunkProcessor.onFirstWrite()`
- `remove()` → clears override if empty map

**Calls/Depends on:** `ElementConcentrations`, `IElementStorage`, `IElementChunkAccessor`, `ElementBiomeProvider`, `ElementChunkProcessor`, `ModAttachments`

---

##### `ElementBiomeProvider.java`
**Role:** Maps Minecraft `Biome` → default `ElementConcentrations`.  
**Computation:** Uses biome temperature, downfall, underground flag → 9 element values scaled to [0, 1000]. Cached in `ConcurrentHashMap`.

---

##### `IElementChunkAccessor.java` (interface)
**Role:** Mixin target interface — methods `gyromancy$getElementOverrides()`, `gyromancy$setElementOverrides()`, `gyromancy$hasElementOverrides()`

##### `IElementTickProcessor.java` (interface)
**Role:** Strategy interface — `onServerTick()`, `onServerStart()`, `onServerStop()`

##### `ElementRegressionLogic.java`
**Role:** Deprecated placeholder — logic now inlined in `ElementConcentrations` and `ElementChunkProcessor`.

---

#### Element Event Subsystem (`element.event`)

| Class | Role |
|-------|------|
| `ElementEventBus.java` | Internal lightweight event bus (independent of NeoForge). Uses `EnumMap<ElementType, List<...>>` with `CopyOnWriteArrayList`. Registration methods return `ElementEventSubscription` handles. |
| `ElementEventSubscription.java` | Handle for unsubscription — `unsubscribe()`, `isActive()` |
| `ElementActivatedEvent.java` | Fired on first element write: `level`, `pos`, `initialValues` |
| `ElementCleanedUpEvent.java` | Fired when values return to biome defaults |
| `ElementThresholdEvent.java` | Fired on threshold crossing: `element`, `oldValue`, `newValue`, `threshold`, `direction` |
| `ElementChangeEvent.java` | Fired on significant delta: `element`, `delta`, `rateOfChange`, `previousValue`, `currentValue` |
| `ThresholdDirection.java` | Enum: `RISING_ABOVE`, `FALLING_BELOW` |

---

### 3.5 Symbol Recognition Pipeline

#### Data Flow

```
ManaPixelDetector
  │  scans canvas faces for unmarked mana pixels
  ▼
FloodFillScheduler (tick-budgeted, max 20 blocks/tick)
  │  submits seed pixels
  ▼
FloodFillExtractor
  │  8-connected BFS within face, cross-block via corner adjacency
  │  produces ExtractedGlyph (pixels, bounding box, block count)
  ▼
SymbolRecognizer
  │  rasterizes glyph → int[][]
  │
  ├── SkeletonMatcher (primary matcher)
  │     ├── Stage 1: hard layer pass (open/closed line counts)
  │     ├── Stage 2: soft threshold pass (graph edit distance + Hungarian assignment)
  │     ├── Matched edge pairs produce rotationDegrees
  │     └── SymbolTemplate lookup from SymbolRegistry
  │
  └── MLSymbolMatcher (optional ML fallback)
        ├── ONNX Runtime siamese network
        └── L1 distance on 64-dim L2-normalized embeddings
```

#### Individual Classes

##### `ManaPixelDetector.java`
**Role:** Scans canvas faces for painted mana pixels.  
**Effect keys:** `gyromancy:mana` (value≥1 = mana pixel), `gyromancy:symbol_id` (marking), `gyromancy:glyph_id`  
**Methods:** `isManaPixel()`, `isMarked()`, `scanForMana()`, `scanFace()`

**Calls/Depends on:** `PixelPos`

---

##### `FloodFillExtractor.java`
**Role:** Cross-block flood-fill BFS for connected mana pixels.  
**Key types:**
- `ExtractedGlyph` — result: pixel set, world coords, bounding box, block count
- `FloodFillState` — BFS state: queue, visited, processed seeds, geometry tracking; `absorb()` for merging
- `ExtractionResult` — result wrapper

**Algorithm:** 8-connected BFS within a canvas face; cross-block via `findAdjacentByCorner()` using canvas face geometry + dot-product projection

**Calls/Depends on:** `PixelPos`

---

##### `FloodFillScheduler.java`
**Role:** Tick-budgeted scheduler for flood-fill extraction.  
**Budget:** Max 20 blocks processed per server tick.  
**State:** Global `allSeeds` map (seed→FloodFillState) for origin-based merging; pending task queue; completion callbacks.

**Calls/Depends on:** `PixelPos`, `FloodFillExtractor.*`

---

##### `GlyphMarker.java`
**Role:** Marks matched glyph pixels as consumed in canvas effect layers.  
**Methods:** `markConsumed()` (writes `glyph_id` + `symbol_id` effect values), `clearMarks()`

**Calls/Depends on:** `PixelPos`, `PositionedGlyph`, `ExtractedGlyph`

---

##### `GlyphChunkStorage.java`
**Role:** Persists recognized glyphs on the chunks they touch.  
**Storage:** `ModAttachments.CHUNK_GLYPHS` as `UUID -> PositionedGlyph`. Cross-chunk glyphs are duplicated in each touched chunk and deduplicated by UUID when chunks load.  
**Methods:** `store()`, `load()`, `remove()`, `touching()`. Uses `getChunkNow()` for cross-chunk writes/removals so storage operations do not force-load neighboring chunks.

**Calls/Depends on:** `PositionedGlyph`, `ModAttachments`, `MagicArrayManager`

---

##### `InteriorValidator.java`
**Role:** Validates glyph interiors.  
**Methods:**
- `hasRawManaInside()` — checks bounding box for un-marked mana pixels
- `findGlyphsInside()` — finds registered `PositionedGlyph`(s) inside another glyph's interior

**Calls/Depends on:** `PixelPos`, `PositionedGlyph`, `ExtractedGlyph`

---

##### `SymbolRecognizer.java`
**Role:** Main entry point for symbol recognition.  
**Config:** `confidenceThreshold` default 0.40f.  
**Flow:** Rasterize `ExtractedGlyph` → `SkeletonMatcher.recognize()` → add glyph pose (`front`, `length`, `width`) → `SymbolMatch` list. Supports debug PNG output.

**Calls/Depends on:** `SymbolMatch`, `SymbolTemplate`, `SkeletonMatcher`, `GyromancyRegistries`, `ExtractedGlyph`

---

##### `SkeletonMatcher.java`
**Role:** Core skeleton-graph-based symbol matcher (singleton).  

**Pipeline:**
1. `computeStats()` — fill holes → upscale to min 128px → Gaussian smooth → Guo-Hall skeletonize → extract nodes → trace edges → merge zero-edges → prune short branches → split at support points
2. **Hard layer pass:** compare open-line counts, closed-loop counts, inner/outer classification
3. **Soft threshold pass:** minimum graph edit matching via Hungarian assignment of surviving edges; sub-edge chain shape comparison (segment count, path length, turning angles, endpoint angles)
4. **Rotation output:** matched edge pairs produce `rotationDegrees`, reused by `SymbolRecognizer` for glyph pose

**Key types:** `SkeletonStats`, `TemplateEntry`, `MatchScore`, `EdgeScore`, `SoftThresholds`

**Calls/Depends on:** `GeometryUtils`, `GeometryPreprocessUtils`, `TemplateLoader`

---

##### `MLSymbolMatcher.java`
**Role:** Two-stage ML matcher via ONNX Runtime.  
- **Stage 1 (disabled):** Skeleton graph topology gating
- **Stage 2:** 64-dim L2-normalized CNN embedding → L1 distance to precomputed template embeddings  
**Models:** `symbol_siamese.onnx`, `template_embeddings.json`

**Calls/Depends on:** `GeometryUtils`, `GeometryPreprocessUtils`, `TemplateLoader`

---

##### `GeometricMatcher.java`
**Role:** Thin backward-compatibility wrapper around `SkeletonMatcher`.  
**Contains:** `MatchResult` record with `NONE` sentinel.

---

##### `SymbolRegistry.java`
**Role:** Unified symbol template registry.  
**Defines 9 symbols:** `arrow`, `circle_outer`, `earth` (green), `figure_8`, `fire` (red), `revert`, `star`, `water` (blue), `wind` (cyan).  
Each has custom matching thresholds (circle_outer is strictest: 0.95 all-around).  
Registers `SymbolTemplate` instances into `GyromancyRegistries.SYMBOL`.  

**Effect system:** Each `SymbolDef` carries two behavior functions:
- `CenterEffect`: called when a valid array forms around this center symbol — returns `Map<String,Object>` scratch data (default no-op, returns empty map)
- `EndEffect`: called when the array is destroyed — receives scratch data from `CenterEffect` (default no-op)
Both are fused into `SymbolDef` as fields; the `SYMBOLS` configuration array is the single extension point.

**Calls/Depends on:** `SymbolRole`, `SymbolTemplate`, `SkeletonMatcher`, `TemplateLoader`, `GyromancyRegistries`

---

### 3.6 Array Detection

##### `MagicArrayDetector.java` (`@EventBusSubscriber`)
**Role:** Bridge between canvas update events and symbol recognition.  

**Event handlers:**
| Event | Action |
|-------|--------|
| `ServerCanvasUpdateEvent.Pre` | Diffs old/new `symbol_id` layers → invalidates changed glyphs |
| `ServerCanvasUpdateEvent` | Scans for new mana seeds → submits to `FloodFillScheduler` |
| `PlayerEvent.PlayerLoggedInEvent` | Sends `SyncGlyphPacket` |
| `ChunkEvent.Load` (via `Gyromancy`) | Restores persisted chunk glyphs into `MagicArrayManager` |
| `ServerTickEvent.Post` (via `Gyromancy`) | Retries recently placed canvas block entities until `CanvasData.faces()` is available |

**Extraction callback (from FloodFillScheduler):**
1. `SymbolRecognizer.recognize()` the extracted glyph
2. `InteriorValidator.hasRawManaInside()` — reject if not clean
3. `GlyphMarker.markConsumed()` — consume pixels
4. Store as `PositionedGlyph` in `MagicArrayManager` and `GlyphChunkStorage`
5. Sync via `SyncCanvasPacket` + `SyncGlyphPacket`

**OUTER_CIRCLE handler (`handleCircleMatch`) — array lifecycle:**
- Circle is stored as a `PositionedGlyph` (role=`OUTER_CIRCLE`) for invalidation binding
- Searches for inner glyphs via `InteriorValidator.findGlyphsInside()`
- **Stage 1 (structural validation):** exactly 1 `CENTER_SYMBOL`, 0+ `PARAMETER_RUNE` → reject otherwise
- **Stage 2 (dispatch):** creates `ArrayObject(circleGlyph, centerGlyph, runeGlyphs)`, calls center symbol's `CenterEffect`, stores returned scratchData in the array object, registers via `MagicArrayManager.registerArrayObj()`

**Glyph invalidation — array teardown:**
- When any glyph is invalidated (`symbol_id` change, block replacement), `invalidateGlyphs` checks if the glyph is bound to an `ArrayObject` via `MagicArrayManager.getArrayForGlyph()`
- If bound: fires the center symbol's `EndEffect(scratchData)` and unregisters the array
- Only the triggering glyph is cleaned up — runes and center symbol survive independently
- A new circle drawn over the old area will find the surviving inner glyphs and re-form the array

**Block replacement handling:**
- `LevelChunkMixin` calls `onBlockReplaced()` from `LevelChunk.setBlockState`
- Any glyph touching the replaced block is invalidated through chunk attachment lookup
- New block entities are queued briefly; once Painter exposes non-empty `CanvasData.faces()`, stale `glyph_id`/`symbol_id` marks are cleared, `mana` remains, and the canvas is rescanned

**Calls/Depends on:** `MagicArrayManager`, `PixelPos`, `PositionedGlyph`, `SymbolMatch`, `SymbolRole`, `FloodFillExtractor.*`, `FloodFillScheduler`, `GlyphChunkStorage`, `GlyphMarker`, `InteriorValidator`, `ManaPixelDetector`, `SymbolRecognizer`, `SymbolRegistry`, `SyncGlyphPacket`, `ModAttachments`

---

### 3.7 Network Package

| Class | Direction | Content |
|-------|-----------|---------|
| `ModNetwork.java` | — | `@EventBusSubscriber`: registers both packet types under version `"1"` |
| `SyncDebugElementPacket.java` | Server→Client | Full snapshot: `List<BlockPos>` + flat `long[] values, derivatives`. Client handler → `ElementDebugRenderer.replaceDebugData()` |
| `SyncGlyphPacket.java` | Server→Client | Full snapshot: `List<GlyphData>` (glyphId, symbolId, confidence, pos, face, front, length, width, bounding box). Client handler → `ElementDebugRenderer.replaceGlyphData()` |

---

### 3.8 Client Package

##### `ClientSetup.java` (`@EventBusSubscriber(Dist.CLIENT)`)
**Role:** Registers `GlyphImageProvider` (priority 2) and `GlyphRenderer` (priority 2) with the Pigmentum canvas rendering pipeline.

##### `ElementDebugRenderer.java`
**Role:** Renders debug overlay for element concentrations and glyph labels.  
**Data sources:** `SyncDebugElementPacket` (element data), `SyncGlyphPacket` (glyph data).  
**Rendering:**
- Colored translucent quads on nearby blocks (radius 12), colored by dominant element type
- Floating text labels for recognized glyphs (symbol path + confidence + size)
- Cyan direction arrows for recognized glyphs, using synced `front`
**Toggle:** `/gyromancy debug <bool>`

##### `GlyphImageProvider.java` (implements `CanvasImageProvider`)
**Role:** Generates colored glyph textures from the `gyromancy:symbol_id` effect layer.  
**Name:** `"gyromancy_glyph"`. Reads pixel data → looks up `SymbolTemplate.glyphColor` → outputs `NativeImage` (ABGR).

##### `GlyphRenderer.java` (implements `CanvasPixelRenderer`)
**Role:** Renders glyph overlay textures on canvas faces at full brightness (`0x00F000F0`). Uses `RenderType.entityTranslucent()`.

---

### 3.9 Ink Package

##### `InkRegistry.java` (`@EventBusSubscriber`)
**Role:** Registers default ink types into `GyromancyRegistries.INK` during `RegisterEvent`.  
**Configuration:** A static `InkDef[] INKS` array — each entry defines (name, ARGB color, manaValue).  
**Currently registered:** `mana_ink` (white, mana=20).  
**Extensibility:** To add an ink, add an `InkDef` entry. No other code changes needed — `PenItem` reads whichever ink is in the offhand via the `INK_TYPE` data component.

**Calls/Depends on:** `InkType`, `GyromancyRegistries`

---

### 3.10 Item Package

##### `DebugBrushItem.java` (implements `IPaintProvider` from Pigmentum)
**Role:** Debug brush that paints single white mana dots (`gyromancy:mana` effect = 10, brush diameter = 1/16). Right-click, distance-threshold gated at step 0.005.

##### `PenItem.java` (implements `IPaintProvider` from Pigmentum)
**Role:** Main-hand brush that reads ink from the offhand `InkBottleItem`.  
**Key behaviors:**
- `getColor()` — resolves `INK_TYPE` from offhand → `InkType` in registry → returns ink color
- `getPattern()` — reads `PenProperties.brushSize` → circular `PaintPattern`; `PixelProvider` writes `gyromancy:mana` + ink `effectKeys`
- `shouldPaint()` — distance gate (step 0.02) + ink consumption every 10th paint action from offhand `INK_REMAINING`
- Constructor sets `PEN_PROPERTIES` (brushSize=1/32) and Pigmentum data components

**Calls/Depends on:** `InkType`, `PenProperties`, `InkBottleItem`, `GyromancyRegistries`, `ManaPixelDetector`, Pigmentum `IPaintProvider`/`PaintPattern`/`PixelProvider`/`PaintProviders`

##### `InkBottleItem.java`
**Role:** Offhand item holding ink data.  
**Data components:** `INK_TYPE` (default `gyromancy:mana_ink`), `INK_REMAINING` (max 64).  
**Durability bar:** Cyan bar visible when `INK_REMAINING < MAX_INK`, width proportional to remaining charges.  
**Dynamic name:** If `INK_TYPE` is not `"air"`, name is `item.gyromancy.ink_bottle.filled` with the ink name inserted.

**Calls/Depends on:** `ModDataComponents`

---

### 3.11 Other Modules

##### `DebugCommands.java`
**Role:** Server-side debug commands under `/gyromancy`: `set <element> <value> <pos>`, `clear`, `test match` (runs `SkeletonMatcher` self-test on template PNGs).

##### `LevelChunkMixin.java`
**Role:** Mixin into `LevelChunk` implementing `IElementChunkAccessor`. Stores/retrieves element override `HashMap` via `ModAttachments.ELEMENT_OVERRIDES`. Also injects `setBlockState` to notify `MagicArrayDetector` when block replacement may invalidate glyphs or place moved canvas data.

---

### 3.12 Util Package

| Class | Role | Lines |
|-------|------|-------|
| `TemplateLoader.java` | Loads 32×32 PNGs from classpath → binary `int[][]` patterns (threshold 0x202020). Caches raw & normalized variants. | ~ |
| `CanvasScanUtils.java` | 8-connected BFS component extraction from `IPixelMatrix`/`int[][]`. Area & aspect ratio filtering. | ~ |
| `GeometryPreprocessUtils.java` | Full skeleton-extraction pipeline: hole-fill, connectivity-preserving upscale (min 128), separable Gaussian smooth, Guo-Hall thinning, node extraction, edge tracing, merge-zero, prune-short, split at support points. Types: `SkelNode`, `SkelEdge` | ~ |
| `GeometryUtils.java` | Large utility (1705 lines): SSIM, PCA, contour tracing, Zhang-Suen thinning, CSS corner detection (4 scales, cross-scale voting), turning function, cyclic DTW, curvature function, centroid distance function, shape context (60-bin histograms), morphology ops, skeleton graph building (graph nodes/edges, cycle detection), pruning, normalization, rotation, mirror | ~ |

---

## 4. Cross-Cutting Dependency Graph

```
                        ┌──────────────────┐
                        │  Gyromancy.java   │  ← main @Mod
                        └────────┬─────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              ▼                  ▼                  ▼
    ┌─────────────────┐  ┌─────────────┐  ┌──────────────────┐
    │ registry.*      │  │ element.*   │  │ command.*        │
    │ ModBlocks,      │  │ TickProc    │  │ DebugCommands    │
    │ ModItems,       │  │ ChunkProc   │  └──────────────────┘
    │ ModAttach, etc  │  │ EventHandler│
    └────────┬────────┘  └──────┬──────┘
             │                  │
    ┌────────┴────────┐  ┌─────┴──────────────────────────────┐
    │ api.*           │  │ element.event.*                     │
    │ ElementType     │  │ ElementEventBus, ThresholdEvent,    │
    │ ElementConcent  │  │ ChangeEvent, Activation, Cleanup    │
    │ SymbolTemplate  │  └────────────────────────────────────┘
    │ ArrayObject     │
    │ SymbolMatch     │
    │ MagicArrayMgr   │  ┌────────────────────────────────────┐
    │ IArrayEffect    │  │ network.*                          │
    │ PixelPos, etc   │  │ SyncDebugElementPacket             │
    └────────┬────────┘  │ SyncGlyphPacket                    │
             │           └──────────┬─────────────────────────┘
             │                      │
    ┌────────┴──────────────────────┴────────────────────────────┐
    │ symbol.*                                                    │
    │ ManaPixelDetector → FloodFillScheduler → FloodFillExtractor │
    │   → SymbolRecognizer → SkeletonMatcher / MLSymbolMatcher    │
    │   → GlyphMarker → InteriorValidator                        │
    │   → SymbolRegistry (templates)                              │
    └────────┬───────────────────────────────────────────────────┘
             │
    ┌────────┴────────────┐
    │ util.*              │
    │ GeometryUtils       │  ← 1700+ lines of image processing
    │ GeometryPreprocess  │  ← skeleton graph extraction
    │ TemplateLoader      │  ← PNG→binary conversion
    │ CanvasScanUtils     │  ← connected components
    └─────────────────────┘

    ┌─────────────────────────────────────────────────────────┐
    │ array.*                                                  │
    │ MagicArrayDetector ← bridges canvas events→recognition   │
    │   → MagicArrayManager (stores positioned glyphs/arrays)  │
    └─────────────────────────────────────────────────────────┘

    ┌─────────────────────────────────────────────────────────┐
    │ client.*                                                 │
    │ ClientSetup → GlyphImageProvider + GlyphRenderer         │
    │ ElementDebugRenderer ← receives network packets          │
    │   ↕ GyromancyClient (toggle command + render hook)       │
    └─────────────────────────────────────────────────────────┘
```

### Module Dependency Order (bottom-up)

```
util ───────────► api ───────────► element ───────────► network
  │                │                 │                    │
  └────────────────┼─────────────────┼────────────────────┤
                   │                 │                    │
                   ▼                 ▼                    ▼
               symbol            registry              client
                   │                 ▲                    │
                   └─────────────────┼────────────────────┘
                                     │
                   ┌─────────────────┘
                   ▼
                array (MagicArrayDetector)
                   │
                   ▼
                command (DebugCommands)
                   │
                   ▼
              root (Gyromancy, GyromancyClient)
```

---

## 5. External Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| **Pigmentum** (`com.astune.painter`) | 0.5.8beta | Multi-block canvas system, faces, effect layers, paint providers, rendering pipeline |
| **Veil** (`foundry.veil`) | 4.2.1 | Post-processing bloom pipeline for glyph rendering (4 GLSL shaders) |
| **ONNX Runtime** (`com.microsoft.onnxruntime`) | 1.18.0 | ML-based symbol matching via siamese network |
| **NeoForge** | 21.1.233 | Mod platform (Minecraft 1.21.1) |
| **SpongePowered Mixin** | bundled | Bytecode mixin for `LevelChunkMixin` |
| **Gson** | bundled | JSON parsing (ML model metadata) |

---

## 6. Key Data Flows

### 6.1 Element Concentration Lifecycle
```
Biome default (ElementBiomeProvider)
  │
  ├── Player paints mana → override written (ElementStorageManager)
  │     └── ElementChunkEventHandler.markActive()
  │     └── ElementEventBus.fireActivation()
  │
  ├── Every 10 ticks: ElementTickProcessor → ElementChunkProcessor
  │     ├── decayAndRecover()
  │     ├── diffuse() excess sharing
  │     └── fire threshold/change/cleanup events
  │
  └── When all values return to biome defaults → fireCleanup() → markInactive()
```

### 6.2 Symbol Detection & Recognition
```
1. Player draws with DebugBrush → mana pixels on canvas face
2. ServerCanvasUpdateEvent fires → MagicArrayDetector scans for new seeds
3. FloodFillScheduler budgets extraction across ticks (max 20 blocks/tick)
4. FloodFillExtractor BFS connects cross-block mana pixels → ExtractedGlyph
5. Callback: SymbolRecognizer rasterizes → SkeletonMatcher computes stats
6. Stage 1 hard pass (open/closed lines) → Stage 2 soft pass (graph edit) → pose from matched edge rotation
7. InteriorValidator checks no raw mana remains inside
8. GlyphMarker marks pixels consumed
9. MagicArrayManager indexes PositionedGlyph at runtime
10. GlyphChunkStorage persists PositionedGlyph on touched chunks
11. SyncGlyphPacket → client for debug labels and direction arrows
```

### 6.3 Array Lifecycle (phrase5)
```
OUTER_CIRCLE drawn on canvas:
  handleCircleMatch
    → InteriorValidator.findGlyphsInside() → classify by role
    → Stage 1: exactly 1 CENTER_SYMBOL? 0+ PARAMETER_RUNE?
    → If invalid: log reject, return
    → Store circle as PositionedGlyph (OUTER_CIRCLE role)
    → Create ArrayObject(circle, center, runes)
    → Call center symbol's CenterEffect → store scratchData
    → MagicArrayManager.registerArrayObj()

Any bound glyph invalidated (circle redrawn, rune overwritten):
  invalidateGlyphs
    → MagicArrayManager.getArrayForGlyph(glyphUuid)
    → Fire EndEffect(scratchData)
    → MagicArrayManager.unregisterArrayObj()
    → Only the triggering glyph is cleaned up; other bound glyphs (runes, center) survive independently
```

### 6.4 Network Sync
```
Server-side:
  ElementTickProcessor (every 10 ticks)
    → builds HashMap<BlockPos, ElementConcentrations> from all active chunks
    → SyncDebugElementPacket(positions, flatValues, flatDerivatives)
    → send to all players

  MagicArrayDetector (on glyph recognized)
    → SyncGlyphPacket(List<GlyphData>)
    → send to all players

Client-side:
  SyncDebugElementPacket.handleClient()
    → ElementDebugRenderer.replaceDebugData()
  SyncGlyphPacket.handleClient()
    → ElementDebugRenderer.replaceGlyphData()
  ElementDebugRenderer.onRenderLevelStage (AFTER_PARTICLES)
    → renders colored quads + glyph text/arrows
```

---

## 7. Test Suite

| Test Class | Tests | Type |
|------------|-------|------|
| `CanvasScanUtilsTest.java` | 7 tests | Connected-component extraction (area, aspect ratio, diagonal connectivity, sorting) |
| `GeometryUtilsTest.java` | 8 tests | SSIM, PCA orientation, bounding boxes, centroids |
| `SkeletonExportTest.java` | Visual debug | End-to-end pipeline: PNG → skeleton graph → debug PNG output |
| `SymbolMatcherTest.java` | Production debug | Full skeleton matching against template library (hard + soft thresholds, per-template scoring) |

---

## 8. Empty / Placeholder Directories

| Directory | Purpose |
|-----------|---------|
| `array/effect/` | Future `IArrayEffect` implementations |
| `client/screen/` | Future GUI screens |
| `compile/` | Planned — maybe compile-time code generation |
| `data/` | Planned — data generation |
| `entity/` | Future `PseudoEntity` subclasses |

---

## 9. Next Architecture Work

| Document | Purpose |
|----------|---------|
| `array-script.md` | Next-phase magic-array compiler architecture: spatial AST, script IR, runtime shells, reusable operations, and projectile/entity rule decomposition. |

---

*Generated 2026-06-28*
