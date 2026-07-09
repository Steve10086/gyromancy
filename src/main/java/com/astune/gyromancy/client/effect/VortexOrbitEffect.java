package com.astune.gyromancy.client.effect;

import com.lowdragmc.photon.client.fx.*;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Sphere;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.function.Supplier;

/**
 * Particles: spherical spawn → accelerating spiral converge → circular orbit.
 * When target moves too far, each particle lerps back to the nearest orbit point.
 */
@OnlyIn(Dist.CLIENT)
public class VortexOrbitEffect {
    private static final ResourceLocation FX_ID =
            ResourceLocation.fromNamespaceAndPath("gyromancy", "vortex_orb");

    private final List<ParticleRunner> particles = new ArrayList<>();
    private final Level level;
    private final Supplier<Vec3> target;
    private final int particleCount;
    private final float spawnRadius;
    private final int convergeTicks;
    private final Vec3 orbitAxis;
    private final float orbitRadius;
    private final float centerLerp;
    private FX fx;

    public VortexOrbitEffect(Level level, Supplier<Vec3> target,
                             int particleCount, float spawnRadius, int convergeTicks,
                             Vec3 orbitAxis, float orbitRadius) {
        this(level, target, particleCount, spawnRadius, convergeTicks, orbitAxis, orbitRadius, 0.04f);
    }

    public VortexOrbitEffect(Level level, Supplier<Vec3> target,
                             int particleCount, float spawnRadius, int convergeTicks,
                             Vec3 orbitAxis, float orbitRadius, float centerLerp) {
        this.level = level;
        this.target = target;
        this.particleCount = particleCount;
        this.spawnRadius = spawnRadius;
        this.convergeTicks = convergeTicks;
        this.orbitAxis = orbitAxis.normalize();
        this.orbitRadius = orbitRadius;
        this.centerLerp = centerLerp;
    }

    public void emit() {
        FX fx = getOrCreateFx();
        if (fx == null) return;
        Random rand = new Random();
        for (int i = 0; i < particleCount; i++) {
            var def = OrbitDef.random(spawnRadius, convergeTicks, orbitAxis, orbitRadius, rand);
            var p = new ParticleRunner(fx, level, target, def, centerLerp);
            p.start();
            particles.add(p);
        }
    }

    public void kill() {
        for (var p : particles) p.kill();
        particles.clear();
    }

    private FX getOrCreateFx() {
        if (fx != null) return fx;
        fx = FXHelper.getFX(FX_ID);
        if (fx == null) fx = buildFallbackFx();
        return fx;
    }

    private static FX buildFallbackFx() {
        FX fx = new FX();
        ParticleEmitter e = new ParticleEmitter();
        e.setName("vortex_orb");
        ParticleConfig c = e.config;
        c.setDuration(Integer.MAX_VALUE);
        c.setLooping(false);
        c.setMaxParticles(1);
        c.setStartLifetime(NumberFunction.constant(Integer.MAX_VALUE));
        c.setStartSpeed(NumberFunction.constant(0f));
        c.setStartSize(new NumberFunction3(0.15f, 0.15f, 0.15f));
        c.shape.setShape(new Sphere());
        c.setSimulationSpace(ParticleConfig.Space.Local);
        c.emission.setEmissionRate(NumberFunction.constant(0f));
        EmissionSetting.Burst burst = new EmissionSetting.Burst();
        burst.time = 0;
        burst.setCount(NumberFunction.constant(1));
        burst.cycles = 1;
        c.emission.getBursts().add(burst);
        fx.getFxData().objects().add(e);
        return fx;
    }

    // ═══ Orbit def ═══
    private static final class OrbitDef {
        final Vector3f axis, u, v;
        final float orbitRadius, orbitSpeed;
        final int convergeTicks;
        // spiral params
        final Vec3 radialDir;   // unit direction in plane (orbit-plane projection of spawn)
        final float startR;     // initial plane radius
        final float startH;     // initial height above plane
        final float[] angles;   // precomputed cumulative spiral radians [0..1]
        final float orbitPhase; // starting orbital phase at spiral end

        OrbitDef(Vector3f axis, Vector3f u, Vector3f v,
                 float orbitRadius, float orbitSpeed, int convergeTicks,
                 Vec3 radialDir, float startR, float startH,
                 float[] angles, float orbitPhase) {
            this.axis = axis; this.u = u; this.v = v;
            this.orbitRadius = orbitRadius; this.orbitSpeed = orbitSpeed;
            this.convergeTicks = convergeTicks;
            this.radialDir = radialDir; this.startR = startR; this.startH = startH;
            this.angles = angles; this.orbitPhase = orbitPhase;
        }

