# Ray Projection Effect Pipeline

This document describes the current glyph ray effect module. The only supported route is the mesh route; the old texture and instanced fallback routes have been removed.

## Runtime Flow

```text
GlyphRenderer.renderFace()
  -> read CanvasFace corners, normal, symbol layer, and glyph colors
  -> ClientRayEffects.spawnOrRefresh(...)
  -> compact non-zero symbol pixels into one extruded boundary mesh
  -> ClientRayEffects.onRenderLevelStage(AFTER_PARTICLES)
  -> render all active meshes into gyromancy:ray_composite
  -> composite gyromancy:ray_composite back onto the main framebuffer
```

`GlyphRenderer` renders the normal glyph texture first, then spawns or refreshes the transient ray effect from the same `CanvasFace`.

## Public Effect Inputs

`ClientRayEffects.spawnOrRefresh(...)` is the module entry point.

Inputs:

- `center`: world-space center of the canvas face.
- `face`: primary block face, used for effect identity and normal lookup.
- `worldRayDir`: beam direction, currently passed as the canvas normal.
- `sourceU`: canvas-local horizontal world vector, from `corner0` to `corner1`.
- `sourceV`: canvas-local vertical world vector, from `corner3` to `corner0`.
- `symbolLayer`: effect layer containing symbol pixel ids.
- `colorBySymbolValue`: maps symbol ids to ARGB colors.
- `color`: whole-effect tint.
- `lifetime`: ticks since last refresh before the effect is removed.
- `beamHeight`: world-space beam length.
- `fadeInTicks`: creation-only fade-in duration.

Defaults:

```text
DEFAULT_BEAM_HEIGHT = 1.5
DEFAULT_FADE_IN_TICKS = 0
```

Current glyph call:

```text
color = 0xA0FFFFFF
lifetime = 40
beamHeight = 0.3
fadeInTicks = 20
```

## Effect State

Effects are keyed by face center and primary face.

`age` is the animation age since creation. It is not reset by refreshes, so fade-in runs once.

`ticksSinceRefresh` is the keep-alive age. Refreshing an existing effect resets only this value. If it reaches `lifetime`, the effect is removed.

During fade-in, refresh calls are intercepted as keep-alive only. They do not replace the mesh, reset `age`, or cancel the effect.

Alpha is:

```text
fadeOut = 1 - ticksSinceRefresh / lifetime
fadeIn = fadeInTicks <= 0 ? 1 : min(1, age / fadeInTicks)
alpha = tintAlpha * fadeOut * fadeIn
```

## Mesh Generation

`compactMesh(...)` reads the symbol layer once and builds CPU-side mesh vertices.

For each non-zero, non-transparent symbol pixel:

- add a bottom cap at `beamT = 0`;
- add a top cap at `beamT = 1`;
- add side faces only where the neighbor pixel is empty or outside the layer.

Adjacent same-layer pixels therefore share no internal side faces. This is the current overdraw reduction step and keeps connected pixels visually joined.

Each mesh vertex contains:

```text
sourceU, sourceV, beamT, r, g, b, a
```

`sourceU/sourceV` are normalized canvas coordinates in `[-0.5, 0.5]`. `beamT` is `0` at the canvas base and `1` at the beam top.

## Projection

The mesh shader receives world-space basis uniforms:

- `SourceCenter`
- `SourceU`
- `SourceV`
- `BeamWorld`
- `EffectTint`

`SourceU` and `SourceV` come from the real `CanvasFace` quad, not from a guessed face basis. This avoids 90-degree rotation errors on X/Z-facing canvases.

`projectionPlane(...)` still computes a camera-dependent projection plane for sizing and clipping safety:

- source face corners stay behind the camera-facing projection plane;
- the plane is padded by `PLANE_CLEARANCE` and `PROJECTION_PADDING`;
- beam direction is clamped away from being perfectly parallel to the face normal;
- `beamHeight` scales `BeamWorld`.

The actual vertex world position is reconstructed in `ray_projection_mesh.vsh`:

```text
world = SourceCenter
      + SourceU * Position.x
      + SourceV * Position.y
      + BeamWorld * Position.z
```

## Mesh Pass

Render type:

```text
gyromancy:ray_projection_mesh
```

Resource files:

- `src/main/resources/assets/gyromancy/pinwheel/rendertypes/ray_projection_mesh.json`
- `src/main/resources/assets/gyromancy/pinwheel/shaders/program/ray_projection_mesh.json`
- `src/main/resources/assets/gyromancy/pinwheel/shaders/program/ray_projection_mesh.vsh`
- `src/main/resources/assets/gyromancy/pinwheel/shaders/program/ray_projection_mesh.fsh`

Vertex format:

```text
POSITION_COLOR
```

Blend mode:

```text
additive: GL_ONE, GL_ONE
```

The mesh pass writes to the offscreen framebuffer:

```text
gyromancy:ray_composite
```

`ray_projection_mesh.fsh` multiplies alpha by `1 - beamT`, so the top of each beam is fully transparent. RGB is premultiplied before additive output.

## Composite Pass

Resource files:

- `src/main/resources/assets/gyromancy/pinwheel/shaders/program/ray_composite.json`
- `src/main/resources/assets/gyromancy/pinwheel/shaders/program/ray_composite.fsh`

The composite pass samples `gyromancy:ray_composite` and writes back to the main framebuffer with additive blending.

It compresses local brightness:

```text
density = max(ray.r, ray.g, ray.b)
compressed = ray.rgb / (1 + density * 0.75)
```

This keeps merged same-color beams from becoming overly bright while preserving the joined shape.

## Current Files

Java:

- `src/main/java/com/astune/gyromancy/client/glyph/GlyphRenderer.java`
- `src/main/java/com/astune/gyromancy/client/effect/ClientRayEffects.java`

Tests:

- `src/test/java/com/astune/gyromancy/client/effect/ClientRayEffectsTest.java`

Shaders and render types:

- `src/main/resources/assets/gyromancy/pinwheel/rendertypes/ray_projection_mesh.json`
- `src/main/resources/assets/gyromancy/pinwheel/shaders/program/ray_projection_mesh.json`
- `src/main/resources/assets/gyromancy/pinwheel/shaders/program/ray_projection_mesh.vsh`
- `src/main/resources/assets/gyromancy/pinwheel/shaders/program/ray_projection_mesh.fsh`
- `src/main/resources/assets/gyromancy/pinwheel/shaders/program/ray_composite.json`
- `src/main/resources/assets/gyromancy/pinwheel/shaders/program/ray_composite.fsh`

## Non-Goals

Do not reintroduce these unless the mesh route is deliberately replaced:

- texture fallback route;
- instanced per-pixel route;
- fragment mask search;
- greedy rectangle route;
- compute shader route.
