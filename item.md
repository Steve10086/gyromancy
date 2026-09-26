# Item & Ink System

This document covers the pen/ink item flow from offhand ink resolution through painting on canvas to ink consumption. It does not describe the internals of `IPaintProvider` or Pigmentum's canvas pipeline.

## 1. Two-Slot Model

The system uses two items that work together across the main hand and offhand:

```
Main hand: PenItem                     Offhand: InkBottleItem
├─ PEN_PROPERTIES → brushSize=1/32    ├─ INK_TYPE → mana_ink ref
│   precision=1.0, drawingSpeed=2     └─ INK_REMAINING → decrements per N paint actions
└─ Implements IPaintProvider
       │
       ▼ reads offhand InkBottleItem
  getColor()        → resolve INK_TYPE → InkType.color
  getPattern()      → PEN_PROPERTIES.brushSize → circle radius
  getEffectValues() → InkType.manaValue + effectKeys map
  shouldPaint()     → distance gate + consume ink every 10th action
```

The pen provides the brush geometry; the ink bottle provides the color and effect data. The pen reads whichever ink is in the offhand — no hardcoded ink type.

## 2. Ink Type Definition

`InkType` is a builder-pattern class stored in the `gyromancy:ink` custom NeoForge registry.

**Fields:**

| Field | Type | Purpose |
|-------|------|---------|
| `id` | `ResourceLocation` | Registry key |
| `color` | `int` (ARGB) | Pixel color written to canvas |
| `magicalAffinity` | `float` (0–2) | Reserved for future array power multiplier |
| `primaryElement` | `ElementType` | Element this ink resonates with |
| `elementBoost` | `float` (0–2) | Reserved for future element concentration modifier |
| `manaValue` | `int` | Value written to the `gyromancy:mana` effect layer |
| `effectKeys` | `Map<String, Integer>` | Additional effect layers to write (empty for base ink) |
| `overlayTint` | `int` (ARGB) | Tint multiplied onto the bottle's ink overlay layer |

**Builder defaults:**

```java
InkType.builder(id)
    .color(0xFFFFFFFF)     // white
    .manaValue(20)         // mana per pixel
    .overlayTint(0xFFFFFFFF) // show the overlay texture as-is
    .build();
```

The `effectKey(String key, int value)` builder method accumulates entries into the `effectKeys` map. For the base `mana_ink`, no extra keys are added.

**Overlay tint:** an ink whose overlay texture is grayscale sets `overlayTint` to its color so the layer is tinted; an ink with a fully colored overlay texture keeps `0xFFFFFFFF` and is drawn unchanged.

`manaValue` and `effectKeys` are the extensibility points: a fire ink would set `manaValue(8).effectKey("gyromancy:element", 1)`. The `PenItem` reads these and writes them to the canvas — no code change needed in the pen.

## 3. Ink Registry

`InkRegistry` is an `@EventBusSubscriber` that registers default ink types during `RegisterEvent`.

**Configuration point — add new inks here:**

```java
private static final InkDef[] INKS = {
    new InkDef("mana_ink", 0xFFFFFFFF, 20, 0xFFFFFFFF),
    new InkDef("water_ink", 0xFF7FC8F8, 21, 0xFF1E4FBF),
    new InkDef("fire_ink", 0xFFFF8A80, 22, 0xFFB30000),
    new InkDef("earth_ink", 0xFFCFA47A, 23, 0xFF8B5A2B),
    new InkDef("wind_ink", 0xFFB7E8A0, 24, 0xFF90EE90),
    new InkDef("water_mana_ink", 0xFF2F6FA8, 25, 0xFF1E4FBF),
    new InkDef("fire_mana_ink", 0xFFC43A2A, 26, 0xFFB30000),
    new InkDef("earth_mana_ink", 0xFF8A6238, 27, 0xFF8B5A2B),
    new InkDef("wind_mana_ink", 0xFF5F9E58, 28, 0xFF90EE90),
    new InkDef("light_mana_ink", 0xFFD9B84A, 29, 0xFFFFE080),
    new InkDef("dark_mana_ink", 0xFF5B3FA8, 30, 0xFF4B2E83),
};
```

Each `InkDef` specifies name, canvas ARGB color, mana id, and overlay tint. The mana id is written to the `gyromancy:mana` effect layer and resolved through `ManaIdTable`, whose rows follow `ElementType` order (wind, fire, water, earth, light, dark, space, time, mana) — e.g. id 21 is `0,0,1,0,0,0,0,0,0` for water. Registration order fixes the registry ids, so the element ink registry ids are 1..4 and their model overrides use `2..5`. Registration:

```java
event.register(GyromancyRegistries.INK_KEY, registry -> {
    for (InkDef def : INKS) {
        InkType ink = InkType.builder(rl(def.name))
                .color(def.color)
                .manaValue(def.manaValue)
                .overlayTint(def.overlayTint)
                .build();
        registry.register(rl(def.name), ink);
    }
});
```

