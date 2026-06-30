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

**Builder defaults:**

```java
InkType.builder(id)
    .color(0xFFFFFFFF)   // white
    .manaValue(20)       // mana per pixel
    .build();
```

The `effectKey(String key, int value)` builder method accumulates entries into the `effectKeys` map. For the base `mana_ink`, no extra keys are added.

`manaValue` and `effectKeys` are the extensibility points: a fire ink would set `manaValue(8).effectKey("gyromancy:element", 1)`. The `PenItem` reads these and writes them to the canvas — no code change needed in the pen.

## 3. Ink Registry

`InkRegistry` is an `@EventBusSubscriber` that registers default ink types during `RegisterEvent`.

**Configuration point — add new inks here:**

```java
private static final InkDef[] INKS = {
    new InkDef("mana_ink", 0xFFFFFFFF, 20),
};
```

Each `InkDef` specifies name, ARGB color, and mana value. Registration:

```java
event.register(GyromancyRegistries.INK_KEY, registry -> {
    for (InkDef def : INKS) {
        InkType ink = InkType.builder(rl(def.name))
                .color(def.color)
                .manaValue(def.manaValue)
                .build();
        registry.register(rl(def.name), ink);
    }
});
```

The `INK` registry (defined in `GyromancyRegistries`) is synced to clients. New ink types are resolved at paint time via `GyromancyRegistries.INK.get(inkId)`.

## 4. Ink Bottle Item

`InkBottleItem` is a simple `Item` that holds two data components:

- `INK_TYPE` (`ResourceLocation`) — points into the `gyromancy:ink` registry
- `INK_REMAINING` (`int`) — remaining paint actions, default `MAX_INK = 64`

**Durability bar:** The item overrides `isBarVisible()`, `getBarWidth()`, and `getBarColor()` to show a cyan bar proportional to remaining ink. The bar is only visible when `INK_REMAINING < MAX_INK`.

**Dynamic name:** `getName()` checks the `INK_TYPE` component. If it is not `"air"`, the name is resolved as:

```java
Component.translatable(
    "item.gyromancy.ink_bottle.filled",
    Component.translatable("ink.gyromancy." + inkType.getPath()));
```

**Extensibility:** To add a new ink, register it in `InkRegistry` and give an ink bottle the matching `INK_TYPE` component. The `InkBottleItem` class itself never changes.

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
int remaining = offhand.getOrDefault(ModDataComponents.INK_REMAINING.get(), 0);
if (remaining <= 0) return false;

if (consumeCounter % 10 == 0)
    offhand.set(ModDataComponents.INK_REMAINING.get(), remaining - 1);
consumeCounter = consumeCounter + 1 % 10;
```

Ink is consumed once every 10 paint actions (every 10th `shouldPaint()` call that reaches this point). This means 64 ink charges support approximately 640 paint strokes before the bottle runs out.

### 5.5 Offhand Ink Resolution

```java
private static InkType resolveOffhandInk(Player player) {
    ItemStack offhand = player.getOffhandItem();
    if (offhand.isEmpty() || !(offhand.getItem() instanceof InkBottleItem))
        return null;
    ResourceLocation inkId = offhand.get(ModDataComponents.INK_TYPE.get());
    if (inkId == null) return null;
    return GyromancyRegistries.INK.get(inkId);
}
```

If the offhand is empty, not an ink bottle, or has no `INK_TYPE` component, the pen returns null colors and patterns — no painting occurs.

## 6. Data Components

Three custom data component types are registered in `ModDataComponents`:

| Component | Type | Used By | Purpose |
|-----------|------|---------|---------|
| `gyromancy:ink_type` | `ResourceLocation` | `InkBottleItem` | Links to an `InkType` in the `INK` registry |
| `gyromancy:pen_properties` | `PenProperties` | `PenItem` | Brush size, precision, drawing speed |
| `gyromancy:ink_remaining` | `int` | `InkBottleItem` | Remaining paint charges |

All three are persistent and network-synchronized.

## 7. Creative Tab

`ModCreativeTabs.GYROMANCY_TAB` includes three items:

```java
output.accept(ModItems.DEBUG_BRUSH.get());
output.accept(ModItems.PEN.get());
output.accept(ModItems.INK_BOTTLE.get());
```

The tab icon remains the debug brush.

## 8. Extensibility

### Adding a new ink type

Add one entry to `InkRegistry.INKS`:

```java
new InkDef("fire_ink", 0xFFFF4400, 8),
```

Then create an ink bottle with the matching `INK_TYPE` component. The pen automatically reads it — no code changes in `PenItem` or `InkBottleItem`.

Optional: add extra effect layers via the builder:

```java
InkType.builder(id)
    .color(0xFFFF4400)
    .manaValue(8)
    .effectKey("gyromancy:element", 0)  // FIRE ordinal
    .build();
```

### Adding a new pen tier

Create a new item registration with modified `PEN_PROPERTIES` defaults. The same `PenItem` class can be reused — just register it under a different name with different component defaults.

## 9. Item Models & Textures

| File | Parent | Purpose |
|------|--------|---------|
| `models/item/pen.json` | `minecraft:item/handheld` | Held-item rendering for the pen |
| `models/item/ink_bottle.json` | `minecraft:item/generated` | Flat item rendering for the ink bottle |
| `textures/item/pen.png` | — | 16×16 brush icon |
| `textures/item/ink_bottle.png` | — | 16×16 bottle icon |
