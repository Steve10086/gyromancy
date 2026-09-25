# Pigmentum (`painter`) — Dependency Guide

## Overview

**Pigmentum** (mod id `painter`) is a Minecraft 1.21.1 / NeoForge library by **astune** whose public API covers painting on blocks: multi-block canvases, per-face pixel matrices, effect layers, paint providers, server sync events/packets, and client-side texture/renderer hooks.

Verified artifact metadata from `META-INF/neoforge.mods.toml` inside `maven.modrinth--pigmentum-0.6.1beta.jar`:

| Field | Value |
|-------|-------|
| Display name | `Pigmentum` |
| Mod id | `painter` |
| Version | `0.6.1beta` |
| Author | `astune` |
| License (as written) | `MIT Lisence` |
| NeoForge requirement | `[21.1.228,)` |
| Minecraft requirement | `[1.21.1]` |
| Mixin config | `painter.mixins.json` (required) |

The JAR bundles its own GuideWeaver index (`GUIDE.md`), but it only points at the upstream repository `git@github.com:Steve10086/neoforge_painter.git`; this file is the local reference.

## Coordinates

| Field | Value |
|-------|-------|
| **Group** | `maven.modrinth` |
| **Artifact** | `pigmentum` |
| **Version** | `0.6.1beta` |
| **Maven** | `https://api.modrinth.com/maven` |
| **Packaged JAR** | `libs/maven.modrinth--pigmentum-0.6.1beta.jar` |
| **Local repo copy** | `repo/maven/modrinth/pigmentum/0.6.1beta/pigmentum-0.6.1beta.jar` |
| **POM** | `repo/maven/modrinth/pigmentum/0.6.1beta/pigmentum-0.6.1beta.pom` (no dependencies declared) |

### Gradle Setup

Exactly as used in `build.gradle` (local repo is checked first, Modrinth is the fallback for the `maven.modrinth` group):

```groovy
repositories {
    maven {
        name = "LocalRepo"
        url = file("${project.projectDir}/repo")
    }
    maven {
        name = "Modrinth"
        url = "https://api.modrinth.com/maven"
        content {
            includeGroup "maven.modrinth"
        }
    }
}

dependencies {
    // Gyromancy embeds Pigmentum via Jar-in-Jar for clean-instance runtime presence.
    jarJar(implementation("maven.modrinth:pigmentum:${pigmentum_version}"))
}
```

`gradle.properties`:

```properties
pigmentum_version=0.6.1beta
pigmentum_version_range=[0.6,0.7)
```

The JAR dropped into `libs/` is auto-installed into `./repo` by the `autoInstallLocalDeps` task (`<group>--<artifact>-<version>.jar` naming), which is why the artifact resolves locally without remote metadata.

## Core Architecture

Pigmentum's model is a per-block `CanvasData` made of `CanvasFace` surfaces. Each face owns a 16×16 `PixelMatrix` plus an arbitrary number of named *effect layers* (`Map<String, int[]>`) used to tag pixels with mod-specific data (Gyromancy uses keys such as `gyromancy:mana`, `gyromancy:glyph_id`, `gyromancy:symbol_id`).

Painting is item-driven:

1. An item implements `IPaintProvider` and is registered with `PaintProviders.register(item, provider)`.
2. The provider returns a `PaintPattern` (size + `PixelProvider`) for a hit.
3. `PixelProvider.getPixel(dx, dy)` returns an ARGB int (or `null` to skip), `PixelProvider.getBlendMode()` selects a `BlendMode`, and `PixelProvider.getEffectValues(dx, dy)` returns effect-layer values to write on the touched pixels.
4. `CanvasFace`/`CanvasData` are synchronized to clients with `SyncCanvasPacket`; server-side mods observe edits through `ServerCanvasUpdateEvent` (and its `Pre` subclass).

Client-side extensibility:

