package com.astune.gyromancy.client.effect;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.UIResourceMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.particle.IParticle;
import com.lowdragmc.photon.client.gameobject.particle.TrailParticle;
import com.lowdragmc.photon.client.gameobject.particle.aratrail.AraTrailParticle;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.IdentityHashMap;
import java.util.Map;

final class DynamicEffectProperties {
    private final Vector3f offset = new Vector3f();
    private float size = 0f;
    private float alpha = -1f;
    private final Map<TextureMaterial, Vector4f> hdrColors = new IdentityHashMap<>();

    void setSize(float size) { this.size = size; }
    void setAlpha(float alpha) { this.alpha = Math.clamp(alpha, 0, 1); }
    void setOffset(double x, double y, double z) { offset.set((float) x, (float) y, (float) z); }

    Vec3 offset(Vec3 position) {
        return position.add(offset.x, offset.y, offset.z);
    }

    void apply(FXRuntime runtime) {
        if (alpha >= 0) {
            for (var object : runtime.getObjects().values()) {
                if (object instanceof Emitter emitter) {
                    emitter.setRGBAColor(new Vector4f(alpha, alpha, alpha, alpha));
                }
                if (object instanceof ParticleEmitter emitter) {
                    for (var setting : emitter.config.renderer.getMaterials()) {
                        var material = setting.getMaterial();
                        if (material instanceof UIResourceMaterial resource && resource.getInternalTexture() instanceof TextureMaterial texture) {
                            var copy = (TextureMaterial) texture.copy();
                            copy.setHdr(new Vector4f(texture.getHdr()));
                            copy.setHdrMode(texture.getHdrMode());
                            setting.setMaterial(copy);
                            material = copy;
                        }
                        if (material instanceof TextureMaterial texture) {
                            Vector4f base = hdrColors.computeIfAbsent(texture, key -> new Vector4f(key.getHdr()));
                            texture.setHdr(new Vector4f(base.x, base.y, base.z, base.w * alpha));
                        }
                    }
                }
            }
        }
        if (size > 0) {
            runtime.root.updateScale(new Vector3f(size, size, size));
            for (var object : runtime.getObjects().values()) {
                if (object instanceof ParticleEmitter emitter) {
                    emitter.getParticles().values().forEach(queue -> queue.forEach(this::applyTrailSize));
                    emitter.waitToAdded.forEach(this::applyTrailSize);
                }
            }
        }
    }

    private void applyTrailSize(IParticle particle) {
        if (particle instanceof TrailParticle trail) {
            trail.setWidthMultiplier(() -> size);
        } else if (particle instanceof AraTrailParticle trail) {
            trail.setThicknessMultiplierSupplier(partialTick -> size);
        }
    }
}
