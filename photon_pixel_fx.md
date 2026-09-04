# Photon FXColor Input

`gyromancy:fx_color` is a transparent `RGBA16F` target containing only selected Photon renderer batches.

## Marking a Photon renderer

Keep the renderer's **Custom Mask** enabled and give it a named **Custom Mask Group**. Set the same
group on the post-process clip. While that clip is active, the mod routes only that group into
`gyromancy:fx_color` instead of the normal scene target.

Photon keeps its own particle, mesh, trail, material, sorting, and instancing implementation. The marked batch is only redirected from Photon’s normal draw target to the isolated target. It shares the normal draw target’s depth attachment, so scene occlusion and the material’s depth state remain unchanged.

## Multiple pixel sizes / multiple groups

There is no reserved group number any more. Use one named group for each independently filtered
family, for example `wind_2px` and `tornado_6px`:

1. On each relevant renderer, enable **Custom Mask** and set its **Custom Mask Group** to that name.
2. On the matching **Post Process** clip, enable **Mask Culling** and enter exactly the same name in
   **Mask Group**.
3. Set that clip's own `PixelSize` override. If two clips use the same Render Graph with different
   values, enable the clip's **Independent** option so Photon keeps the parameter sets separate.

For a fullscreen graph that performs its own mask comparison, expose its numeric group input and
re-select the fullscreen graph on the Render Graph Pass so that the corresponding value port exists.
Add a Render Graph input variable named exactly `MaskFilter` and connect it to that group port.
`MaskFilter` is Photon's reserved parameter: the runtime resolves the clip's named group to the
actual 8-bit mask id and supplies it through this connection. Leaving an inline default of `1` on
the Pass only works accidentally for whichever named group received id 1.

Each contiguous Photon render batch gets its own `FXColor` segment and is composited immediately
before the next batch begins. Therefore unfiltered effects, a different filtered group, and even a
later pass using the same group can all render over an earlier filtered result.

`orderInLayer` remains the primary authored layer boundary. Within the same layer, whenever a
translucent `FXColor` renderer is present, particles from different render passes and Custom Mask
groups are merged and sorted back-to-front from their current world position to the camera every
frame. Adjacent particles using the same pass are coalesced again before drawing. This restores the
dynamic distance ordering that would otherwise be lost when mask groups split Photon batching.
Fullscreen executions follow the number of pass runs after sorting; alternating passes can therefore
cost more than one unsplit Photon batch, but a contiguous run is still processed together instead of
once per particle. On Iris and LATE routes, the segment write-back preserves RGBA because the
destination is a transparent premultiplied accumulator; the ordinary complete-scene route continues
preserving the world target's existing alpha.

The distance key is a particle/mesh center (or the available trail/beam anchor), matching ordinary
transparent-object sorting. Intersecting translucent surfaces still require an OIT/depth-peeling
technique for exact per-fragment ordering; ordinary alpha blending cannot resolve that case exactly.

Additive materials are preserved as additive in the isolated layer and continue to add over the
already-rendered scene at the segment boundary. Materials whose blend equation reads the destination
colour (`MAX`, `MIN`, multiply, etc.) retain their authored Renderer blend settings. Those modes
cannot be mathematically identical against a transparent texture, but forcing them into Add would
break alpha-based mesh materials.

## Graph input

In the existing Photon Render Graph, add a **Texture Input** in **Asset** mode and choose or enter:

```text
gyromancy:fx_color
```

The **Fullscreen Shader Graph** used by the Render Graph pass must also expose and *use* a
`Sampler2D` input named `FxColor`: wire its variable node to a **Sample Texture 2D** node's
`sampler` input, then wire the sample's `color` output into the existing `PixelUV` effect path.
An unconnected sampler variable is removed by GLSL compilation, so the Render Graph Pass has no
`FxColor` port. If the `FxColor` node is already wired as above, stale missing-port warnings from
other nodes do not affect this input.

Save that fullscreen graph, then re-select it on the Render Graph's **Pass** node (this rebuilds
the node's dynamic input ports). Connect the Texture Input's `out` to the regenerated `FxColor`
port. Use that input where the existing graph previously sampled `Scene(PixelUV)`. Keep the
background sample at `Scene(screenUv)` and the current final premultiplied-alpha composite unchanged:

```text
Final = PixelFX + Background * (1 - PixelFX.a)
```

`FXColor` is cleared to transparent every render frame, recreated when the draw target size changes, and released when the client level unloads.

## Preview and black output

`FXColor` is a transparent layer, not a replacement for the entire screen. Its thumbnail is black
where no renderer in the selected Custom Mask Group is drawing. To test it, make the marked Photon particle
visible and sample this input in the running world or Photon editor preview.

Do **not** wire `FXColor` straight to the Render Graph `Output`: that replaces the screen with a
mostly-transparent layer and appears black. It must only replace the prior effect-color source;
the final composition must retain the `Scene(screenUv)` background as shown above.
