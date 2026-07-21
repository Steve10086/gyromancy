# Array Script Architecture

This document defines the next core architecture for magic-array compilation.
It replaces the current "center symbol owns every effect combination" model
with a structural AST, a neutral compiled-node tree, generic runtime shells,
and reusable entity behaviors.

The goal is to stop effect complexity from growing with symbol combinations.
Adding a symbol should usually add one local compile contribution. Adding a
behavior should usually add one reusable compiled-node behavior or projectile
behavior. Only genuinely new lifecycle models should add a runtime.

## 1. Current Problem

The current array activation path is:

```text
outer circle recognized
-> find inner glyphs
-> require exactly one center symbol
-> call the center symbol's CenterEffect
-> center effect manually validates and consumes all runes
-> center effect spawns/owns the concrete entity
-> EndEffect tears down whatever CenterEffect stored in scratchData
```

This works for a few examples, but it does not scale:

- `FireSymbol`, `WaterSymbol`, and `ManaSymbol` duplicate projectile logic.
- Each center symbol must know which runes are allowed.
- Adding one new rune can require touching every existing center effect.
- Rule details such as element exchange, explosion, old fireball conversion,
  freezing, drying, and teardown are hidden inside projectile entity classes.
- The current compile model has no ownership tree; it only sees a flat list of
  runes inside the activating circle.

The next architecture must separate:

- glyph recognition and persistence;
- spatial structure;
- semantic compilation;
- runtime lifecycle;
- reusable magic behaviors;
- concrete entity body and rendering.

## 2. Pipeline

The target pipeline is:

```text
PositionedGlyphs
-> ArrayAstBuilder
-> ArrayNode AST
-> ArrayNodeCompiler
-> CompiledArrayNode tree
-> optional runtime launch envelope
-> ArrayRuntime
-> entity body + ProjectileBehavior execution
```

`ArrayRuntime` does not need to be fully decomposed before the AST and op
compiler land. The first implementation may route compiled nodes through a
`LegacyRuntimeAdapter`:

```text
CompiledArrayNode tree
-> LegacyRuntimeAdapter
-> existing entity constructors / existing center-effect helpers
```

The adapter is a runtime implementation boundary, not part of AST or compiler
semantics. `ArrayNodeCompiler` must not directly construct entities or call
symbol callbacks. Later, `ProjectileRuntime`, `FieldRuntime`, and other runtime
shells replace the adapter while consuming the same compiled node tree shape.

`MagicArrayDetector` should remain responsible for recognition lifecycle:

- scan canvas updates;
- create and store `PositionedGlyph`;
- assign parent links during circle completion events;
- build direct child lists for every circle, including nested circles;
- bind active arrays to glyph invalidation;
- sync debug data.

It should stop owning semantic effect dispatch. After structural ownership is
known, it should call the new compiler and runtime:

```java
ArrayNode ast = ArrayAstBuilder.build(circleGlyph, glyphHierarchy);
CompileResult<CompiledArrayNode> result = ArrayNodeCompiler.compile(ast);
if (result.success()) {
    RuntimeHandle handle = ArrayRuntimes.activate(result.value(), context);
    registerArrayObject(circleGlyph, ast.boundGlyphs(), handle.scratchData());
}
```

## 3. AST Layer

The AST represents spatial structure only. It does not execute magic and it does
not know about fireballs, lenses, shields, mines, or storage.

Minimum node set:

```java
sealed interface ArrayNode permits SymbolNode, SequenceNode, GroupNode, ApplyNode {}

record SymbolNode(PositionedGlyph glyph) implements ArrayNode {}

record SequenceNode(List<ArrayNode> children) implements ArrayNode {}

record GroupNode(PositionedGlyph boundary, ArrayNode body) implements ArrayNode {}

record ApplyNode(ArrayNode operator, ArrayNode target) implements ArrayNode {}
```

Meaning:

| Node | Meaning |
|------|---------|
| `SymbolNode` | One recognized glyph. |
| `SequenceNode` | Ordered children. Order is derived from spatial rules. |
| `GroupNode` | A boundary glyph, usually a circle, creates a scope. |
| `ApplyNode` | One node modifies or targets another node. Used later when adjacency or links become meaningful. |

Initial builder rules should be deliberately small:

1. The activating `OUTER_CIRCLE` becomes the root `GroupNode`.
2. Parent ownership is assigned only when a circle is recognized as complete.
3. At that moment, the circle claims every already-recognized node inside it
   that has no parent.
