package com.astune.gyromancy.client.render;

import com.astune.gyromancy.Gyromancy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ObjFrameModel {
    private static final Pattern FRAMETIME_PATTERN = Pattern.compile("\"frametime\"\\s*:\\s*(\\d+)");

    private final List<Frame> frames;
    private final Map<ResourceLocation, TextureAnimation> textureAnimations;

    private ObjFrameModel(List<Frame> frames, Map<ResourceLocation, TextureAnimation> textureAnimations) {
        this.frames = frames;
        this.textureAnimations = textureAnimations;
    }

    public boolean hasFrames() {
        return !frames.isEmpty();
    }

    public int frameCount() {
        return frames.size();
    }

    public void renderFrame(int frameIndex, float ageTicks, PoseStack.Pose pose, MultiBufferSource bufferSource,
                            int packedLight, ResourceLocation fallbackTexture) {
        Frame frame = frames.get(Math.floorMod(frameIndex, frames.size()));
        for (Map.Entry<ResourceLocation, List<Face>> batch : frame.batches.entrySet()) {
            ResourceLocation texture = batch.getKey() == null ? fallbackTexture : batch.getKey();
            TextureAnimation animation = textureAnimations.computeIfAbsent(texture, ObjFrameModel::loadAnimation);
            int textureFrame = animation.frame(ageTicks);
            VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(texture));
            for (Face face : batch.getValue()) {
                emitVertex(consumer, pose, face.a, animation, textureFrame, packedLight);
                emitVertex(consumer, pose, face.b, animation, textureFrame, packedLight);
                emitVertex(consumer, pose, face.c, animation, textureFrame, packedLight);
                emitVertex(consumer, pose, face.d, animation, textureFrame, packedLight);
            }
        }
    }

    public static ObjFrameModel load(ResourceLocation frameDirectory) {
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        Map<ResourceLocation, Resource> found = resources.listResources(frameDirectory.getPath(),
                location -> location.getNamespace().equals(frameDirectory.getNamespace())
                        && location.getPath().endsWith(".obj"));
        List<ResourceLocation> locations = new ArrayList<>(found.keySet());
        locations.sort(Comparator.comparing(ResourceLocation::getPath));

        List<Frame> frames = new ArrayList<>();
        Map<ResourceLocation, TextureAnimation> textureAnimations = new HashMap<>();
        for (ResourceLocation location : locations) {
            try (BufferedReader reader = found.get(location).openAsReader()) {
                frames.add(parse(reader, location, resources));
            } catch (IOException | RuntimeException ex) {
                Gyromancy.LOGGER.warn("Skipping OBJ frame {}", location, ex);
            }
        }
        if (frames.isEmpty()) {
            Gyromancy.LOGGER.info("No OBJ frames found under assets/{}/{}",
                    frameDirectory.getNamespace(), frameDirectory.getPath());
        }
        for (Frame frame : frames) {
            for (ResourceLocation texture : frame.batches.keySet()) {
                if (texture != null) {
                    textureAnimations.computeIfAbsent(texture, ObjFrameModel::loadAnimation);
                }
            }
        }
        return new ObjFrameModel(frames, textureAnimations);
    }

    private static Frame parse(BufferedReader reader, ResourceLocation objLocation,
                               ResourceManager resources) throws IOException {
        List<Vector3f> vertices = new ArrayList<>();
        List<Uv> uvs = new ArrayList<>();
        Map<String, ResourceLocation> materials = new HashMap<>();
        Map<ResourceLocation, List<Face>> batches = new LinkedHashMap<>();
        String currentMaterial = null;

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] parts = line.split("\\s+");
            if ("v".equals(parts[0]) && parts.length >= 4) {
                vertices.add(new Vector3f(parseFloat(parts[1]), parseFloat(parts[2]), parseFloat(parts[3])));
            } else if ("vt".equals(parts[0]) && parts.length >= 3) {
                uvs.add(new Uv(parseFloat(parts[1]), 1.0F - parseFloat(parts[2])));
            } else if ("mtllib".equals(parts[0]) && parts.length >= 2) {
                materials.putAll(loadMtl(resources, resolveSibling(objLocation, parts[1])));
            } else if ("usemtl".equals(parts[0]) && parts.length >= 2) {
                currentMaterial = parts[1];
            } else if ("f".equals(parts[0]) && parts.length >= 4) {
                VertexRef first = parseRef(parts[1], vertices, uvs);
                VertexRef previous = parseRef(parts[2], vertices, uvs);
                ResourceLocation texture = materials.get(currentMaterial);
                List<Face> faces = batches.computeIfAbsent(texture, ignored -> new ArrayList<>());
                for (int i = 3; i < parts.length; i += 2) {
                    VertexRef third = parseRef(parts[i], vertices, uvs);
                    VertexRef fourth = i + 1 < parts.length ? parseRef(parts[i + 1], vertices, uvs) : third;
                    Vector3f normal = normal(first.position, previous.position, third.position);
                    faces.add(new Face(
                            new ObjVertex(first.position, first.uv, normal),
                            new ObjVertex(previous.position, previous.uv, normal),
                            new ObjVertex(third.position, third.uv, normal),
                            new ObjVertex(fourth.position, fourth.uv, normal)));
                    previous = fourth;
                }
            }
        }

        return new Frame(batches);
    }

    private static Map<String, ResourceLocation> loadMtl(ResourceManager resources, ResourceLocation location) {
        Map<String, ResourceLocation> materials = new HashMap<>();
        Resource resource = resources.getResource(location).orElse(null);
        if (resource == null) return materials;

        String material = null;
        try (BufferedReader reader = resource.openAsReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\s+", 2);
                if ("newmtl".equals(parts[0]) && parts.length == 2) {
                    material = parts[1].trim();
                } else if ("map_Kd".equals(parts[0]) && parts.length == 2 && material != null) {
                    materials.put(material, textureLocation(parts[1].trim(), location, resources));
                }
            }
        } catch (IOException ex) {
            Gyromancy.LOGGER.warn("Unable to read OBJ MTL {}", location, ex);
        }
        return materials;
    }

    private static ResourceLocation resolveSibling(ResourceLocation base, String relativePath) {
        String basePath = base.getPath();
        int slash = basePath.lastIndexOf('/');
        String directory = slash >= 0 ? basePath.substring(0, slash + 1) : "";
        return ResourceLocation.fromNamespaceAndPath(base.getNamespace(), directory + cleanPath(relativePath));
    }

    private static ResourceLocation textureLocation(String mapKd, ResourceLocation mtlLocation,
                                                    ResourceManager resources) {
        String path = cleanPath(mapKd);
        ResourceLocation sibling = resolveSibling(mtlLocation, path);
        if (resources.getResource(sibling).isPresent()) {
            return sibling;
        }

        int assetsIndex = path.indexOf("assets/" + mtlLocation.getNamespace() + "/");
        if (assetsIndex >= 0) {
            path = path.substring(assetsIndex + ("assets/" + mtlLocation.getNamespace() + "/").length());
        }
        if (!path.startsWith("textures/") && !path.startsWith("models/")) {
            path = "textures/" + fileName(path);
        }
        return ResourceLocation.fromNamespaceAndPath(mtlLocation.getNamespace(), path);
    }

    private static String cleanPath(String path) {
        return path.replace('\\', '/').replace("\"", "");
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static VertexRef parseRef(String token, List<Vector3f> vertices, List<Uv> uvs) {
        String[] parts = token.split("/");
        Vector3f position = vertices.get(index(parts[0], vertices.size()));
        Uv uv = parts.length >= 2 && !parts[1].isEmpty()
                ? uvs.get(index(parts[1], uvs.size()))
                : Uv.ZERO;
        return new VertexRef(position, uv);
    }

    private static int index(String value, int size) {
        int index = Integer.parseInt(value);
        return index < 0 ? size + index : index - 1;
    }

    private static float parseFloat(String value) {
        return Float.parseFloat(value);
    }

    private static Vector3f normal(Vector3f a, Vector3f b, Vector3f c) {
        Vector3f edge1 = new Vector3f(b).sub(a);
        Vector3f edge2 = new Vector3f(c).sub(a);
        Vector3f normal = edge1.cross(edge2);
        if (normal.lengthSquared() > 0.0F) {
            normal.normalize();
        }
        return normal;
    }

    private static TextureAnimation loadAnimation(ResourceLocation texture) {
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        Resource resource = resources.getResource(texture).orElse(null);
        if (resource == null) return TextureAnimation.STATIC;

        try (InputStream stream = resource.open()) {
            var image = ImageIO.read(stream);
            if (image == null || image.getWidth() <= 0) return TextureAnimation.STATIC;

            int frames = image.getHeight() / image.getWidth();
            if (frames <= 1 || image.getHeight() % image.getWidth() != 0) return TextureAnimation.STATIC;
            int frametime = readFrametime(resources, texture);
            return new TextureAnimation(frames, frametime);
        } catch (IOException ex) {
            Gyromancy.LOGGER.warn("Unable to read animated OBJ texture {}", texture, ex);
            return TextureAnimation.STATIC;
        }
    }

    private static int readFrametime(ResourceManager resources, ResourceLocation texture) {
        ResourceLocation metadata = ResourceLocation.fromNamespaceAndPath(
                texture.getNamespace(), texture.getPath() + ".mcmeta");
        Resource resource = resources.getResource(metadata).orElse(null);
        if (resource == null) return 1;

        try (BufferedReader reader = resource.openAsReader()) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line);
            }
            Matcher matcher = FRAMETIME_PATTERN.matcher(text);
            return matcher.find() ? Math.max(1, Integer.parseInt(matcher.group(1))) : 1;
        } catch (IOException | RuntimeException ex) {
            Gyromancy.LOGGER.warn("Unable to read OBJ texture metadata {}", metadata, ex);
            return 1;
        }
    }

    private static void emitVertex(VertexConsumer consumer, PoseStack.Pose pose, ObjVertex vertex,
                                   TextureAnimation animation, int textureFrame, int packedLight) {
        consumer.addVertex(pose, vertex.position.x(), vertex.position.y(), vertex.position.z())
                .setColor(255, 255, 255, 255)
                .setUv(vertex.uv.u, animation.v(vertex.uv.v, textureFrame))
                .setOverlay(0)
                .setLight(packedLight)
                .setNormal(pose, vertex.normal.x(), vertex.normal.y(), vertex.normal.z());
    }

    private record Frame(Map<ResourceLocation, List<Face>> batches) {}

    private record Face(ObjVertex a, ObjVertex b, ObjVertex c, ObjVertex d) {}

    private record ObjVertex(Vector3f position, Uv uv, Vector3f normal) {}

    private record VertexRef(Vector3f position, Uv uv) {}

    private record Uv(float u, float v) {
        private static final Uv ZERO = new Uv(0.0F, 0.0F);
    }

    private record TextureAnimation(int frames, int frametime) {
        private static final TextureAnimation STATIC = new TextureAnimation(1, 1);

        int frame(float ageTicks) {
            return frames <= 1 ? 0 : ((int)(ageTicks / frametime)) % frames;
        }

        float v(float baseV, int frame) {
            if (frames <= 1) return baseV;
            float localV = baseV - (float)Math.floor(baseV);
            return (localV + frame) / frames;
        }
    }
}