The `INK` registry (defined in `GyromancyRegistries`) is synced to clients. New ink types are resolved at paint time via `GyromancyRegistries.INK.get(inkId)`.

## 4. Ink Bottle Item

`InkBottleItem` is the single item shared by the empty bottle and every ink. It holds two optional data components:

- `INK_TYPE` (`ResourceLocation`) — points into the `gyromancy:ink` registry
- `INK_REMAINING` (`int`) — remaining paint charges, capacity `MAX_INK = 6400`

**States:**

| State | Components | Behaviour |
|-------|------------|-----------|
| Base (empty) | none | Cannot paint; no durability bar |
| Filled | `INK_TYPE` + `INK_REMAINING` | Paints; bar shows remaining charges |

**Mixing:** `mix(bottle, recipeInk, amount)` runs `InkContents.mix`, which stacks charges when the bottle already holds the recipe's ink and replaces the contents otherwise (bounded by `MAX_INK`).

**Consumption:** `consume(stack, amount)` subtracts charges; when the last charge is spent the components are cleared and the stack reverts to the empty base bottle instead of breaking.

**Durability bar:** `isBarVisible()` is true while ink is present; `getBarWidth()` scales by `MAX_INK` and `getBarColor()` uses the ink's color.

**Names:** the item only has two names — `item.gyromancy.ink_bottle` for a filled bottle and `item.gyromancy.ink_bottle.empty` for the base state. The ink name, its `ManaIdTable` element row (`|` separated, each value colored by `ElementType.color()`) and the remaining charges (`tooltip.gyromancy.ink_remaining`) are shown in the hover tooltip.

**Appearance:** `inkStyleId(stack)` returns `0` for an empty bottle and `inkRegistryId + 1` for a filled one. The client registers it as the `gyromancy:ink` item property; `models/item/ink_bottle.json` overrides select `gyromancy:item/ink_bottle/<ink_path>`, so the ink id alone drives the overlay model and texture.

## 5. Pen Item

`PenItem` extends `Item` and implements `IPaintProvider` from the Pigmentum API. It is the main-hand brush.

### 5.1 Construction

```java
public PenItem() {
    super(new Properties()
            .stacksTo(1)
            .component(ModDataComponents.PEN_PROPERTIES.get(), PenProperties.DEFAULT)
            .component(PainterDataComponents.CURRENT_COLOR.get(), 0xFFFFFFFF)
            .component(PainterDataComponents.BRUSH_SIZE.get(), DEFAULT_BRUSH_DIAMETER)
            .component(PainterDataComponents.FEATHER_STRENGTH.get(), 0.0f)
            .component(PainterDataComponents.OPACITY.get(), 1.0f)
            .component(PainterDataComponents.BLEND_MODE.get(), BlendMode.OVERWRITE.name())
            .component(PainterDataComponents.STEP_SIZE.get(), STEP));
    PaintProviders.register(this, this);
}
```

It sets both Gyromancy data components (`PEN_PROPERTIES`) and Pigmentum data components (`CURRENT_COLOR`, `BRUSH_SIZE`, `FEATHER_STRENGTH`, `OPACITY`, `BLEND_MODE`, `STEP_SIZE`). `PaintProviders.register(this, this)` registers the item as its own `IPaintProvider`.

**Constants:**

| Constant | Value | Purpose |
|----------|-------|---------|
| `DEFAULT_BRUSH_DIAMETER` | `1.0 / 32.0` | Half-pixel brush on a 16×16 face |
| `STEP` | `0.02` | Minimum distance between paint actions |
| `MANA_KEY` | `"gyromancy:mana"` | Effect layer key (from `ManaPixelDetector`) |

### 5.2 Color Resolution

`getColor()` reads the offhand ink bottle, resolves the `INK_TYPE` component to an `InkType` in the registry, and returns its color:

```java
public Integer getColor(ItemStack stack, Player player, Level level,
                        BlockPos pos, CanvasFace face, int pixelX, int pixelY) {
    InkType ink = resolveOffhandInk(player);
    return ink != null ? ink.getColor() : null;
}
```

### 5.3 Pattern Generation

`getPattern()` creates a `PaintPattern` with a circular `PixelProvider`. The circle diameter comes from `PenProperties.brushSize` on this item's `PEN_PROPERTIES` component.

The `PixelProvider`:

```java
// getPixel(dx, dy):
//   returns ink.getColor() for pixels inside the circle radius
//   returns null outside

// getEffectValues(dx, dy):
//   inside circle → Map.of(MANA_KEY, manaValue) + effectKeys
//   outside circle → Collections.emptyMap()
```

`getBlendMode()` returns `BlendMode.OVERWRITE` — same as the debug brush. Effect values replace existing values at each pixel.

### 5.4 Paint Gating & Ink Consumption

`shouldPaint()` implements two gates:

1. **Client-side use check:** Requires `keyUse.isDown()` on the client.
2. **Distance gate:** Same tracking as `DebugBrushItem` — `lastHitLoc.distanceTo(result.getLocation()) > STEP`.