4. A claimed node may be a symbol node or a completed nested circle node.
5. A completed nested circle is exposed to its parent as one direct node. The
   parent compiler does not recursively inspect that nested circle's leaf
   symbols.
6. When a circle is invalidated, only the parent links of its direct children
   are cleared.
7. The direct children of a circle become that group's body.
8. Children are kept deterministic for debugging, but semantic compilation must
   not depend on child order unless a later spatial rule explicitly creates an
   `ApplyNode`.

Parent ownership belongs in detection/management, not in semantic compilation.
The compiler receives an already-owned direct-child list and rejects impossible
structures instead of guessing parentage.

Do not add language nodes such as `IfNode`, `LoopNode`, `VariableNode`, or
`FunctionNode`. Symbols such as `figure_8` can compile into duration/repeat
operations without becoming AST control flow.

## 4. Compiled Node Layer

The executable semantic IR is a tree of neutral `CompiledArrayNode`s. `verb`,
`effect`, and `op` are context roles, not separate compile-time products:

- A verb is the local compile rule that chooses one compiled node class.
- A root compiled node is used as an effect and receives a runtime lifecycle.
- A nested compiled node is used as an op by its parent.
- The same compiled node class can appear in either role.

Each circle compiles from only its own direct children:

```text
GroupNode
-> local direct symbol nodes choose primary element and attributes
-> direct nested circle nodes provide child CompiledArrayNodes
-> primary element selects current compiled node class and default behavior
-> current compiled node validates and accepts allowed child nodes
```

The minimum compiled node shape is:

```java
sealed interface CompiledArrayNode {}

record EffectNode(
        EffectKind kind,
        ElementType primaryElement,
        RuntimeKind runtime,
        ShapeSpec shape,
        TriggerSpec trigger,
        DurationSpec duration,
        EffectAttributes attributes,
        List<CompiledArrayNode> children
) implements CompiledArrayNode {}
```

`ArrayScript` may still exist as a thin runtime launch envelope, but it should
wrap a root `CompiledArrayNode` instead of replacing the node tree with a flat
list.

There is no separate `Fragment` type. What a previous design might call a
fragment is simply a nested circle's `CompiledArrayNode`.

## 5. Runtime Shells

Runtime shells own lifecycle. They should be few.

```java
enum RuntimeKind {
    INSTANT,
    FIELD,
    PROJECTILE,
    TRIGGER,
    STORAGE
}
```

| Runtime | Responsibility |
|---------|----------------|
| `INSTANT` | Run ops once at activation. |
| `FIELD` | Maintain an area and run ops when the trigger matches. |
| `PROJECTILE` | Spawn and maintain a projectile body with payload behaviors. |
| `TRIGGER` | Wait for a condition, then run ops. |
| `STORAGE` | Store energy or a `CompiledArrayNode`, then release it later. |

Runtimes are lifecycle containers, not effect implementations. For example,
"element lens" should be a `FIELD` runtime with trigger and ops, not a
`LensExecutor` class.

## 6. Local Compile Contributions

Symbols and nested circles contribute to the current compiled node in different
ways. A symbol does not execute magic by itself. It contributes primary element
or attributes. A nested circle contributes its already-compiled node.

```java
interface LocalContribution {}
```

Initial contribution and op concepts:

| Concept | Purpose |
|----|---------|
| `PrimaryElement` | Selects the current compiled node class and initializes defaults. |
| `InvertElement` | Replaces the primary element with its inverse, when one exists. |
| `MotionAttribute` | Adds direction and speed contribution. |
| `ShapeAttribute` | Sets or refines shape and scale, usually from circle area. |
| `DurationAttribute` | Sets sustain, timeout, repeat, or lifetime. |
| `TriggerAttribute` | Sets when a runtime should apply the op. |
| `AccelerationAttribute` | Sets gravity, drag, lift, or other acceleration. |
| `FilterNode` | Child-context node that restricts affected targets. |
| `DamageNode` | Child-context node or behavior that applies damage. |
| `PushNode` | Child-context node or behavior that applies impulse or repulsion. |
| `TransformBlockNode` | Child-context node that changes blocks or element concentrations. |
| `StoreNode` | Child-context node that stores element, magnitude, or node data. |
| `ReleaseNode` | Child-context node that releases stored data through another runtime. |
| `SplitNode` | Child-context node that splits a projectile or effect path. |
| `RefractNode` | Child-context node that changes motion based on surface/field orientation. |
| `BindOp` | Runtime op that binds an entity, target, or location to array state. |