- `CanvasImageProvider` generates a `NativeImage` texture from a face (e.g. from an effect layer). Registered via `CanvasImageProviderRegistry.register(provider, priority)`.
- `CanvasPixelRenderer` draws a face texture using a `RenderContext`. Registered via `CanvasRendererRegistry.registerPixelRenderer(renderer, priority)`.

A key implementation detail: `CanvasDataHolder` is **mixed into vanilla `BlockEntity`** by `com.astune.painter.mixin.BlockEntityMixin` (`@Mixin(BlockEntity.class)`, listed in `painter.mixins.json`), so any block entity can be cast to `CanvasDataHolder` and queried with `painter$getCanvasData()`. `com.astune.painter.block.CanvasBlockEntity` itself does **not** declare the interface.

### Package Structure (verified JAR contents)

```
com.astune.painter                  Painter (MODID="painter"), PainterClient, Config, CanvasProperties
com.astune.painter.api              CanvasData, CanvasFace, CanvasDataHolder, IPixelMatrix, PixelMatrix,
                                    IPaintProvider, PaintPattern, PixelProvider, PaintProviders,
                                    IPaintLayer, CompositePainting, BlendMode, ResourcesBundle, ExposurePredicate
com.astune.painter.api.blend        BlendContext, BlendFunction, DefaultBlendFunctions
com.astune.painter.api.imageProvider CanvasImageProvider, CanvasImageProviderRegistry,
                                    ImageProviderContext, DefaultCanvasImageProvider
com.astune.painter.api.render       CanvasPixelRenderer, CanvasRendererRegistry,
                                    RenderContext, DefaultCanvasPixelRenderer
com.astune.painter.block            CanvasBlock, CanvasBlockEntity, CanvasBlockHelper, CanvasBlockItem,
                                    CanvasBlockModel, OcclusionCanvasBlock, NoOcclusionCanvasBlock
com.astune.painter.client           PaintInputHandler, CanvasTextureManager, CanvasBlockEntityRenderer,
                                    CompositeRenderer, CanvasWorldRenderQueue, ClientSetup, ...
com.astune.painter.command          PainterCommands
com.astune.painter.event            ServerCanvasUpdateEvent (+ Pre), ClientCanvasFrameEvent,
                                    ClientCanvasTickEvent, PaintEvents, CanvasBlockReplacedEvent, ModBusEvents
com.astune.painter.item             Paintbrush, DebugPaintbrush, EffectCreator, CanvasSheet
com.astune.painter.mixin            BlockEntityMixin (BlockEntity -> CanvasDataHolder), piston/level/etc. mixins
com.astune.painter.network          SyncCanvasPacket, PaintPixelPacket, CanvasUploadPacket, CanvasAction,
                                    CanvasStrokeHistory, ClientCanvasCache, ...
com.astune.painter.registry         ModBlocks, ModItems, ModDataComponents, ModBlockEntities,
                                    ModPaintProviders, ModAttachments, ModCreativeTabs
com.astune.painter.util             CanvasBlacklist, CanvasBlockSetController
```

## API Reference

All signatures below were read from the 0.6.1beta JAR with `javap`.

### Canvas model

