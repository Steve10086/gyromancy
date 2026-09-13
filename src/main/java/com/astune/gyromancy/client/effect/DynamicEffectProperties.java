package com.astune.gyromancy.client.effect;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.particle.IParticle;
import com.lowdragmc.photon.client.gameobject.particle.TrailParticle;
import com.lowdragmc.photon.client.gameobject.particle.aratrail.AraTrailParticle;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

final class DynamicEffectProperties {
    private final Vector3f offset = new Vector3f();
    private float size = 0f;
    private float alpha = -1f;
    private Vector4f color;

    private Vec3 dir = Vec3.ZERO;

    void setSize(float size) { this.size = size; }
    void setAlpha(float alpha) { this.alpha = Math.clamp(alpha, 0, 1); }
    void setColor(int argb) {
        color = new Vector4f(
                ((argb >> 16) & 0xFF) / 255f,
                ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f,
                ((argb >>> 24) & 0xFF) / 255f
        );
    }
    void setOffset(double x, double y, double z) { offset.set((float) x, (float) y, (float) z); }

    void setDir(Vec3 dir){
        this.dir = dir;
    }

    Vec3 offset(Vec3 position) {
        return position.add(offset.x, offset.y, offset.z);
    }

    void apply(FXRuntime runtime) {
        if (alpha >= 0 || color != null) {
            Vector4f emitterTint = emitterTint();
            for (var object : runtime.getObjects().values()) {
                if (object instanceof Emitter emitter) {
                    emitter.setRGBAColor(new Vector4f(emitterTint));
                }
            }
        }
        if (size > 0) {
            runtime.root.updateScale(new Vector3f(size, size, size));
        }
        if (dir.lengthSqr() > 1e-8) {
            Vector3f target = dir.normalize().toVector3f();

            Quaternionf rotation = new Quaternionf().rotationTo(
                    new Vector3f(0, 0, 1),
                    target
            );

            runtime.root.updateRotation(rotation);
        }
    }

    private void applyTrailSize(IParticle particle) {
        if (particle instanceof TrailParticle trail) {
            trail.setWidthMultiplier(() -> size);
        } else if (particle instanceof AraTrailParticle trail) {
            trail.setThicknessMultiplierSupplier(partialTick -> size);
        }
    }

    private Vector4f emitterTint() {
        float a = alpha >= 0 ? alpha : 1f;
        if (color == null) return new Vector4f(a, a, a, a);
        return new Vector4f(color.x, color.y, color.z, color.w * a);
    }
}
