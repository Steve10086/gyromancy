# Photon Runtime FXColor Filter

`gyromancy:fx_color` is a dynamic `RGBA16F` texture that contains only the Photon pass currently
being filtered. It is a runtime layer, not an alternative Photon renderer: Photon still builds its
normal queues, does its own particle/mesh sorting, draws CPU and instanced effects through its own
materials, and keeps its normal depth handling. The bridge only changes the colour attachment for a
short segment, runs the configured fullscreen graph, and composites that graph's result back into
Photon's current target before the next original pass.

## One-time graph setup

In the existing fullscreen graph:

1. Keep the current pixelation logic and its exposed `PixelSize`, `MaxP`, and `MinP` inputs.
2. Add a **Texture Input** in **Asset** mode with this exact path:

   ```text
   gyromancy:fx_color
   ```

3. Connect it to the graph's exposed `FxColor` sampler and use `FxColor(PixelUV)` as the effect
   colour to pixelate.
4. In the outer **Render Graph** (not the fullscreen graph), change the existing float variable
   `MaskFilter` from `LOCAL` to `EXPOSED`, then connect its variable node to the Pass node's current
   `MaskGroup` input. The fullscreen graph can keep its existing `MaskGroup` input and should compare
   the red channel of `Mask` to `MaskGroup / 255.0` (or its equivalent). Do not hard-code `1`.
5. Keep the normal final composition shape, for example:

   ```text
   PixelFx + Scene * (1 - PixelFx.a)
   ```

   At runtime, `Scene` is transparent black for this isolated invocation, so this produces only the
   filtered effect layer. Photon then applies that layer using its own composition helper.

Save the fullscreen graph and re-select it on the Render Graph pass so Photon regenerates its input
ports. An `FxColor` node left unconnected is removed by graph compilation and cannot be supplied at
runtime.

## Assigning a filter in Photon

1. On the effect renderer, enable **Custom Mask** and give it a meaningful **Custom Mask Group**, for
   example `tornado_6px`.
2. Add the existing Photon **Post Process** clip and select the render graph from the graph setup.
3. Enable the clip's existing **Mask Culling** option and set its **Mask Group** to exactly the same
   string: `tornado_6px`.
4. Set `PixelSize`, `MaxP`, and `MinP` on that clip as usual.

There is no manually entered numeric mask id. Photon assigns its runtime 8-bit id to each named group;
the bridge replaces the clip's group name with that id in the exposed `MaskFilter` parameter and fills
the graph's mask input with the same id for the isolated layer.

Use another named group and another Post Process clip whenever a second pixel size is needed, for
example `spark_2px` and `tornado_6px`. Different groups remain separate; each is filtered and
composited at its original render-order boundary.

## Scope and compatibility

- The filter activates only for a graph that both samples `gyromancy:fx_color` and declares
  `MaskFilter`. Other Photon post effects keep their normal frame-end behaviour.
- Render passes keep Photon&apos;s native ordering. The bridge does not re-sort particles, replay masks,
  or replace the render-pass loop.
- Standard alpha and additive materials use Photon&apos;s premultiplied layer conversion, so additive
  light remains additive rather than becoming opaque coverage. A pure `MAX` renderer is restored with
  `GL_MAX` on Photon&apos;s ordinary target. Destination-dependent blends other than this fall back to
  normal, unfiltered Photon drawing rather than silently changing their blend semantics.
- The bridge has no dependency on `com.lowdragmc.photon.gui.editor` or `SceneView`. It uses the same
  Post Process runtime API that the clip already needs, so removing Photon editor pages does not add a
  new runtime dependency.

The legacy full-pipeline FXColor implementation is retained separately in Git stash as
`legacy-photon-fxcolor-pipeline-before-native-filter` until this implementation has been verified in
both the Photon editor and the world.