```java
public class com.astune.painter.api.CanvasData {
    public static final Codec<CanvasData> CODEC;
    public static final StreamCodec<RegistryFriendlyByteBuf, CanvasData> STREAM_CODEC;
    public CanvasData(List<CanvasFace> faces);
    public static CanvasData empty();
    public long getVersion();
    public void incrementVersion();
    public List<CanvasFace> faces();
    public CanvasFace addOrGetFace(CanvasFace face);
    public CanvasFace tryGetFace(CanvasFace face);
    public List<CanvasFace> getFaceAtHit(BlockPos pos, Vec3 hit);
    public CanvasFace getFaceAtHit(BlockPos pos, BlockHitResult hit);
    public List<CanvasFace> getFaceAtHit(BlockPos pos, Vec3 hit, double tolerance);
    public static CanvasFace calculateCanvasFace(Level, BlockPos, BlockState, Vec3, Direction);
    public static Pair<CanvasData, CanvasFace> getOrCreateCanvasFace(Level, BlockPos, BlockState, Vec3, Direction);
    public Optional<CanvasFace> getFace(Direction dir, Vec3 hit);
    public boolean isEmpty();
}

public class com.astune.painter.api.CanvasFace {
    public static final Codec<CanvasFace> CODEC;
    public static final StreamCodec<RegistryFriendlyByteBuf, CanvasFace> STREAM_CODEC;
    // (Direction, corner0..corner3, PixelMatrix[, Map<String,int[]> effectLayers])
    public CanvasFace(Direction, Vec3, Vec3, Vec3, Vec3, PixelMatrix);
    public CanvasFace(Direction, Vec3, PixelMatrix);
    public int[] getEffectLayer(String key);           // null when layer absent
    public void setEffectLayer(String key, int[] values);
    public Map<String, int[]> getEffectLayers();
    public int getEffectValue(String key, int x, int y);
    public void setEffectValue(String key, int x, int y, int value);
    public void removeEffectLayer(Predicate<String> keyFilter);
    public boolean isSameSurface(CanvasFace other);
    public Vec3[] cornerWithOffset();                  // 4 corners
    public Vec3[] cornerWithOffset(double offset);      // same, offset along normal
    public Direction primaryFace();
    public Vec3 corner0(); public Vec3 corner1(); public Vec3 corner2(); public Vec3 corner3();
    public PixelMatrix pixels();
    public Vec3 centerOffset();
}
```

Note: effect layers are `int[]` in 0.6.1beta. Gyromancy indexes them as `layer[y * width + x]` (e.g. `client/glyph/GlyphImageProvider.java:49-58`).

```java
public interface com.astune.painter.api.IPixelMatrix {
    int SIZE = 16;            // verified constant
    int PIXEL_COUNT = 256;    // verified constant
    int getWidth();
    int getHeight();
    int getPixel(int x, int y);
    boolean setPixel(int x, int y, int argb);
    IPixelMatrix copy();
    void fill(int argb);
    int[] getPixels();
    static IPixelMatrix createEmpty();
    boolean isEmpty();
}

public class com.astune.painter.api.PixelMatrix implements IPixelMatrix {
    public static final Codec<PixelMatrix> CODEC;
    public static final StreamCodec<RegistryFriendlyByteBuf, PixelMatrix> STREAM_CODEC;
    public PixelMatrix(int width, int height);
    public PixelMatrix();
    public void fillWhite();
    public static PixelMatrix fullWhite();
}

public interface com.astune.painter.api.CanvasDataHolder {
    default CanvasData painter$getCanvasData();
    default void painter$setCanvasData(CanvasData data);
    List<Pair<CanvasFace, ResourcesBundle>> painter$getCachedFaceTextures();
    void painter$regenerateTextures(CanvasData data);
    void painter$releaseTextures();
}
```

### Paint providers

