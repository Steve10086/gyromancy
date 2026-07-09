# Project Guide

Generated: 2026-07-09 07:09:38Z

<!-- guideweaver:start -->

## Repo Shape

- Files indexed: 396
- Files changed in this refresh: 396
- Git remotes: none detected
- Manifests: build.gradle, settings.gradle
- Top-level source roots: .codex, .github, assets, gradle, libs, net, src, tools, train

## File Types

- `.png`: 136
- `.obj`: 97
- `.java`: 95
- `.json`: 21
- `.md`: 11
- `.fsh`: 7
- `.mcmeta`: 5
- `.onnx`: 4
- `(none)`: 3
- `.txt`: 3
- `.gradle`: 2
- `.properties`: 2
- `.py`: 2
- `.vsh`: 2
- `.bat`: 1
- `.ckpt`: 1
- `.jar`: 1
- `.mtl`: 1
- `.toml`: 1
- `.yml`: 1

## Changed Files

- `.codex/project-guides/GUIDE_INDEX.json`
- `.codex/project-guides/PROJECT_GUIDE.md`
- `.codex/project-guides/dependencies/com.microsoft.onnxruntime-onnxruntime@1.18.0.md`
- `.codex/project-guides/dependencies/maven.modrinth-pigmentum@0.5.8beta.md`
- `.codex/project-guides/dependencies/org.junit.jupiter-junit-jupiter-engine@5.10.2.md`
- `.codex/project-guides/dependencies/org.junit.jupiter-junit-jupiter@5.10.2.md`
- `.codex/project-guides/index.json`
- `.gitattributes`
- `.github/workflows/build.yml`
- `.gitignore`
- `README.md`
- `TEMPLATE_LICENSE.txt`
- `assets/magic_mist_vortex_vertical.png`
- `assets/magic_mist_vortex_vertical_preview.png`
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
- `shader.md`
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
- `src/main/java/com/astune/gyromancy/client/effect/FireballSpawnEffect.java`
- `src/main/java/com/astune/gyromancy/client/effect/FlipbookEffect.java`
- `src/main/java/com/astune/gyromancy/client/entity/FireballRenderer.java`
- `src/main/java/com/astune/gyromancy/client/glyph/GlyphImageProvider.java`
- `src/main/java/com/astune/gyromancy/client/glyph/GlyphRenderer.java`
- `src/main/java/com/astune/gyromancy/client/render/FrameAnimation.java`
- `src/main/java/com/astune/gyromancy/client/render/ObjFrameModel.java`
- `src/main/java/com/astune/gyromancy/client/render/RenderAnimation.java`
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
- `src/main/java/com/astune/gyromancy/entity/FireballEntity.java`
- `src/main/java/com/astune/gyromancy/ink/InkRegistry.java`
- `src/main/java/com/astune/gyromancy/item/DebugBrushItem.java`

## Dependency Guides

- `com.microsoft.onnxruntime-onnxruntime`@1.18.0: `.codex/project-guides/dependencies/com.microsoft.onnxruntime-onnxruntime@1.18.0.md`
- `maven.modrinth-pigmentum`@0.5.8beta: `.codex/project-guides/dependencies/maven.modrinth-pigmentum@0.5.8beta.md`
- `org.appliedenergistics.yoga-yoga`@1.0.0: `.codex/project-guides/dependencies/org.appliedenergistics.yoga-yoga@1.0.0.md`
- `org.junit.jupiter-junit-jupiter-engine`@5.10.2: `.codex/project-guides/dependencies/org.junit.jupiter-junit-jupiter-engine@5.10.2.md`
- `org.junit.jupiter-junit-jupiter`@5.10.2: `.codex/project-guides/dependencies/org.junit.jupiter-junit-jupiter@5.10.2.md`
- `photon`: `.codex/project-guides/dependencies/photon.md`

<!-- guideweaver:end -->