A compiled node should not know which symbol produced it. It only exposes its
kind, element, attributes, accepted children, and runtime behavior.

## 7. Compiled Node Rules

Every completed circle compiles with the same local process:

1. Read only that circle's direct child list.
2. Split direct children into local symbol nodes and direct nested circle nodes.
3. Obtain each direct nested circle's compiled node.
4. Use local direct symbols to select one primary element.
5. The primary element chooses the current compiled node class and initializes its
   default runtime and behavior.
6. Other local direct symbols become attributes on the current node.
7. The current node class validates which child nodes it accepts in child/op
   context, including counts and conflicts.
8. If validation succeeds, emit one `CompiledArrayNode` for this circle.

There is no separate verb registry. A "verb" is just the selected compiled node
class for the current circle. That node becomes an effect only when activated as
the root; it becomes an op only when accepted by a parent.

Examples:

| Direct node | Local contribution |
|-------------|--------------------|
| `circle_outer` | Boundary/scope. If direct child of another circle, contributes one compiled child node. |
| `fire` | Primary element `FIRE`; chooses fire node class. |
| `water` | Primary element `WATER`; chooses water node class. |
| `mana` | Primary element `MANA`; chooses mana node class. |
| `arrow` | Motion attribute on the current node. |
| `revert` | Element inversion attribute on the current node. |
| `figure_8` | Duration/repeat attribute on the current node. |
| `star` | Usually selects or attributes a split-style node when it is primary/local. |

Nested groups produce compiled child nodes:

```text
outer_circle {
  field_center
  water
  figure_8
  small_circle {
    star
    arrow
  }
}
```

This should compile inner first as one neutral node:

```text
small_circle {
  star
  arrow
}
-> SplitNode(direction from arrow)
```

The outer circle then compiles from its direct list:

```text
field/water/figure_8 + child SplitNode
-> WaterFieldNode(duration = sustain, children = [SplitNode])
```

No dedicated lens executor is required.

## 8. Runtime And Entity Decomposition

The current fire/water/ice/dry/mana ball behavior must be decomposed, but it is
the runtime migration layer, not the detector/AST layer. Do this after detector
ownership and op compilation exist, so projectile rules compile from
`CompiledArrayNode` trees instead of from center-symbol callbacks.

Current behavior mixed into center symbols and entities:

- validate allowed runes;
- sum arrow vectors into velocity;
- derive size from circle area;
- invert fire into ice or water into dry;
- spawn a concrete entity;
- apply gravity or resistant movement;
- exchange element concentrations;
- explode or not explode;
- convert to old fireball behavior;
- bind entity lifetime to the array;
- discard bound entities on teardown.

Keep entity bodies responsible for Minecraft entity concerns:

- position, size, dimensions, growth animation, base movement tick;
- synced entity data and NBT save/load;
- rendering-specific body type and constructor compatibility;
- entity lifetime hooks such as `tick()` and `discard()`.

Move magic semantics out of entities:

- element exchange, mana drain, and element consumption;
- fire explosion, ignite, ice freeze, dry water removal, water item capture,
  brewing, and resistant movement rules;
- rune-derived velocity, scale, revert, body choice, and payload choice;
- array scratch-data writes and entity binding.

Target decomposition:

```text
PROJECTILE runtime
+ ProjectileOp(primary element)
+ element inversion attribute
+ motion attribute
+ scale attribute
+ acceleration / drag attribute
+ accepted child nodes
+ BindOp
+ projectile payload behaviors
```

### 8.1 Projectile Spec

```java
record ProjectileSpec(
        ProjectileBody body,
        MagicPayload payload,
        Vec3 velocity,
        Vec3 acceleration,
        double drag,
        float size
) {}
```

`ProjectileBody` is the physical/visual body:

```java
enum ProjectileBody {
    BALL,
    OLD_FIREBALL,
    RAY,
    ORB,
    MIST
}
```

The body owns rendering, base movement model, collision shape, and client
presentation. It should not own magic rules.

### 8.2 Magic Payload

```java
record MagicPayload(
        ElementType element,
        double magnitude,
        List<ProjectileBehavior> behaviors
) {}
```

Projectile entities call behaviors from generic hooks:

```java
interface ProjectileBehavior {
    default void onTick(ProjectileContext ctx) {}
    default void onHit(ProjectileContext ctx) {}
    default void onEnterField(ProjectileContext ctx, FieldContext field) {}
    default void onExpire(ProjectileContext ctx) {}
}
```

Initial behaviors:

| Behavior | Former owner |
|----------|--------------|
| `ExplodeOnHitBehavior` | Fireball entity. |
| `ElementExchangeOnHitBehavior` | Fireball/ice/dry/magic ball variants. |
| `IgniteOnHitBehavior` | Fire projectile rule. |
| `FreezeOnHitBehavior` | Ice projectile rule. |
| `DryWaterOnHitBehavior` | Dry projectile rule. |
| `ResistMotionBehavior` | Ice/dry/mana resistant movement. |
| `TransformProjectileBehavior` | Old fireball conversion or body swapping. |
| `SplitOnHitBehavior` | Future split projectile behavior. |
| `RefractOnEnterFieldBehavior` | Future field interaction behavior. |

`FireballEntity`, `WaterBallEntity`, `IceBallEntity`, `DryBallEntity`, and
`ManaballEntity` can remain temporarily, but magic rules should move into
payload behaviors. Long term, they should converge toward one `MagicBallEntity`
with different `ProjectileBody`, `ElementType`, and behavior lists.

Entities should not know about `ArrayObject`. Runtime context should expose
binding operations such as `bindEntity(key, entity)`, and internally store the
result in array scratch data.

### 8.3 Projectile Examples

Fire projectile:

```text
FireProjectileOp(
  element = FIRE,
  motion = arrow.front * arrow.length,
  scale = from circle area,
  acceleration = gravity if motion exists,
  children = [],
  payload behaviors = [
    ElementExchangeOnHit,
    ExplodeOnHit,
    IgniteOnHit
  ]
)
```

Reverted fire projectile:

```text
FireProjectileOp(element = inverse(FIRE), behaviors = [
  ElementExchangeOnHit,
  FreezeOnHit,
  ResistMotion
])
```

Water projectile:

```text
WaterProjectileOp(element = WATER, behaviors = [
  ElementExchangeOnHit,
  DryOrFluidInteractionOnHit
])
```

Old fireball should become a body variant:

```text
body = OLD_FIREBALL
payload = FIRE + ExplodeOnHit + ElementExchangeOnHit
```

It should not be the place where fire semantics are defined.

## 9. ArrayObject And Persistence

Active arrays should be stored by compiled ownership, not by the old
center/rune split:

- Store the root circle glyph.
- Store every glyph consumed by the compiled node tree, including nested circle
  nodes accepted as child-context nodes.
- Store `scratchData` for runtime handles, entity refs, and small primitive
  runtime state.
- Do not require `centerGlyph` or `runeGlyphs` as top-level `ArrayObject`
  fields. Primary-element and attribute symbols are local compile inputs, not
  detector contract fields.
- Store runtime refs and entity refs in `scratchData`, as today.
- Keep persisted glyphs as the source of truth across chunk load.

Do not persist `ArrayNode` as a long-term save contract yet. Rebuild AST from
persisted glyphs when needed. The AST rules will likely change while the system
is being tuned.

Do not persist parent links as the save contract. Rebuild `glyph -> parent
circle` and `circle -> direct children` indexes from persisted glyphs whenever
glyphs are loaded, added, or invalidated.

Later:

- Store `CompiledArrayNode` trees if compiled nodes need to survive reload without
  recompilation.
- Version the op format before making it persistent.

## 10. Compile Diagnostics

Compilation should return explicit diagnostics instead of silently returning an
empty scratch map.

```java
sealed interface CompileResult<T> {
    record Success<T>(T value) implements CompileResult<T> {}
    record Failure<T>(List<CompileDiagnostic> diagnostics) implements CompileResult<T> {}
}
```

Useful diagnostic codes:

| Code | Meaning |
|------|---------|
| `missing_primary_element` | Local direct symbols did not choose a primary element. |
| `ambiguous_primary_element` | More than one local primary element was present. |
| `multiple_parent_circles` | Detector found ownership that cannot be reduced to one parent circle. |
| `orphan_required_rune` | A rune required for compilation has no parent circle. |
| `multiple_centers` | Legacy diagnostic for more than one center-style primary symbol. |
| `unknown_symbol_semantics` | Recognized glyph has no compile rule. |
| `conflicting_runtime` | Two symbols requested incompatible runtimes. |
| `invalid_element_inverse` | `revert` was applied to an element with no inverse. |
| `missing_projectile_body` | Projectile runtime could not derive a body. |
| `missing_trigger` | Trigger runtime has no trigger condition. |
| `unsupported_child_node` | The current node class rejected a nested child node. |
| `too_many_child_nodes` | A child node kind exceeded the current node class limit. |