```java
public interface com.astune.painter.api.IPaintProvider {
    Integer getColor(ItemStack, Player, Level, BlockPos, CanvasFace, int pixelX, int pixelY);
    PaintPattern getPattern(ItemStack, Player, Level, BlockPos, Vec3 hitLocation);
    default Double getStep();
    default boolean onPaintTick(ItemStack, Player, Level);
    default int getPaintInterval();
    default BlendFunction getCustomBlendFunction(ItemStack);
    default boolean shouldPaint(Player);
    default boolean shouldPaint(Player, BlockHitResult result);          // new in 0.6.1beta
    default Vec3[] transformPatternAxes(Player, Vec3 hit, Direction, double width, double height);
}

public class com.astune.painter.api.PaintProviders {
    public static void register(Item item, IPaintProvider provider);
    public static IPaintProvider getProvider(ItemStack stack);
    public static boolean isPaintbrush(ItemStack stack);
}

public final class com.astune.painter.api.PaintPattern extends Record {
    public PaintPattern(double width, double height, PixelProvider provider);
    public double width(); public double height(); public PixelProvider provider();
}

public interface com.astune.painter.api.PixelProvider {
    BlendMode getBlendMode();
    default BlendMode getBlendMode(double dx, double dy);
    Integer getPixel(double dx, double dy);              // null = leave pixel untouched
    default Map<String, Integer> getEffectValues(double dx, double dy);
}

public final class com.astune.painter.api.BlendMode extends Enum<BlendMode> {
    public static final BlendMode OVERWRITE, ADD, MULTIPLY, ERASE;
    public String getTranslationKey();
    public BlendFunction getDefaultFunction();
}

public interface com.astune.painter.api.blend.BlendFunction {
    boolean apply(BlendContext context);
}

public class com.astune.painter.api.blend.BlendContext {
    public final CanvasFace face;
    public final int px, py, existingColor, newColor;
    public final BlendMode mode;
    public final ItemStack brushStack;
    public final Map<String, Integer> effectValues;      // new in 0.6.1beta
    public void setEffect(String key, int value);
    public int getEffect(String key);
}

public class com.astune.painter.api.blend.DefaultBlendFunctions {
    public static final BlendFunction OVERWRITE, ADD, MULTIPLY, ERASE;
}
```

### Client rendering

```java
public interface com.astune.painter.api.imageProvider.CanvasImageProvider {
    NativeImage createImage(CanvasFace face);
    default String name();
    default boolean canProvide(ImageProviderContext context);
}

public class com.astune.painter.api.imageProvider.ImageProviderContext {
    public final CanvasFace face;
    public final Level level;
    public final BlockPos pos;
}

public class com.astune.painter.api.imageProvider.CanvasImageProviderRegistry {
    public static synchronized void register(CanvasImageProvider provider, int priority);
    public static synchronized boolean unregister(CanvasImageProvider provider);
    public static List<CanvasImageProvider> resolveAll(ImageProviderContext context);
}

public interface com.astune.painter.api.render.CanvasPixelRenderer {
    default boolean canRender(RenderContext context);
    boolean renderFace(RenderContext context);           // true = stop further renderers
}

public class com.astune.painter.api.render.RenderContext {
    public final CanvasFace face;
    public final ResourceLocation texture;
    public final PoseStack poseStack;
    public final MultiBufferSource bufferSource;
    public final int packedLight, packedOverlay;
    public final Level level;
    public final BlockPos pos;
    public final boolean isOcclusion;
    public final double offset;
    public RenderType renderType(ResourceLocation texture);
}

public class com.astune.painter.api.render.CanvasRendererRegistry {
    public static synchronized void registerPixelRenderer(CanvasPixelRenderer renderer, int priority);
    public static CanvasPixelRenderer resolve(RenderContext context);
    public static synchronized void setDefaultRenderer(CanvasPixelRenderer renderer);
}
```

### Events and network

```java
public class com.astune.painter.event.ServerCanvasUpdateEvent extends Event {
    public ServerCanvasUpdateEvent(BlockPos, CanvasData, CanvasAction, Player);
    public BlockPos getPos();
    public CanvasData getCanvasData();
    public CanvasAction getAction();
    public Player getPlayer();
    public static class Pre extends ServerCanvasUpdateEvent { ... }
}

public final class com.astune.painter.network.CanvasAction extends Enum<CanvasAction> implements StringRepresentable {
    public static final CanvasAction ADD_CREATION, ADD, ERASE;
}

public final class com.astune.painter.network.SyncCanvasPacket extends Record implements CustomPacketPayload {
    public static final Type<SyncCanvasPacket> TYPE;
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCanvasPacket> STREAM_CODEC;
    public SyncCanvasPacket(BlockPos pos, CanvasData canvasData,
                            Optional<BlockState> mimickedState, boolean createIfAbsent);
    public BlockPos pos();
    public CanvasData canvasData();
    public Optional<BlockState> mimickedState();
    public boolean createIfAbsent();
    public static void handleClient(SyncCanvasPacket, IPayloadContext);
}
```