        static OrbitDef random(float spawnR, int convergeTicks,
                                Vec3 sharedAxis, float orbitR, Random rng) {
            Vector3f axis = deviate(toV3f(sharedAxis), (float)Math.toRadians(15), rng);
            Vector3f u = perp(axis);
            Vector3f v = axis.cross(u, new Vector3f()).normalize();

            Vec3 spawnDir = randomUnit(rng);
            float h = (float)(spawnDir.x*axis.x() + spawnDir.y*axis.y() + spawnDir.z*axis.z());
            float startH = h * spawnR;
            float px = (float)spawnDir.x - h*axis.x();
            float py = (float)spawnDir.y - h*axis.y();
            float pz = (float)spawnDir.z - h*axis.z();
            float len = (float) Math.sqrt(px*px+py*py+pz*pz);
            Vec3 radialDir;
            float startR;
            if (len < 1e-6f) {
                radialDir = toMC(u);
                startR = 0f;
            } else {
                radialDir = new Vec3(px/len, py/len, pz/len);
                startR = len * spawnR;
            }

            float period = 40 + rng.nextFloat() * 80;
            float speed = (float)(Math.PI*2 / period);
            float[] angles = buildAngles(startR, orbitR, speed, convergeTicks);
            float total = angles[ANGLE_SAMPLES];
            Vec3 endDir = rotate(radialDir, total, axis);
            float orbPhase = (float) Math.atan2(
                    endDir.x*v.x() + endDir.y*v.y() + endDir.z*v.z(),
                    endDir.x*u.x() + endDir.y*u.y() + endDir.z*u.z());
            return new OrbitDef(axis, u, v, orbitR, speed, convergeTicks,
                    radialDir, startR, startH, angles, orbPhase);
        }

        Vec3 orbitPos(float phase, Vec3 center) {
            double cx=Math.cos(phase), sx=Math.sin(phase);
            return center.add(u.x()*orbitRadius*cx + v.x()*orbitRadius*sx,
                    u.y()*orbitRadius*cx + v.y()*orbitRadius*sx,
                    u.z()*orbitRadius*cx + v.z()*orbitRadius*sx);
        }

        Vec3 orbitVel(float phase, Vec3 center) {
            double cx=Math.cos(phase), sx=Math.sin(phase);
            return new Vec3((-u.x()*sx+v.x()*cx)*orbitRadius*orbitSpeed,
                    (-u.y()*sx+v.y()*cx)*orbitRadius*orbitSpeed,
                    (-u.z()*sx+v.z()*cx)*orbitRadius*orbitSpeed);
        }

        /** Nearest orbit point. */
        Vec3 nearest(Vec3 fromWorld, Vec3 center) {
            return nearest(fromWorld, center, null);
        }

        /** Nearest orbit point + fills outPhase[0] with orbital phase (if non-null). */
        Vec3 nearest(Vec3 fromWorld, Vec3 center, float[] outPhase) {
            Vec3 rel = fromWorld.subtract(center);
            float ha = (float)(rel.x*axis.x()+rel.y*axis.y()+rel.z*axis.z());
            float px = (float)rel.x - ha*axis.x();
            float py = (float)rel.y - ha*axis.y();
            float pz = (float)rel.z - ha*axis.z();
            double len = Math.sqrt(px*px+py*py+pz*pz);
            if (len < 1e-8) {
                if (outPhase != null) outPhase[0] = 0;
                return orbitPos(0, center);
            }
            float phase = (float) Math.atan2(px*v.x()+py*v.y()+pz*v.z(), px*u.x()+py*u.y()+pz*u.z());
            if (outPhase != null) outPhase[0] = phase;
            return orbitPos(phase, center);
        }

        // ── spiral helpers ──
        private static final int ANGLE_SAMPLES = 200;
        private static float[] buildAngles(float startR, float orbitR, float speed, int ticks) {
            float[] a = new float[ANGLE_SAMPLES+1];
            float L = speed * orbitR * orbitR;
            for (int i=1;i<=ANGLE_SAMPLES;i++) {
                float s = (float)i/ANGLE_SAMPLES;
                float r = lerp(startR, orbitR, ss(s));
                if (r<1e-4f) r=1e-4f;
                a[i] = a[i-1] + L/(r*r) * (1f/ANGLE_SAMPLES) * ticks;
            }
            return a;
        }

        float spiralAngle(float s) {
            if (s<=0) return 0;
            if (s>=1) return angles[ANGLE_SAMPLES];
            float fi = s * ANGLE_SAMPLES;
            int i = (int)fi;
            return angles[i] + (angles[i+1]-angles[i])*(fi-i);
        }

        Vec3 spiralPos(float s, Vec3 center) {
            float ss = ss(s);
            float r = lerp(startR, orbitRadius, ss);
            float h = startH * (1-ss);
            float a = spiralAngle(s);
            Vec3 dir = rotate(radialDir, a, axis);
            return center.add(dir.x*r, dir.y*r, dir.z*r).add(axis.x()*h, axis.y()*h, axis.z()*h);
        }

