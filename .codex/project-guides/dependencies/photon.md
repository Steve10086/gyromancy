# Photon2 VFX Library — Dependency Guide

## Overview

**Photon** is a Minecraft VFX (visual effect) library by KilaBash (Low-Drag-MC), inspired by Unity's particle system. It provides a high-level, declarative VFX framework including particle systems, trail effects, custom shader materials, and built-in bloom post-processing.

**License**: CC BY-NC-SA 4.0 (commercial use requires permission; user-created content/configs are excluded from restrictions)

## Coordinates

| Field | Value |
|-------|-------|
| **Group** | `com.lowdragmc.photon` |
| **Artifact** | `photon-neoforge-1.21.1` |
| **Version** | `2.1.5` |
| **Maven** | `https://maven.firstdark.dev/snapshots` |

### Dependencies
- **LDLib2**: `com.lowdragmc.ldlib2:ldlib2-neoforge-1.21.1:2.2.28` (from same Maven)
- **Yoga**: `org.appliedenergistics.yoga:yoga:1.0.0` (compileOnly)

## Gradle Setup

```groovy
repositories {
    maven {
        name = "FirstDark"
        url = "https://maven.firstdark.dev/snapshots"
        content {
            includeGroup "com.lowdragmc.ldlib2"
            includeGroup "com.lowdragmc.photon"
        }
    }
}

dependencies {
    implementation("com.lowdragmc.ldlib2:ldlib2-neoforge-${minecraft_version}:${ldlib2_version}:all") { transitive = false }
    implementation("com.lowdragmc.photon:photon-neoforge-${minecraft_version}:${photon2_version}") { transitive = false }
    compileOnly("org.appliedenergistics.yoga:yoga:1.0.0")
}
```

Note: Both LDLib2 and Photon use `transitive = false` to avoid classpath conflicts. Yoga is a layout engine needed by LDLib2's UI system.

## Core Architecture

Photon effects are **pre-authored files** (`.nbt` format), created via Photon's in-game GUI editor. The Java API is for loading those files, controlling their lifecycle, and binding them to world positions or entities.

### Package Structure

```
com.lowdragmc.photon
├── Photon.java                          — Main mod class
├── PhotonCommonListeners.java
├── PhotonCommonProxy.java
├── PhotonConfig.java
├── PhotonNetworking.java
├── PhotonRegistries.java
├── ServerCommands.java
├── client/
│   ├── fx/
│   │   ├── FXHelper.java               — Load .nbt effects
│   │   └── FX.java / FXRuntime.java     — Effect runtime model
│   ├── gameobject/                      — Game object integration
│   ├── postprocessing/
│   │   └── PhotonPostProcessing.java    — Built-in bloom
│   ├── PhotonShaders.java              — HDR particle + bloom shaders
│   ├── PhotonClientProxy.java
│   ├── PhotonParticleManager.java
│   ├── AutoCloseCleaner.java
│   └── ClientCommands.java
├── command/
├── core/mixins/
├── gui/editor/                          — In-game effect editor
└── integration/                         — Other mod integrations
```

## API Reference

### Loading Effects

**`FXHelper`** — Primary entry point for loading effect files.

```java
// Load from assets/<modid>/fx/<name>.nbt
FX fx = FXHelper.getFX(ResourceLocation.parse("gyromancy:fireball_spawn"));
```

FX files are loaded from `assets/<namespace>/fx/<path>.nbt` (compressed NBT format).

**Methods:**
- `getFX(ResourceLocation fxLocation)` — Load and cache an FX
- `getFX(ResourceLocation fxLocation, boolean useCache)` — Optionally bypass cache
- `clearCache()` — Clear the internal FX cache, returns previous count

### Effect Lifecycle

**`FX`** — Represents a loaded effect definition.
- `createRuntime()` — Create a new runtime instance of this effect
- `createRuntime(boolean modifyConfig)` — Pass `true` if you need to modify config data at runtime

**`FXRuntime`** — Manages the active lifecycle of an effect instance.
- `emmit(IEffectExecutor executor)` — Start the effect (note: the API spells it "emmit")
- `destory(boolean remove)` — Clean up the effect (note: the API spells it "destory")

### Binding Effects to World

**Built-in executors:**

```java
// Bind to a block position
new BlockEffectExecutor(fx, level, blockPos).start();

// Bind to an entity (with auto-rotation option)
new EntityEffectExecutor(fx, level, entity, AutoRotate.NONE).start();
```

### Custom IEffectExecutor

For advanced control, implement `IEffectExecutor`:

```java
public class MyEffectExecutor implements IEffectExecutor {
    private final FX fx;
    private final Level level;
    @Nullable private FXRuntime fxRuntime;

    public MyEffectExecutor(FX fx, Level level) {
        this.fx = fx;
        this.level = level;
    }

    @Override
    public Level getLevel() { return level; }

    public void emit() {
        kill(); // Clean up any existing runtime first
        fxRuntime = fx.createRuntime(); // pass true to modify config
        fxRuntime.emmit(this);
    }

    public void kill() {
        if (fxRuntime != null) {
            fxRuntime.destory(true);
            fxRuntime = null;
        }
    }

    @Override
    public void updateFXObjectTick(IFXObject object) {
        // Low-frequency logic: per-tick updates (e.g., kill condition)
    }

    @Override
    public void updateFXObjectFrame(IFXObject object, float partialTicks) {
        // High-frequency logic: per-frame updates (e.g., position, rotation, scale)
    }

    @Override
    public RandomSource getRandomSource() {
        return level.random; // default
    }
}
```

