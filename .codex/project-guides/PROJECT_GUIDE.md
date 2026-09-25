# Project Guide

Generated: 2026-09-25 12:28:32Z

<!-- guideweaver:start -->

## Repo Shape

- Files indexed: 1080
- Files changed in this refresh: 1080
- Git remotes: https://github.com/Steve10086/gyromancy.git
- Manifests: build.gradle, settings.gradle
- Top-level source roots: .codex, .github, META-INF, assets, gradle, libs, net, src, tools

## File Types

- `.java`: 492
- `.json`: 167
- `.png`: 159
- `.obj`: 102
- `.md`: 74
- `.fsh`: 19
- `.nbt`: 17
- `.mcmeta`: 13
- `.vsh`: 8
- `.fx`: 5
- `.mtl`: 5
- `(none)`: 4
- `.gradle`: 2
- `.onnx`: 2
- `.properties`: 2
- `.toml`: 2
- `.txt`: 2
- `.bat`: 1
- `.fxpack`: 1
- `.jar`: 1
- `.py`: 1
- `.yml`: 1

## Changed Files

- `.codex/project-guides/GUIDE_INDEX.json`
- `.codex/project-guides/PROJECT_GUIDE.md`
- `.codex/project-guides/dependencies/com.microsoft.onnxruntime-onnxruntime@1.18.0.md`
- `.codex/project-guides/dependencies/maven.modrinth-pigmentum@0.5.8beta.md`
- `.codex/project-guides/dependencies/org.appliedenergistics.yoga-yoga@1.0.0.md`
- `.codex/project-guides/dependencies/org.junit.jupiter-junit-jupiter-engine@5.10.2.md`
- `.codex/project-guides/dependencies/org.junit.jupiter-junit-jupiter@5.10.2.md`
- `.codex/project-guides/dependencies/photon.md`
- `.codex/project-guides/index.json`
- `.gitattributes`
- `.github/workflows/build.yml`
- `.gitignore`
- `LICENSE`
- `META-INF/neoforge.mods.toml`
- `README.md`
- `TEMPLATE_LICENSE.txt`
- `assets/magic_mist_vortex_vertical.png`
- `assets/magic_mist_vortex_vertical_preview.png`
- `assets/photon/shaders/core/postfx/mask_outline.fsh`
- `assets/photon/shaders/core/postfx/mask_outline.json`
- `assets/photon/shaders/core/postfx/pixelate.fsh`
- `assets/photon/shaders/core/postfx/pixelate.json`
- `assets/wand_ui_material_sheet_imagegen.png`
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
- `operator.md`
- `photon.mixins.json`
- `photon_pixel_fx.md`
- `settings.gradle`
- `shader.md`
- `src/main/java/com/astune/gyromancy/Config.java`
- `src/main/java/com/astune/gyromancy/Gyromancy.java`
- `src/main/java/com/astune/gyromancy/GyromancyClient.java`
- `src/main/java/com/astune/gyromancy/RuneCarvingTableMenuProvider.java`
- `src/main/java/com/astune/gyromancy/api/array/ArrayActivationResult.java`
- `src/main/java/com/astune/gyromancy/api/array/ArrayObject.java`
- `src/main/java/com/astune/gyromancy/api/array/IArrayEffect.java`
- `src/main/java/com/astune/gyromancy/api/array/MagicArrayComponents.java`
- `src/main/java/com/astune/gyromancy/api/array/MagicArrayManager.java`
- `src/main/java/com/astune/gyromancy/api/array/MagicArrayState.java`
- `src/main/java/com/astune/gyromancy/api/canvas/CanvasEditorTool.java`
- `src/main/java/com/astune/gyromancy/api/canvas/CanvasPenTool.java`
- `src/main/java/com/astune/gyromancy/api/canvas/CanvasStampTool.java`
- `src/main/java/com/astune/gyromancy/api/canvas/Carvable.java`
- `src/main/java/com/astune/gyromancy/api/canvas/StampCanvasApi.java`
- `src/main/java/com/astune/gyromancy/api/canvas/StampCanvasMaterial.java`
- `src/main/java/com/astune/gyromancy/api/element/ElementConcentrations.java`
- `src/main/java/com/astune/gyromancy/api/element/ElementType.java`
- `src/main/java/com/astune/gyromancy/api/element/IElementStorage.java`
- `src/main/java/com/astune/gyromancy/api/element/ManaElements.java`
- `src/main/java/com/astune/gyromancy/api/entity/PseudoEntity.java`
- `src/main/java/com/astune/gyromancy/api/entity/PseudoEntityType.java`
- `src/main/java/com/astune/gyromancy/api/field/CircularFieldShape.java`
- `src/main/java/com/astune/gyromancy/api/field/FieldDirection.java`
- `src/main/java/com/astune/gyromancy/api/field/MagicFieldShape.java`
- `src/main/java/com/astune/gyromancy/api/field/RectangularFieldShape.java`
- `src/main/java/com/astune/gyromancy/api/field/ShapeOrientation.java`
- `src/main/java/com/astune/gyromancy/api/geometry/SurfaceFrame.java`
- `src/main/java/com/astune/gyromancy/api/ink/InkType.java`
- `src/main/java/com/astune/gyromancy/api/ink/PenProperties.java`
- `src/main/java/com/astune/gyromancy/api/symbol/ParameterRune.java`
- `src/main/java/com/astune/gyromancy/api/symbol/PixelPos.java`
- `src/main/java/com/astune/gyromancy/api/symbol/PositionedGlyph.java`
- `src/main/java/com/astune/gyromancy/api/symbol/SymbolMatch.java`
- `src/main/java/com/astune/gyromancy/api/symbol/SymbolRole.java`
- `src/main/java/com/astune/gyromancy/api/symbol/SymbolTemplate.java`
- `src/main/java/com/astune/gyromancy/array/MagicArrayDetector.java`
- `src/main/java/com/astune/gyromancy/array/compile/ApplyNode.java`
- `src/main/java/com/astune/gyromancy/array/compile/ArrayAstBuilder.java`
- `src/main/java/com/astune/gyromancy/array/compile/ArrayCompileDebug.java`
- `src/main/java/com/astune/gyromancy/array/compile/ArrayCompileFeedback.java`

## Dependency Guides

- `com.microsoft.onnxruntime-onnxruntime`@1.18.0: `.codex/project-guides/dependencies/com.microsoft.onnxruntime-onnxruntime@1.18.0.md`
- `maven.modrinth-pigmentum`@${pigmentum_version}: `.codex/project-guides/dependencies/maven.modrinth-pigmentum@pigmentum_version.md`
- `org.appliedenergistics.yoga-yoga`@1.0.0: `.codex/project-guides/dependencies/org.appliedenergistics.yoga-yoga@1.0.0.md`
- `org.commonmark-commonmark`@0.30.0: `.codex/project-guides/dependencies/org.commonmark-commonmark@0.30.0.md`
- `org.junit.jupiter-junit-jupiter-engine`@5.10.2: `.codex/project-guides/dependencies/org.junit.jupiter-junit-jupiter-engine@5.10.2.md`
- `org.junit.jupiter-junit-jupiter`@5.10.2: `.codex/project-guides/dependencies/org.junit.jupiter-junit-jupiter@5.10.2.md`

<!-- guideweaver:end -->