        private static float ss(float t) { return t*t*(3-2*t); }
        private static float lerp(float a, float b, float t) { return a+(b-a)*t; }
        private static Vec3 randomUnit(Random r) { return new Vec3(r.nextGaussian(),r.nextGaussian(),r.nextGaussian()).normalize(); }
        private static Vector3f toV3f(Vec3 v) { return new Vector3f((float)v.x,(float)v.y,(float)v.z); }
        private static Vec3 toMC(Vector3f v) { return new Vec3(v.x,v.y,v.z); }
        private static Vec3 rotate(Vec3 v, float ang, Vector3f ax) {
            var j = toV3f(v); new Quaternionf().rotateAxis(ang,ax).transform(j); return toMC(j);
        }
        private static Vector3f deviate(Vector3f b, float max, Random r) {
            float a = r.nextFloat()*max; var p = perp(b); var o = new Vector3f(b);
            new Quaternionf().rotateAxis(a,p).transform(o); return o;
        }
        private static Vector3f perp(Vector3f v) {
            return Math.abs(v.x())<0.9 ? new Vector3f(1,0,0).cross(v).normalize() : new Vector3f(0,1,0).cross(v).normalize();
        }
    }

    // ═══ Per-particle runtime ═══
    private static class ParticleRunner extends FXEffectExecutor {
        // PD controller gains for re-entry — critically damped (kd = 2*sqrt(kp))
        private static final float KP = 0.05f;
        private static final float KD = 0.45f;

        private final Supplier<Vec3> targetSupplier;
        private final OrbitDef def;
        private final float centerLerp;
        private Vec3 prevCtr, currCtr;

        private enum State { SPIRAL, ORBIT, REENTRY }
        private State state;
        private int age;        // ticks in current state (SPIRAL/ORBIT)
        private double phase;   // orbital phase (ORBIT only)

        // integrated position/velocity
        private Vec3 pos;
        private Vec3 vel;

        ParticleRunner(FX fx, Level level, Supplier<Vec3> target,
                       OrbitDef def, float centerLerp) {
            super(fx, level);
            this.targetSupplier = target;
            this.def = def;
            this.centerLerp = centerLerp;
            Vec3 c = target.get();
            this.currCtr = c;
            this.prevCtr = c;
            this.state = State.SPIRAL;
            this.age = 0;
            this.pos = def.spiralPos(0, c);
            this.vel = Vec3.ZERO;
        }

        // ══ Tick ══
        @Override
        public void updateFXObjectTick(IFXObject obj) {
            if (runtime == null || obj != runtime.getRoot()) return;
            prevCtr = currCtr;
            currCtr = currCtr.add(targetSupplier.get().subtract(currCtr).scale(centerLerp));

            switch (state) {
                case SPIRAL  -> tickSpiral();
                case ORBIT   -> tickOrbit();
                case REENTRY -> tickReentry();
            }
        }

        private void tickSpiral() {
            age++;
            if (age >= def.convergeTicks) {
                state = State.ORBIT;
                age = 0;
                phase = def.orbitPhase;
                pos = def.spiralPos(1f, currCtr);
            }
        }

        private void tickOrbit() {
            Vec3 realTarget = targetSupplier.get();
            float dist = (float)currCtr.distanceTo(realTarget);
            if (dist > Math.max(3f, def.orbitRadius * 1.2f)) {
                // re-entry: carry current orbital velocity
                vel = def.orbitVel((float)phase, currCtr);
                state = State.REENTRY;
                return;
            }
            age++;
            phase += def.orbitSpeed;
            pos = def.orbitPos((float)phase, currCtr);
        }

        private void tickReentry() {
            // Target: nearest orbit point around drifted center, with orbital velocity
            float[] endPhase = new float[1];
            Vec3 targetPos = def.nearest(pos, currCtr, endPhase);
            Vec3 targetVel = def.orbitVel(endPhase[0], currCtr);

            // PD controller: spring toward orbit + match orbital velocity
            Vec3 accel = targetPos.subtract(pos).scale(KP)
                    .add(targetVel.subtract(vel).scale(KD));

            vel = vel.add(accel);
            pos = pos.add(vel);

            // close enough to orbit → merge
            if (pos.distanceTo(targetPos) < def.orbitRadius * 0.1f) {
                state = State.ORBIT;
                age = 0;
                phase = endPhase[0];
                pos = targetPos;
            }
        }

        // ══ Frame ══
        @Override
        public void updateFXObjectFrame(IFXObject obj, float partialTicks) {
            if (runtime == null || obj != runtime.getRoot()) return;
            Vec3 center = prevCtr.lerp(currCtr, partialTicks);
            Vec3 framePos;

            switch (state) {
                case SPIRAL -> {
                    float s = Math.min(1f, (age + partialTicks) / def.convergeTicks);
                    framePos = def.spiralPos(s, center);
                }
                case ORBIT -> {
                    float p = (float)(phase + partialTicks * def.orbitSpeed);
                    framePos = def.orbitPos(p, center);
                }
                case REENTRY -> {
                    // extrapolate tick position forward by partialTicks for smooth sub-tick motion
                    framePos = pos.add(vel.scale(partialTicks));
                }
                default -> framePos = pos;
            }

            obj.updatePos(new Vector3f((float)framePos.x, (float)framePos.y, (float)framePos.z));
        }

        @Override
        public void start() {
            runtime = fx.createRuntime();
            runtime.getRoot().updatePos(new Vector3f(
                    (float)pos.x, (float)pos.y, (float)pos.z));
            runtime.emmit(this, 0);
        }

        void kill() {
            if (runtime != null) { runtime.destroy(true); runtime = null; }
        }
    }
}
