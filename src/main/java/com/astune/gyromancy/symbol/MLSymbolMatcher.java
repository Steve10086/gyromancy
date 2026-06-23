package com.astune.gyromancy.symbol;

import ai.onnxruntime.*;
import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.util.GeometryUtils;
import com.astune.gyromancy.util.GeometryUtils.SkeletonGraph;
import com.astune.gyromancy.util.TemplateLoader;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.FloatBuffer;
import java.util.*;

/**
 * Two-stage symbol matcher combining geometric graph matching with ML embedding.
 *
 * <p>Stage 1 — Geometric graph gate: skeleton graph topology matching
 * (cycle count, skeleton length, node/edge geometry) via
 * {@link GeometricMatcher#geometricConfidence}. Noise gets ~0 against all templates.
 *
 * <p>Stage 2 — Contrastive embedding: 64-dim L2-normalized CNN embeddings
 * compared via L1 distance to pre-computed template embeddings.
 *
 * <p>Final score = geometricScore × mlScore. Single threshold gates both stages:
 * if geometry says 0, result is 0 regardless of ML.
 */
public final class MLSymbolMatcher {

    private static final String MODEL_PATH = "/assets/gyromancy/ml/symbol_siamese.onnx";
    private static final String EMBEDDINGS_PATH = "/assets/gyromancy/ml/template_embeddings.json";

    private final OrtEnvironment env;
    private final OrtSession session;
    private final Map<String, float[]> templateEmbeddings;
    private final Map<String, TemplateGeomDesc> templateGeom;

    private static volatile MLSymbolMatcher INSTANCE;

    // ═══════════════════ Template geometric descriptor ═══════════════════

    /** Precomputed geometric data for one template — used by Stage 1 graph gate. */
    private record TemplateGeomDesc(
            SkeletonGraph skeletonGraph,
            int trueCycleCount,
            int maxEnclosedArea,
            int totalLength
    ) {}

    // ═══════════════════ Singleton init ═══════════════════

    private MLSymbolMatcher() throws OrtException {
        env = OrtEnvironment.getEnvironment();
        session = loadSession(env);
        templateEmbeddings = loadEmbeddings();
        templateGeom = loadTemplateGeom();
    }

    public static MLSymbolMatcher getInstance() {
        if (INSTANCE == null) {
            synchronized (MLSymbolMatcher.class) {
                if (INSTANCE == null) {
                    try { INSTANCE = new MLSymbolMatcher(); }
                    catch (OrtException e) {
                        Gyromancy.LOGGER.error("[MLSymbolMatcher] Failed to init", e);
                        return null;
                    }
                }
            }
        }
        return INSTANCE;
    }

    // ═══════════════════ Public API ═══════════════════

    /**
     * Two-stage combined scores for all templates.
     *
     * <p>For each template: builds the drawn skeleton graph once, then
     * computes geometricScore × mlScore. Noise gets geometric ≈ 0 against
     * every template, so all combined scores stay near 0.
     *
     * @param image 32×32 raw binary (1=foreground, 0=background)
     * @return map of template id → combined [0,1] score
     */
    public Map<String, Float> allScores(int[][] image) {
        // ═══ Stage 1: Build drawn skeleton graph (once) ═══
        // Detect cycles on raw image — consistent with template loading
        int drawnCycles = GeometryUtils.detectTrueCycles(image);
        int drawnMaxArea = GeometryUtils.maxEnclosedArea(image);

        int[][] normDrawn = GeometryUtils.normalize(image, 32, 32);

        int[][] hiresDrawn = GeometryUtils.upscaleConnectivityPreserving(normDrawn, 128);
        int k = (128 + Math.min(normDrawn.length, normDrawn[0].length) - 1)
                / Math.min(normDrawn.length, normDrawn[0].length);
        int[][] smoothDrawn = GeometryUtils.gaussianSmoothBinary(hiresDrawn, k / 3.0);
        int[][] normSkel = GeometryUtils.thin(smoothDrawn);
        int[][] normPruned = GeometryUtils.pruneSkeleton(normSkel, 0.04);
        SkeletonGraph drawnGraph = GeometryUtils.buildSkeletonGraph(normPruned);


        // ═══ Stage 2: ML embedding ═══
        float[] emb = embed(image);
        if (emb == null) return Map.of();

        // ═══ Per-template combined scoring ═══
        Map<String, Float> scores = new LinkedHashMap<>();
        for (var entry : templateEmbeddings.entrySet()) {
            String id = entry.getKey();
            TemplateGeomDesc tgd = templateGeom.get(id);
            if (tgd == null) continue;

            // Geometric graph-matching score [0,1]
            float geo = GeometricMatcher.geometricConfidence(
                    drawnGraph, drawnCycles, drawnMaxArea,
                    tgd.skeletonGraph, tgd.trueCycleCount,
                    tgd.maxEnclosedArea, tgd.totalLength);

            // ML embedding score [0,1]
            float ml = Math.max(0f, 1f - l1Distance(emb, entry.getValue()) / 5f);

            // Binary geometric gate: if geometry rejects, score=0; else pure ML
            if (geo < 0.02f) {
                scores.put(id, 0f);
            } else {
                scores.put(id, ml);
            }
        }
        return scores;
    }