After both gates pass, ink is consumed from the offhand bottle:

```java
ItemStack offhand = player.getOffhandItem();
if (!InkBottleItem.hasInk(offhand)) return false;

if (consumeCounter % 10 == 0)
    InkBottleItem.consume(offhand, 1);
consumeCounter = (consumeCounter + 1) % 10;
```

Ink is consumed once every 10 paint actions (every 10th `shouldPaint()` call that reaches this point). This means 6400 ink charges support approximately 64000 paint strokes before the bottle empties back to its base state.

### 5.5 Offhand Ink Resolution

```java
private static InkType resolveOffhandInk(Player player) {
    ItemStack offhand = player.getOffhandItem();
    return InkBottleItem.hasInk(offhand) ? InkBottleItem.resolveInk(offhand) : null;
}
```

If the offhand is empty, not an ink bottle, or holds no charged ink, the pen returns null colors and patterns — no painting occurs. The canvas editor's `canvasStroke` also returns empty for the same reason.

## 6. Data Components

Three custom data component types are registered in `ModDataComponents`:

| Component | Type | Used By | Purpose |
|-----------|------|---------|---------|
| `gyromancy:ink_type` | `ResourceLocation` | `InkBottleItem` | Links to an `InkType` in the `INK` registry |
| `gyromancy:pen_properties` | `PenProperties` | `PenItem` | Brush size, precision, drawing speed |
| `gyromancy:ink_remaining` | `int` | `InkBottleItem` | Remaining paint charges |

All three are persistent and network-synchronized.

## 7. Ink Mixing Recipes

Mixing is a custom data-driven recipe, `gyromancy:ink_mixing` (`InkMixingRecipe`):

```json
{
  "type": "gyromancy:ink_mixing",
  "category": "misc",
  "ink": "gyromancy:mana_ink",
  "amount": 6400,
  "ingredients": [ { "item": "minecraft:ink_sac" }, { "item": "gyromancy:silver_powder" } ]
}
```

- Matching requires exactly one ink bottle (any state) plus the listed ingredients, shapeless.
- `ink` must exist in the `gyromancy:ink` registry (validated at load time); the crafted stack stores that id in `INK_TYPE`.
- `amount` is bounded to `1..MAX_INK`. Same ink stacks; any other state is replaced.
- The empty bottle itself is crafted by the shaped recipe `gyromancy:ink` (wooden button + glass bottle, no ink sac).

## 8. Creative Tab

`ModCreativeTabs.GYROMANCY_TAB` includes an empty ink bottle and a full mana ink bottle:

```java
output.accept(ModItems.INK_BOTTLE.get());
output.accept(InkBottleItem.createFilled(InkRegistry.MANA_INK, InkBottleItem.MAX_INK));
```

The tab icon remains the debug brush.

## 9. Extensibility

### Adding a new ink type

1. Add an entry to `InkRegistry.INKS`:

```java
new InkDef("fire_ink", 0xFFFF4400, 8, 0xFFFFFFFF),
```

2. Add a mixing recipe `data/gyromancy/recipe/fire_ink.json` referencing that id.
3. Add the overlay model `models/item/ink_bottle/fire_ink.json` (layer0 = `gyromancy:item/ink_bottle/fire_ink`, layer1 = `gyromancy:item/ink_bottle`) and its texture.
4. Add an override entry to `models/item/ink_bottle.json` with predicate `gyromancy:ink` equal to the ink's registry id + 1. Overrides are matched as thresholds, so keep them in ascending order.

The pen reads the stored ink id automatically — no code changes in `PenItem` or `InkBottleItem`.

Optional: add extra effect layers via the builder:

```java
InkType.builder(id)
    .color(0xFFFF4400)
    .manaValue(8)
    .overlayTint(0xFFFF4400)            // tint a grayscale overlay
    .effectKey("gyromancy:element", 0)  // FIRE ordinal
    .build();
```

### Adding a new pen tier

Create a new item registration with modified `PEN_PROPERTIES` defaults. The same `PenItem` class can be reused — just register it under a different name with different component defaults.

## 10. Item Models & Textures

| File | Parent | Purpose |
|------|--------|---------|
| `models/item/pen.json` | `minecraft:item/handheld` | Held-item rendering for the pen |
| `models/item/ink_bottle.json` | `minecraft:item/generated` | Empty bottle plus the `gyromancy:ink` model overrides |
| `models/item/ink_bottle/<ink>.json` | `minecraft:item/generated` | Filled bottle: layer0 = ink layer (below), layer1 = bottle (above) |
| `textures/item/pen.png` | — | 16×16 brush icon |
| `textures/item/ink_bottle.png` | — | 16×16 bottle icon |
| `textures/item/ink_default.png` | — | Shared default ink layer used by untinted inks |
| `textures/item/ink_bottle/<ink>.png` | — | Per-ink overlay (grayscale or colored) |