### Block entity integration

```java
public class com.astune.painter.block.CanvasBlockEntity extends BlockEntity {
    public CanvasBlockEntity(BlockPos, BlockState);
    public void setMimickedState(BlockState);
    public BlockState getMimickedState();
    public void checkAndRestoreIfNeeded();
    public boolean isCanvasEmpty();
}
```

### Data components and client mixin target

`com.astune.painter.registry.ModDataComponents` exposes `Supplier<DataComponentType<...>>` fields:
`CANVAS` (`CanvasData`), `BLOCK_STATE` (`BlockState`), `CURRENT_COLOR` (`Integer`), `BRUSH_SIZE` (`Double`), `FEATHER_STRENGTH` (`Float`), `BLEND_MODE` (`String`), `STEP_SIZE` (`Double`), `OPACITY` (`Float`), plus `STORED_FACE` (`CanvasFace`) and `CANVAS_TEXTURE` (`String`).

`com.astune.painter.client.PaintInputHandler` (used as a mixin target by Gyromancy):
`public static void onRenderFrame(RenderFrameEvent.Pre)`, `public static void onMouseReleased(InputEvent.MouseButton.Pre)`, `public static void onClientTick(ClientTickEvent.Post)`; private static `traceHit(Minecraft, Vec3)`, `traceNormalDir(Vec3, Vec3, Player)` and `paintPattern(Minecraft, ItemStack, BlockHitResult, IPaintProvider, double)` (verified with `javap -p`).

## Usage in Gyromancy

| Gyromancy source | Pigmentum API | Purpose |
|---|---|---|
| `item/PenItem.java` | `IPaintProvider` (`getColor`/`getPattern`/`getStep`/`shouldPaint(Player,BlockHitResult)`), `PaintPattern`, `PixelProvider`, `BlendMode.OVERWRITE`, `PaintProviders.register` | Main ink pen; writes `gyromancy:mana` and ink effect layers per brush dab (`PenItem.java:42-130`) |
| `item/DebugBrushItem.java` | `IPaintProvider`, painter `ModDataComponents.BRUSH_SIZE/OPACITY/FEATHER_STRENGTH/BLEND_MODE/STEP_SIZE/STEP_SIZE` | Single-pixel debug brush; reads brush defaults from Pigmentum data components (`DebugBrushItem.java:44-117`) |
| `item/StampItem.java` | `IPaintProvider`, `PixelProvider`, `BlendMode.ADD`, `PaintPattern`, `PixelMatrix` | Stamps a saved raster; fallback path reads a stored `CanvasFace` (`StampItem.java:359-437`) |
| `item/CompassItem.java` | `IPaintProvider` (including delegate `shouldPaint(Player,BlockHitResult)` and `transformPatternAxes`), `PaintProviders.register`, `BlendFunction` | Circle tool that feeds synthetic block hits back into Pigmentum's painting pipeline (`CompassItem.java:515-580`) |
| `mixin/PaintInputHandlerMixin.java` | `com.astune.painter.client.PaintInputHandler#onRenderFrame/traceHit/traceNormalDir`, redirect in `#paintPattern` of `IPaintProvider.transformPatternAxes` | Overrides hit tracing for the paint camera and compass (`PaintInputHandlerMixin.java:19-69`) |
| `client/ClientSetup.java` | `CanvasImageProviderRegistry.register(provider, 2)`, `CanvasRendererRegistry.registerPixelRenderer(renderer, 2)` | Registers the glyph texture generator and glyph face renderer (`ClientSetup.java:57-59`) |
| `client/glyph/GlyphImageProvider.java` | `CanvasImageProvider`, `ImageProviderContext`, `CanvasFace.getEffectLayer` | Builds a `NativeImage` from the `gyromancy:symbol_id` effect layer (`GlyphImageProvider.java:26-66`) |
| `client/glyph/GlyphRenderer.java` | `CanvasPixelRenderer`, `RenderContext` (`face`, `texture`, `poseStack`, `bufferSource`, `packedOverlay`, `offset`), `CanvasFace.cornerWithOffset/primaryFace/getEffectLayer` | Renders full-bright glyph quads on canvas faces (`GlyphRenderer.java:41-79`) |
| `symbol/ManaPixelDetector.java` | `CanvasData.faces()`, `CanvasFace.getEffectValue/pixels/primaryFace`, `IPixelMatrix.getWidth/getHeight/getPixel` | Finds `gyromancy:mana` pixels (`ManaPixelDetector.java:37-120`) |
| `symbol/MagicArrayDetector.java` | `CanvasData`, `CanvasDataHolder.painter$getCanvasData`, `ServerCanvasUpdateEvent(.Pre)`, `CanvasAction`, `SyncCanvasPacket`, `CanvasBlockEntity.getMimickedState` | Re-scans canvases after edits and pushes `SyncCanvasPacket` to tracking clients (`MagicArrayDetector.java:76-108, 266-270, 477-492`) |
| `symbol/FloodFillExtractor.java`, `GlyphMarker.java`, `GlyphStrokeValidator.java`, `SymbolMana.java` | `CanvasDataHolder`, `CanvasFace.getEffectValue/setEffectValue/getEffectLayer/pixels` | Cross-block flood fill and glyph marking on effect layers (`FloodFillExtractor.java:485-494`, `GlyphMarker.java:47-75`) |
| `util/CanvasScanUtils.java` | `IPixelMatrix` (`getWidth/getHeight/getPixel`) | Connected-component extraction over a face (`CanvasScanUtils.java:39-61`) |
| `client/PaintCameraController.java` | `PaintProviders.getProvider(ItemStack)` | Decides whether the held item is a Pigmentum paint provider (`PaintCameraController.java:202-204`) |