    /**
     * Best matching template with two-stage combined score above threshold.
     *
     * @return (templateId, combinedScore) or (null, 0) if none pass threshold
     */
    public Map.Entry<String, Float> bestMatch(int[][] image, float threshold) {
        Map<String, Float> scores = allScores(image);

        String bestId = null;
        float bestScore = 0f;
        for (var e : scores.entrySet()) {
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                bestId = e.getKey();
            }
        }

        if (bestId == null || bestScore < threshold) {
            return Map.entry(null, bestScore);
        }
        return Map.entry(bestId, bestScore);
    }

    /** Raw ML embedding (64-dim L2-normalized) — for debugging. */
    public float[] embed(int[][] image) {
        try {
            float[] input = new float[32 * 32];
            int idx = 0;
            for (int y = 0; y < 32; y++)
                for (int x = 0; x < 32; x++)
                    input[idx++] = (image[y][x] != 0) ? 1.0f : 0.0f;

            long[] shape = {1, 1, 32, 32};
            try (var tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape);
                 var result = session.run(Collections.singletonMap("input", tensor))) {
                float[][] output = (float[][]) result.get(0).getValue();
                return output[0];
            }
        } catch (OrtException e) {
            Gyromancy.LOGGER.error("[MLSymbolMatcher] Inference failed", e);
            return null;
        }
    }

    public Set<String> templateIds() { return templateEmbeddings.keySet(); }

    // ═══════════════════ Internals ═══════════════════

    private static float l1Distance(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += Math.abs((double) a[i] - b[i]);
        return (float) sum;
    }

    // ── ONNX model loading ──

    private OrtSession loadSession(OrtEnvironment env) throws OrtException {
        var opts = new OrtSession.SessionOptions();
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
        InputStream is = getClass().getResourceAsStream(MODEL_PATH);
        if (is == null) throw new OrtException("Model not found: " + MODEL_PATH);
        try {
            return env.createSession(is.readAllBytes(), opts);
        } catch (java.io.IOException e) {
            throw new OrtException("Failed to read model: " + e.getMessage());
        }
    }

    // ── Template embeddings JSON ──

    private Map<String, float[]> loadEmbeddings() throws OrtException {
        InputStream is = getClass().getResourceAsStream(EMBEDDINGS_PATH);
        if (is == null) throw new OrtException("Embeddings not found: " + EMBEDDINGS_PATH);
        Type type = new TypeToken<Map<String, List<Double>>>() {}.getType();
        Map<String, List<Double>> raw;
        try (var reader = new InputStreamReader(is)) {
            raw = new Gson().fromJson(reader, type);
        } catch (java.io.IOException e) {
            throw new OrtException("Failed to read embeddings: " + e.getMessage());
        }
        Map<String, float[]> embeddings = new LinkedHashMap<>();
        for (var entry : raw.entrySet()) {
            List<Double> v = entry.getValue();
            float[] arr = new float[v.size()];
            for (int i = 0; i < v.size(); i++) arr[i] = v.get(i).floatValue();
            embeddings.put(entry.getKey(), arr);
        }
        return embeddings;
    }

    // ── Template geometric descriptors ──

    /**
     * Loads and precomputes skeleton graph descriptors for each template.
     * Uses the same template IDs as the embeddings JSON.
     * Tries symbol path first, then rune path.
     */
    private Map<String, TemplateGeomDesc> loadTemplateGeom() {
        Map<String, TemplateGeomDesc> geom = new LinkedHashMap<>();
        for (String id : templateEmbeddings.keySet()) {
            TemplateGeomDesc desc = loadOneTemplateGeom(id);
            if (desc != null) {
                geom.put(id, desc);
            } else {
                Gyromancy.LOGGER.warn("[MLSymbolMatcher] No template image for: {}", id);
            }
        }
        Gyromancy.LOGGER.info("[MLSymbolMatcher] Loaded {} template geometric descriptors", geom.size());
        return geom;
    }

    private static TemplateGeomDesc loadOneTemplateGeom(String templateId) {
        // Try symbol path first, then rune path
        String path = "/assets/gyromancy/textures/symbol/" + templateId + ".png";
        int[][] raw = TemplateLoader.loadRaw(path);
        if (isEmpty(raw)) {
            path = "/assets/gyromancy/textures/rune/" + templateId + ".png";
            raw = TemplateLoader.loadRaw(path);
        }
        if (isEmpty(raw)) return null;

        // Pipeline: detect cycles on raw (consistent with SymbolTemplate),
        // then normalize → thin → prune → build graph
        int cycles = GeometryUtils.detectTrueCycles(raw);
        int maxArea = GeometryUtils.maxEnclosedArea(raw);

        int[][] norm = GeometryUtils.normalize(raw, 32, 32);

        int[][] hires = GeometryUtils.upscaleConnectivityPreserving(norm, 128);
        int k2 = (128 + Math.min(norm.length, norm[0].length) - 1)
                 / Math.min(norm.length, norm[0].length);
        int[][] smoothHires = GeometryUtils.gaussianSmoothBinary(hires, k2 / 3.0);
        int[][] skel = GeometryUtils.thin(smoothHires);
        int[][] pruned = GeometryUtils.pruneSkeleton(skel, 0.04);
        SkeletonGraph graph = GeometryUtils.buildSkeletonGraph(pruned);

        return new TemplateGeomDesc(graph, cycles, maxArea, graph.totalLength());
    }

    private static boolean isEmpty(int[][] img) {
        if (img == null || img.length == 0) return true;
        for (int[] row : img)
            for (int v : row)
                if (v != 0) return false;
        return true;
    }
}
