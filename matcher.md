# Skeleton Matcher

This document covers the matching internals that `symbol.md` intentionally
leaves out. It starts after `SymbolRecognizer` has converted an
`ExtractedGlyph` into a raw binary matrix and ends when `SkeletonMatcher`
returns `Match` records.

## Boundary With `symbol.md`

`symbol.md` owns:

- canvas update scanning;
- flood-fill extraction;
- `SymbolRecognizer.recognize(glyph)` invocation;
- glyph marking, persistence, sync, and debug rendering.

`matcher.md` owns:

- template registration into `SkeletonMatcher`;
- raw matrix preprocessing into skeleton graph stats;
- hard layer filtering;
- graph-edit edge matching;
- soft threshold scoring;
- matched-pair rotation output.

## Entry Points

`SymbolRegistry` pre-registers each template PNG:

```java
SkeletonMatcher.getInstance().registerTemplate(id, resourcePath, thresholds);
```

Registration loads the PNG through `TemplateLoader.load()`, computes
`SkeletonStats`, and stores a `TemplateEntry(stats, thresholds)` in insertion
order. NeoForge registry registration later calls `SkeletonMatcher.init()`.

Runtime recognition calls:

```java
SkeletonMatcher.getInstance().recognize(rawMatrix);
```

If the matcher is not initialized, or the target matrix cannot produce
`SkeletonStats`, recognition returns an empty list.

## Skeleton Stats

`computeStats(raw)` turns a binary image into the graph data used by every
matching stage:

1. Fill small holes.
2. Compute upscale factor `K` from the minimum dimension.
3. Upscale to at least `128px` using connectivity-preserving upscale.
4. Gaussian smooth.
5. Skeletonize.
6. Crop to foreground.
7. Extract skeleton nodes and trace skeleton edges.
8. Merge zero-length edges.
9. Prune short branches with `minBranch = 2 * K`.
10. Snapshot the pre-split graph.
11. Split edges at support points with parent-edge tracking.
12. Count open lines, closed loops, outer open lines, and inner open lines.

The pre-split graph is the minimum topology used for graph edit matching. The
post-split graph is used to compare the shape carried by each surviving
pre-split edge.

## Hard Layer Filter

Before expensive matching, each template must match the target's topology
counts exactly:

```java
target.openLines == tpl.openLines
target.closedLoops == tpl.closedLoops
target.outerOpenLines == tpl.outerOpenLines
target.innerOpenLines == tpl.innerOpenLines
```

Templates that fail this layer are ignored. No confidence score is produced for
them.

## Graph Edit Match

`minGraphEditMatch(tpl, target)` compares a template and target that passed the
hard layer.

The method first converts both pre-split graphs into mutable node/edge lists.
Then it repeatedly merges the cheapest cycle edge until the live graph
structures match or the edit loop can no longer improve them.

Merge cost favors edges that look like artificial cycle splits:

- the cheapest merge has the straightest continuation at both junction ends;
- shorter cycle edges are cheaper than long structural edges;
- merged edges are tracked in `tplEdited` / `tgtEdited`.

After editing, the matcher builds live edge lists from unedited edges and runs a
rectangular Hungarian assignment. Dummy rows/columns have high cost. Real pair
cost is:

```java
1.0 - compareEdgeChains(tpl, target, tplEdge, targetEdge).combined()
```

## Edge Pair Scoring

For each matched pre-split edge pair, `compareEdgeChains()` compares the
post-split edge chains that came from each parent edge.

It computes:

- `segment`: ratio of support-point segment counts;
- `length`: ratio of total post-split path lengths;
- `turning`: similarity of turning-angle profiles;
- `endpointAngle`: best direct/flipped endpoint angle signature similarity.

The current edge-level combined score is deliberately small:

```java
0.5 * length + 0.5 * endpointAngle
```

`segment` and `turning` are still returned because the final soft thresholds
check them independently.

## Final Score And Soft Thresholds

Valid matched pairs are averaged into a `MatchScore`:

```java
combined =
    0.18 * segment
  + 0.18 * length
  + 0.26 * turning
  + 0.20 * endpointAngle
  + 0.18 * edit
```

The score is accepted only if it passes the template's `SoftThresholds`:

```java
segment >= threshold.segment
length >= threshold.length
turning >= threshold.turning
endpointAngle >= threshold.endpointAngle
edit >= threshold.edit
```

Accepted matches are sorted by descending confidence.

## Rotation From Matched Pairs

The matcher does not run a separate angle search. Rotation is derived from the
same Hungarian edge pairs that produced the match.

For every valid pair:

1. Compare direct and flipped endpoint correspondence using the same endpoint
   angle score logic.
2. Choose the better correspondence.
3. Compute the signed angle delta from the template edge direction to the
   target edge direction.
4. Weight the delta by template edge path length.
5. Accumulate deltas with a circular mean.

The final `MatchScore.rotationDegrees` is normalized to `[0, 360)` and copied
into `Match.rotationDegrees`.

`SymbolRecognizer` later turns that rotation into the glyph pose fields:

- `rotationDegrees`;
- world-space `front`;
- `length`;
- `width`.

## Output

Each accepted template becomes:

```java
new SkeletonMatcher.Match(templateId, confidence, rotationDegrees)
```

`SymbolRecognizer` resolves `templateId` back to a `SymbolTemplate`, creates a
`SymbolMatch`, and hands control back to the flow documented in `symbol.md`.

## Debug And Tests

`SymbolMatcherTest` exports per-image hard/soft match diagnostics to:

```text
build/skeleton_viz/hard_match/
```

`SkeletonMatcherRotationTest` checks that rotation is produced from matched
edge pairs for a simple asymmetric skeleton.