## Differences from 0.5.8beta

Verified against the 0.6.1beta bytecode (the older `maven.modrinth-pigmentum@0.5.8beta.md` guide documents the previous shapes):

- `CanvasFace` effect layers are `Map<String, int[]>`; `getEffectLayer` returns `int[]`. The 0.5.8 guide documented `byte[]`.
- `BlendContext` gained an `effectValues` field plus `setEffect(String,int)` / `getEffect(String)`.
- `IPaintProvider` gained `shouldPaint(Player, BlockHitResult)` and `transformPatternAxes(Player, Vec3, Direction, double, double)`; the parameterless (player-only) `shouldPaint` still exists.
- `BlendMode` gained `getDefaultFunction()`; `DefaultBlendFunctions` still provides `OVERWRITE/ADD/MULTIPLY/ERASE`.

## Unverified / Uncertain

- Priority ordering semantics of `CanvasImageProviderRegistry.register(..., int)` and `CanvasRendererRegistry.registerPixelRenderer(..., int)` (Gyromancy passes `2`; no Javadoc or upstream docs are bundled).
- `com.astune.painter.event.PaintEvents` exposes no public members through `javap`; its role could not be verified.
- The texture naming convention `{entityId}_{faceIndex}_{providerName}_{counter}` used by `GlyphRenderer` is taken from a Gyromancy comment (`GlyphRenderer.java:38-40`) and was not confirmed against `CanvasTextureManager` bytecode.
- Runtime behavior of `CanvasDataHolder` texture lifecycle methods (`painter$regenerateTextures`, `painter$releaseTextures`) is only known by signature.
- `DefaultCanvasImageProvider` / `DefaultCanvasPixelRenderer` behavior beyond their signatures was not inspected.
- The upstream JAR `GUIDE.md` is only a GuideWeaver index with no API detail, so all API statements above come from `javap`/Gyromancy usage rather than upstream prose.