**Interface methods:**
| Method | Purpose |
|--------|---------|
| `getLevel()` | Return the world/level context |
| `updateFXObjectTick(IFXObject)` | Called per tick for low-frequency logic |
| `updateFXObjectFrame(IFXObject, float)` | Called per frame for position/rotation/scale updates |
| `getRandomSource()` | Returns a RandomSource (defaults to level.random) |

## Material System

Photon materials control how effects are rendered. Three types:

1. **Texture Material** — Standard texture-based rendering with configurable blend modes, HDR color, alpha discard, pixel-art mode
2. **Sprite Material** — Uses Minecraft's registered particle textures
3. **Custom Shader Material** — Full control with custom GLSL shaders, samplers, uniforms, and particle data access

### Common Settings (all materials)
| Setting | Default | Description |
|---------|---------|-------------|
| `blendMode` | — | Controls background blending |
| `cull` | `true` | Face culling |
| `depthTest` | `true` | Depth testing |
| `depthMask` | `false` | Depth buffer writes |

### Special Material: `block_atlas`
Accesses Minecraft's block atlas texture. Useful for particles in model mode with `useBlockUV`.

## Built-in Bloom (Post-Processing)

Photon includes a built-in bloom system via `PhotonPostProcessing`. It is a multi-pass EffectChain:
1. **Bright pass** — Extract bright areas (configurable threshold)
2. **Down-sampling** — Iterative mip chain at half resolution per level
3. **Up-sampling** — Additive blend from smallest to largest mip
4. **Final combine** — Scatter pass with configurable intensity

Configurable parameters: `bloomThreshold`, `bloomMipLevel`, `bloomIntensity`, `filterRadius`.

The bloom is activated through Photon's internal rendering pipeline — not something you call directly from application code. It replaces what would otherwise require a Veil post-processing pipeline.

## Shader System (LDLib2)

Photon's custom shader support comes from LDLib2's shader management:

```java
// LDLib2 Shader management
import com.lowdragmc.lowdraglib2.client.shader.management.Shader;
import com.lowdragmc.lowdraglib2.client.shader.management.ShaderProgram;
import com.lowdragmc.lowdraglib2.client.shader.LDLibShaders;

// Load a compute shader
Shader shader = LDLibShaders.load(Shader.ShaderType.COMPUTE, ResourceLocation.parse("gyromancy:my_shader"));
ShaderProgram program = new ShaderProgram();
program.attach(shader);
```

### Vanilla ShaderInstance Registration (PhotonShaders)

Photon registers standard `ShaderInstance` objects for core rendering passes:
- `hdr_particle` — HDR particle rendering
- `sprite_hdr_particle` — Sprite-based HDR particles
- `pixel_hdr_particle` — Pixel-mode HDR particles
- `bright_pass`, `down_sampling`, `up_sampling`, `bloom_final_scatter_pass` — Bloom pipeline

These are registered via `RegisterShadersEvent` and can be used through the vanilla `ShaderInstance` system.

## Effect Authoring

Effects are created as `.nbt` files. Two approaches:
1. **In-game editor** — Use Photon's GUI editor (accessible via `/photon` commands)
2. **Direct NBT composition** — Hand-author the `.nbt` structure following Photon's schema

The NBT format is documented in Photon's resource pack integration documentation. Effect files are placed at:
```
assets/<namespace>/fx/<effect_name>.nbt
```

## Usage Patterns for Gyromancy

### Fireball Spawn Effect
```java
// Load the effect
FX fx = FXHelper.getFX(ResourceLocation.parse("gyromancy:fireball_spawn"));
// Bind to entity
new EntityEffectExecutor(fx, level, entity, AutoRotate.NONE).start();
```

### Fireball Trail
```java
FX trailFx = FXHelper.getFX(ResourceLocation.parse("gyromancy:fireball_trail"));
var executor = new EntityEffectExecutor(trailFx, level, entity, AutoRotate.NONE);
executor.start();
```

### Fireball Explosion
```java
FX explosionFx = FXHelper.getFX(ResourceLocation.parse("gyromancy:fireball_explosion"));
new BlockEffectExecutor(explosionFx, level, pos).start();
```

### Custom Executor with Position Updates
```java
var executor = new IEffectExecutor() {
    private FXRuntime runtime;
    private final Level level;
    private final Supplier<Vec3> positionSupplier;

    @Override public Level getLevel() { return level; }

    public void emit(FX fx) {
        runtime = fx.createRuntime();
        runtime.emmit(this);
    }

    @Override
    public void updateFXObjectFrame(IFXObject object, float partialTicks) {
        Vec3 pos = positionSupplier.get();
        object.setPos(pos.x, pos.y, pos.z);
    }
};
```

## Notes
- Photon is `@OnlyIn(Dist.CLIENT)` for most classes — effects are client-side only
- Effect files use compressed NBT (`.nbt` extension), loaded via `NbtIo.readCompressed`
- `transitive = false` is required in Gradle to avoid pulling conflicting transitive dependencies
- Photon and Veil can coexist in the same project since they operate at different API levels
