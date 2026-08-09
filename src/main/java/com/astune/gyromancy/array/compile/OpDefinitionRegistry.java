package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.Gyromancy;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.ModFileScanData;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class OpDefinitionRegistry {
    private static final String OPERATOR_PACKAGE = "com.astune.gyromancy.compile.operator";
    private static final String OPERATOR_PATH = OPERATOR_PACKAGE.replace('.', '/');
    private static final Map<ResourceLocation, OpDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static boolean DISCOVERY_COMPLETE;

    static {
        discoverRegisteredOps();
        DISCOVERY_COMPLETE = true;
    }

    private OpDefinitionRegistry() {}

    public static synchronized List<OpDefinition> definitions() {
        return List.copyOf(DEFINITIONS.values());
    }

    public static synchronized void register(OpDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("definition cannot be null");
        ResourceLocation id = definition.id();
        if (id == null) throw new IllegalArgumentException("definition id cannot be null");
        OpDefinition existing = DEFINITIONS.get(id);
        if (existing != null && existing != definition) {
            throw new IllegalArgumentException("Duplicate op definition: " + id);
        }
        if (existing != null) return;
        if (!DISCOVERY_COMPLETE) {
            DEFINITIONS.put(id, definition);
            return;
        }

        // Extensions registered after discovery are explicit overrides for
        // equal-length matches, so put them before the built-in definitions.
        Map<ResourceLocation, OpDefinition> reordered = new LinkedHashMap<>();
        reordered.put(id, definition);
        reordered.putAll(DEFINITIONS);
        DEFINITIONS.clear();
        DEFINITIONS.putAll(reordered);
    }

    private static void discoverRegisteredOps() {
        for (String className : discoverOperatorClassNames()) {
            registerIfAnnotated(className);
        }
    }

    private static List<String> discoverOperatorClassNames() {
        List<String> neoForgeScanData = discoverNeoForgeScanDataClassNames();
        if (!neoForgeScanData.isEmpty()) return neoForgeScanData;

        List<String> classNames = new ArrayList<>();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) loader = OpDefinitionRegistry.class.getClassLoader();
        try {
            Enumeration<URL> resources = loader.getResources(OPERATOR_PATH);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                switch (resource.getProtocol()) {
                    case "file" -> classNames.addAll(discoverFileClasses(resource));
                    case "jar" -> classNames.addAll(discoverJarClasses(resource));
                    default -> Gyromancy.LOGGER.debug("[OpDefinitionRegistry] Skipping unsupported classpath URL: {}",
                            resource);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to discover operator definitions", e);
        }
        return classNames.stream().distinct().sorted().toList();
    }

    private static List<String> discoverNeoForgeScanDataClassNames() {
        ModList modList = ModList.get();
        if (modList == null) return List.of();
        return modList.getAllScanData().stream()
                .flatMap(data -> data.getAnnotatedBy(RegisteredOp.class, ElementType.TYPE))
                .map(ModFileScanData.AnnotationData::clazz)
                .map(type -> type.getClassName().replace('/', '.'))
                .filter(name -> name.startsWith(OPERATOR_PACKAGE + "."))
                .distinct()
                .sorted()
                .toList();
    }

    private static List<String> discoverFileClasses(URL resource) {
        try {
            Path root = Path.of(resource.toURI());
            if (!Files.isDirectory(root)) return List.of();
            try (var stream = Files.walk(root)) {
                return stream
                        .filter(path -> path.getFileName().toString().endsWith(".class"))
                        .map(root::relativize)
                        .map(OpDefinitionRegistry::classNameFromRelativePath)
                        .filter(name -> !name.contains("$"))
                        .toList();
            }
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("Failed to scan operator classes from " + resource, e);
        }
    }

    private static List<String> discoverJarClasses(URL resource) {
        try {
            JarURLConnection connection = (JarURLConnection) resource.openConnection();
            try (JarFile jar = connection.getJarFile()) {
                return discoverJarClasses(jar);
            }
        } catch (ClassCastException | IOException e) {
            String external = resource.toExternalForm();
            int separator = external.indexOf("!/");
            if (!external.startsWith("jar:file:") || separator < 0) {
                throw new IllegalStateException("Failed to scan operator classes from " + resource, e);
            }
            String jarPath = URLDecoder.decode(external.substring("jar:file:".length(), separator),
                    StandardCharsets.UTF_8);
            try (JarFile jar = new JarFile(new File(jarPath))) {
                return discoverJarClasses(jar);
            } catch (IOException fallback) {
                fallback.addSuppressed(e);
                throw new IllegalStateException("Failed to scan operator classes from " + resource, fallback);
            }
        }
    }

    private static List<String> discoverJarClasses(JarFile jar) {
        List<String> classNames = new ArrayList<>();
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || !name.startsWith(OPERATOR_PATH) || !name.endsWith(".class")) continue;
            if (name.contains("$")) continue;
            classNames.add(name.substring(0, name.length() - ".class".length()).replace('/', '.'));
        }
        return classNames;
    }

    private static String classNameFromRelativePath(Path relativePath) {
        String relative = relativePath.toString().replace(File.separatorChar, '.');
        return OPERATOR_PACKAGE + "." + relative.substring(0, relative.length() - ".class".length());
    }

    private static void registerIfAnnotated(String className) {
        try {
            Class<?> type = Class.forName(className);
            RegisteredOp registration = type.getAnnotation(RegisteredOp.class);
            if (registration == null) return;
            for (String fieldName : registration.definitions()) {
                registerDefinitionField(type, fieldName);
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to load operator class " + className, e);
        }
    }

    private static void registerDefinitionField(Class<?> type, String fieldName) {
        String className = type.getName();
        try {
            Field definition = type.getField(fieldName);
            if (!Modifier.isStatic(definition.getModifiers())) {
                throw new IllegalStateException(className + "." + fieldName + " must be static");
            }
            Object value = definition.get(null);
            if (!(value instanceof OpDefinition opDefinition)) {
                throw new IllegalStateException(className + "." + fieldName + " must be an OpDefinition");
            }
            register(opDefinition);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(className + " is @RegisteredOp but has no public "
                    + fieldName + " field", e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access " + className + "." + fieldName, e);
        }
    }
}