Diagnostics should be suitable for debug overlays later.

## 11. Implementation Target

Build the target architecture directly. The work divides by ownership boundary,
not by temporary feature phases.

### 11.1 Detector and hierarchy

- Store every recognized circle as a glyph, even before it activates an array.
- Parent assignment happens only when a circle is recognized as complete.
- A completed circle claims only already-recognized internal nodes that have no
  parent.
- A completed nested circle is one node in its parent's direct child list.
- Keep a circle-to-direct-children index in `MagicArrayManager`.
- When a circle is invalidated, clear the parent links of its direct children.
- When any glyph or circle changes, retry compilation for the affected direct
  parent circle. Do not recursively compile through descendants.
- Array invalidation binds all glyphs consumed by the compiled node tree,
  including nested circle nodes accepted in child/op context.
- `MagicArrayDetector` must not reject arrays for missing or multiple center
  symbols. That is a compiler diagnostic.

### 11.2 AST and op compiler

- Add `array/compile/ArrayNode` and node records.
- Add `ArrayAstBuilder` that converts the detector-owned circle tree into
  `GroupNode` and direct `SymbolNode` structures.
- Add `ArrayNodeCompiler` that compiles one group into one `CompiledArrayNode`.
- Local direct symbols choose the primary element and attributes.
- Direct nested circles contribute child `CompiledArrayNode`s.
- The selected node class validates accepted child node kinds, counts, and
  conflicts.
- If no primary element matches, report `missing_primary_element`. If multiple
  primary elements conflict, report `ambiguous_primary_element`.
- Add `CompiledArrayNode`, compile diagnostics, and the runtime activation path.
- `SymbolRole.CENTER_SYMBOL` can remain as a recognizer role, but compiler
  should treat it as a primary-element candidate. Detector should not
  special-case it.

### 11.3 Active array storage and sync

- Replace `ArrayObject(circleGlyph, centerGlyph, runeGlyphs, scratchData)` with
  `ArrayObject(rootCircleGlyph, boundGlyphs, scratchData)` or equivalent.
- `SyncArrayPacket` masks should be built from compiled `boundGlyphs`, not from
  old center/rune fields.
- Array color/debug metadata should come from the compiled root node, not from
  `centerGlyph`.
- Add parent circle identity to glyph debug sync, or add a separate AST debug
  packet, so nested ownership can be inspected.

### 11.4 Runtime and entity migration

- Add `LegacyRuntimeAdapter` first if needed, so AST and op compilation can
  run without immediately rewriting projectile entities.
- Keep the adapter thin: it consumes a root `CompiledArrayNode` and delegates to existing
  entity constructors or extracted legacy helpers.
- Add real `ArrayRuntime` shells after compiled node trees are stable.
- Add projectile payload behavior objects.
- Move projectile-specific magic rules out of `FireSymbol`, `WaterSymbol`,
  `ManaSymbol`, and concrete projectile entities.
- Make runtime context own array scratch writes and entity binding.
- Keep concrete entity classes only where body, rendering, or constructor
  compatibility still requires them.
- Remove center-symbol semantic dispatch once equivalent compiled node classes
  exist.

Recommended order:

1. Implement glyph/circle parent ownership index.
2. Build AST from direct-child circle trees.
3. Compile each group into one `CompiledArrayNode` selected by primary element.
4. Change active array binding to root circle plus consumed glyph set.
5. Add `LegacyRuntimeAdapter` for existing projectile behavior.
6. Add runtime context for entity binding and scratch data.
7. Add parent/AST debug sync.
8. Move projectile magic semantics from symbols/entities into payload behaviors.
9. Replace legacy adapter paths with real runtime shells.

## 12. Non-Goals

Do not build these into this architecture:

- a custom scripting language;
- variables;
- arbitrary conditionals;
- user-defined functions;
- bytecode;
- persisted AST compatibility;
- executor classes for every named effect.

The intended abstraction is smaller:

```text
spatial AST
-> CompiledArrayNode tree
-> few runtime shells
-> payload behaviors for entity-specific hooks
```

That is enough to make symbols extensible without making every new combination
an implementation task.
