# Project Guide

Generated: 2026-07-04 06:20:38Z

<!-- guideweaver:start -->

## Repo Shape

- Files indexed: 264
- Files changed in this refresh: 264
- Git remotes: none detected
- Manifests: build.gradle, settings.gradle
- Top-level source roots: .github, gradle, libs, net, src, tools, train

## File Types

- `.png`: 128
- `.java`: 86
- `.json`: 17
- `.fsh`: 6
- `.md`: 5
- `.onnx`: 4
- `(none)`: 3
- `.txt`: 3
- `.gradle`: 2
- `.properties`: 2
- `.py`: 2
- `.bat`: 1
- `.ckpt`: 1
- `.jar`: 1
- `.toml`: 1
- `.vsh`: 1
- `.yml`: 1

## Changed Files

- `.gitattributes`
- `.github/workflows/build.yml`
- `.gitignore`
- `README.md`
- `TEMPLATE_LICENSE.txt`
- `build.gradle`
- `gradle.properties`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradlew`
- `gradlew.bat`
- `item.md`
- `libs/README.txt`
- `matcher.md`
- `net/minecraft/client/Camera.java`
- `net/minecraft/client/renderer/entity/EntityRenderer.java`
- `settings.gradle`
- `src/main/java/com/astune/gyromancy/Config.java`
- `src/main/java/com/astune/gyromancy/Gyromancy.java`
- `src/main/java/com/astune/gyromancy/GyromancyClient.java`
- `src/main/java/com/astune/gyromancy/api/array/ArrayActivationResult.java`
- `src/main/java/com/astune/gyromancy/api/array/ArrayObject.java`
- `src/main/java/com/astune/gyromancy/api/array/IArrayEffect.java`
- `src/main/java/com/astune/gyromancy/api/array/MagicArrayComponents.java`
- `src/main/java/com/astune/gyromancy/api/array/MagicArrayManager.java`
- `src/main/java/com/astune/gyromancy/api/array/MagicArrayState.java`
- `src/main/java/com/astune/gyromancy/api/element/ElementConcentrations.java`
- `src/main/java/com/astune/gyromancy/api/element/ElementType.java`
- `src/main/java/com/astune/gyromancy/api/element/IElementStorage.java`
- `src/main/java/com/astune/gyromancy/api/entity/PseudoEntity.java`
- `src/main/java/com/astune/gyromancy/api/entity/PseudoEntityType.java`
- `src/main/java/com/astune/gyromancy/api/ink/InkType.java`
- `src/main/java/com/astune/gyromancy/api/ink/PenProperties.java`
- `src/main/java/com/astune/gyromancy/api/symbol/ParameterRune.java`
- `src/main/java/com/astune/gyromancy/api/symbol/PixelPos.java`
- `src/main/java/com/astune/gyromancy/api/symbol/PositionedGlyph.java`
- `src/main/java/com/astune/gyromancy/api/symbol/SymbolMatch.java`
- `src/main/java/com/astune/gyromancy/api/symbol/SymbolRole.java`
- `src/main/java/com/astune/gyromancy/api/symbol/SymbolTemplate.java`
- `src/main/java/com/astune/gyromancy/array/MagicArrayDetector.java`
- `src/main/java/com/astune/gyromancy/client/ClientSetup.java`
- `src/main/java/com/astune/gyromancy/client/ElementDebugRenderer.java`
- `src/main/java/com/astune/gyromancy/client/effect/ClientRayEffects.java`
- `src/main/java/com/astune/gyromancy/client/glyph/GlyphImageProvider.java`
- `src/main/java/com/astune/gyromancy/client/glyph/GlyphRenderer.java`
- `src/main/java/com/astune/gyromancy/command/DebugCommands.java`
- `src/main/java/com/astune/gyromancy/element/ElementBiomeProvider.java`
- `src/main/java/com/astune/gyromancy/element/ElementChunkEventHandler.java`
- `src/main/java/com/astune/gyromancy/element/ElementChunkProcessor.java`
- `src/main/java/com/astune/gyromancy/element/ElementRegressionLogic.java`
- `src/main/java/com/astune/gyromancy/element/ElementStorageManager.java`
- `src/main/java/com/astune/gyromancy/element/ElementTickProcessor.java`
- `src/main/java/com/astune/gyromancy/element/IElementChunkAccessor.java`
- `src/main/java/com/astune/gyromancy/element/IElementTickProcessor.java`
- `src/main/java/com/astune/gyromancy/element/event/ElementActivatedEvent.java`
- `src/main/java/com/astune/gyromancy/element/event/ElementChangeEvent.java`
- `src/main/java/com/astune/gyromancy/element/event/ElementCleanedUpEvent.java`
- `src/main/java/com/astune/gyromancy/element/event/ElementEventBus.java`
- `src/main/java/com/astune/gyromancy/element/event/ElementEventSubscription.java`
- `src/main/java/com/astune/gyromancy/element/event/ElementThresholdEvent.java`
- `src/main/java/com/astune/gyromancy/element/event/ThresholdDirection.java`
- `src/main/java/com/astune/gyromancy/ink/InkRegistry.java`
- `src/main/java/com/astune/gyromancy/item/DebugBrushItem.java`
- `src/main/java/com/astune/gyromancy/item/InkBottleItem.java`
- `src/main/java/com/astune/gyromancy/item/PenItem.java`
- `src/main/java/com/astune/gyromancy/mixin/LevelChunkMixin.java`
- `src/main/java/com/astune/gyromancy/network/ModNetwork.java`
- `src/main/java/com/astune/gyromancy/network/SyncDebugElementPacket.java`
- `src/main/java/com/astune/gyromancy/network/SyncGlyphPacket.java`
- `src/main/java/com/astune/gyromancy/registry/GyromancyRegistries.java`
- `src/main/java/com/astune/gyromancy/registry/ModAttachments.java`
- `src/main/java/com/astune/gyromancy/registry/ModBlockEntities.java`
- `src/main/java/com/astune/gyromancy/registry/ModBlocks.java`
- `src/main/java/com/astune/gyromancy/registry/ModCreativeTabs.java`
- `src/main/java/com/astune/gyromancy/registry/ModDataComponents.java`
- `src/main/java/com/astune/gyromancy/registry/ModEntities.java`
- `src/main/java/com/astune/gyromancy/registry/ModItems.java`
- `src/main/java/com/astune/gyromancy/symbol/FloodFillExtractor.java`
- `src/main/java/com/astune/gyromancy/symbol/FloodFillScheduler.java`
- `src/main/java/com/astune/gyromancy/symbol/GeometricMatcher.java`

## Dependency Guides

- `com.microsoft.onnxruntime-onnxruntime`@1.18.0: `.codex/project-guides/dependencies/com.microsoft.onnxruntime-onnxruntime@1.18.0.md`
- `maven.modrinth-pigmentum`@0.5.8beta: `.codex/project-guides/dependencies/maven.modrinth-pigmentum@0.5.8beta.md`
- `org.junit.jupiter-junit-jupiter-engine`@5.10.2: `.codex/project-guides/dependencies/org.junit.jupiter-junit-jupiter-engine@5.10.2.md`
- `org.junit.jupiter-junit-jupiter`@5.10.2: `.codex/project-guides/dependencies/org.junit.jupiter-junit-jupiter@5.10.2.md`

<!-- guideweaver:end -->
