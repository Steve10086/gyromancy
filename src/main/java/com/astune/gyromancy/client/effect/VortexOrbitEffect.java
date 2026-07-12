package com.astune.gyromancy.client.effect;

import com.lowdragmc.photon.client.fx.*;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Continuous particle effect: spherical spawn → accelerating spiral converge → circular orbit.
 * Emits at a given rate (particles/20ticks), recycles oldest when pool is full.
 */
@OnlyIn(Dist.CLIENT)
public class VortexOrbitEffect {
    private final ResourceLocation fxId;
    private final List<ParticleRunner> particles = new ArrayList<>();
    private final Level level;
    private Supplier<Vec3> target;
    private int maxParticles;
    private float rate; // particles per 20 ticks
    private float spawnRadius;
    private int convergeTicks;
    private Vec3 orbitAxis;
    private float orbitRadius;
    private int initialParticles;
    private float centerLerp;
    private float speed;
    private BooleanSupplier alive = () -> true;
    private final DynamicEffectProperties properties = new DynamicEffectProperties();
    private final Random rand = new Random();
    private FX fx;
    private float emitAccumulator;
    private long lastGameTick = -1;
    private boolean started;

    private final static ResourceLocation DEFAULT_FX = ResourceLocation.fromNamespaceAndPath("gyromancy", "surrounding_element");

    public VortexOrbitEffect(Level level, Supplier<Vec3> target,
                             int maxParticles, float rate, float spawnRadius, int convergeTicks,
                             Vec3 orbitAxis, float orbitRadius) {
        this(DEFAULT_FX, level, target, maxParticles, rate, spawnRadius, convergeTicks,
                orbitAxis, orbitRadius, 20, 0.04f);
    }

    public VortexOrbitEffect(Level level, Supplier<Vec3> target,
                             int maxParticles, float rate, float spawnRadius, int convergeTicks,
                             Vec3 orbitAxis, float orbitRadius, int initialParticles) {
        this(DEFAULT_FX, level, target, maxParticles, rate, spawnRadius, convergeTicks,
                orbitAxis, orbitRadius, initialParticles, 0.04f);
    }
    public VortexOrbitEffect(ResourceLocation resourceLocation, Level level, Supplier<Vec3> target,
                             int maxParticles, float rate, float spawnRadius, int convergeTicks,
                             Vec3 orbitAxis, float orbitRadius, int initialParticles) {
        this(resourceLocation, level, target, maxParticles, rate, spawnRadius, convergeTicks,
                orbitAxis, orbitRadius, initialParticles, 0.04f);
    }

    public VortexOrbitEffect(ResourceLocation fxId, Level level, Supplier<Vec3> target,
                             int maxParticles, float rate, float spawnRadius, int convergeTicks,
                             Vec3 orbitAxis, float orbitRadius, int initialParticles, float centerLerp) {
        this(fxId, level, target, maxParticles, rate, spawnRadius, convergeTicks,
                orbitAxis, orbitRadius, initialParticles, centerLerp, 1f);
    }

    public VortexOrbitEffect(ResourceLocation fxId, Level level, Supplier<Vec3> target,
                             int maxParticles, float rate, float spawnRadius, int convergeTicks,
                             Vec3 orbitAxis, float orbitRadius, int initialParticles, float centerLerp,
                             float speed) {
        this.level = level;
        this.target = target;
        this.maxParticles = maxParticles;
        this.rate = rate;
        this.spawnRadius = spawnRadius;
        this.convergeTicks = convergeTicks;
        this.orbitAxis = orbitAxis.normalize();
        this.orbitRadius = orbitRadius;
        this.initialParticles = initialParticles;
        this.centerLerp = centerLerp;
        this.speed = Math.max(0, speed);
        this.fxId = fxId;
    }



    /** Start emitting. Safe to call multiple times. */
    public void start() {
        if (started) return;
        started = true;
        fx = FXHelper.getFX(fxId);
        if (fx == null) {
            started = false;
            return;
        }
        emitAccumulator = 0;
        for (int i = 0; i < initialParticles && i < maxParticles; i++) emitOne();
    }

    /** Call every game tick to drive emission. Safe to call at any frequency. */
    public void tick() {
        if (!started || fx == null) return;
        particles.removeIf(particle -> !particle.isAlive());
        for (var particle : particles) particle.applyProperties();
        long now = level.getGameTime();
        if (now == lastGameTick) return;
        lastGameTick = now;
        emitAccumulator += rate / 20f;
        while (emitAccumulator >= 1f) {
            emitOne();
            emitAccumulator -= 1f;
        }
    }

    private void emitOne() {
        if (maxParticles <= 0) return;
        // recycle oldest if at capacity
        if (particles.size() >= maxParticles) {
            particles.get(0).kill();
            particles.remove(0);
        }
        var def = OrbitDef.random(spawnRadius, convergeTicks, orbitAxis, orbitRadius, rand);
        var p = new ParticleRunner(fx, level, target, def, centerLerp, speed, rand, properties, alive);
        p.start();
        particles.add(p);
    }

    private void rebuildParticles(int count) {
        if (!started || fx == null) return;
        for (var particle : particles) particle.kill();
        particles.clear();
        for (int i = 0; i < Math.min(count, maxParticles); i++) emitOne();
    }

    public void kill() {
        for (var p : particles) p.kill();
        particles.clear();
        started = false;
        emitAccumulator = 0;
    }

    public VortexOrbitEffect setSize(float size) { properties.setSize(size); return this; }
    
    public VortexOrbitEffect setAlpha(float alpha) { properties.setAlpha(alpha); return this; }

    public VortexOrbitEffect setOffset(Vec3 offset) {
        return setOffset(offset.x, offset.y, offset.z);
    }

    public VortexOrbitEffect setOffset(double x, double y, double z) {
        properties.setOffset(x, y, z);
        return this;
    }

    public VortexOrbitEffect setMaxParticles(int maxParticles) {
        this.maxParticles = Math.max(0, maxParticles);
        while (particles.size() > this.maxParticles) particles.remove(0).kill();
        return this;
    }

    public VortexOrbitEffect setRate(float rate) { this.rate = rate; return this; }

    public VortexOrbitEffect setSpawnRadius(float spawnRadius) {
        if (this.spawnRadius == spawnRadius) return this;
        this.spawnRadius = spawnRadius;
        rebuildParticles(particles.size());
        return this;
    }

    public VortexOrbitEffect setConvergeTicks(int convergeTicks) {
        if (this.convergeTicks == convergeTicks) return this;
        this.convergeTicks = convergeTicks;
        rebuildParticles(particles.size());
        return this;
    }

    public VortexOrbitEffect setOrbitAxis(Vec3 orbitAxis) {
        orbitAxis = orbitAxis.normalize();
        if (this.orbitAxis.equals(orbitAxis)) return this;
        this.orbitAxis = orbitAxis;
        rebuildParticles(particles.size());
        return this;
    }

    public VortexOrbitEffect setOrbitRadius(float orbitRadius) {
        if (this.orbitRadius == orbitRadius) return this;
        this.orbitRadius = orbitRadius;
        for (var particle : particles) particle.setOrbitRadius(orbitRadius);
        return this;
    }

    public VortexOrbitEffect setInitialParticles(int initialParticles) {
        this.initialParticles = Math.max(0, initialParticles);
        rebuildParticles(this.initialParticles);
        return this;
    }

    public VortexOrbitEffect setCenterLerp(float centerLerp) {
        this.centerLerp = centerLerp;
        for (var particle : particles) particle.setCenterLerp(centerLerp);
        return this;
    }

    public VortexOrbitEffect setSpeed(float speed) {
        this.speed = Math.max(0, speed);
        for (var particle : particles) particle.setSpeed(this.speed);
        return this;
    }

    public VortexOrbitEffect setAlive(BooleanSupplier alive) {
        this.alive = alive;
        for (var particle : particles) particle.setAlive(alive);
        return this;
    }

    public VortexOrbitEffect setOrbitCenter(Vec3 center) {
        this.target = () -> center;
        for (var particle : particles) particle.setOrbitCenter(this.target, center);
        return this;
    }

    // ═══ Orbit def ═══
    private static final class OrbitDef {
        final Vector3f axis, u, v;
        float orbitRadius;
        final float orbitSpeed;
        final int convergeTicks;
        final Vec3 radialDir;
        final float startR, startH;
        float[] angles;
        float orbitPhase;

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

        void setOrbitRadius(float orbitRadius) {
            this.orbitRadius = orbitRadius;
            this.angles = buildAngles(startR, orbitRadius, orbitSpeed, convergeTicks);
            Vec3 endDir = rotate(radialDir, angles[ANGLE_SAMPLES], axis);
            this.orbitPhase = (float) Math.atan2(
                    endDir.x*v.x() + endDir.y*v.y() + endDir.z*v.z(),
                    endDir.x*u.x() + endDir.y*u.y() + endDir.z*u.z());
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

        Vec3 nearest(Vec3 fromWorld, Vec3 center) { return nearest(fromWorld, center, null); }

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
        private static final float KP = 0.05f;
        private static final float KD = 0.45f;

        private Supplier<Vec3> targetSupplier;
        private final OrbitDef def;
        private float centerLerp;
        private float speed;
        private final DynamicEffectProperties properties;
        private BooleanSupplier alive;
        private Vec3 prevCtr, currCtr;

        private enum State { SPIRAL, ORBIT, REENTRY }
        private State state;
        private float age;
        private double phase;
        private Vec3 pos;
        private Vec3 vel;

        ParticleRunner(FX fx, Level level, Supplier<Vec3> target,
                       OrbitDef def, float centerLerp, float speed, Random rand, DynamicEffectProperties properties,
                       BooleanSupplier alive) {
            super(fx, level);
            this.targetSupplier = target;
            this.def = def;
            this.centerLerp = centerLerp;
            this.speed = speed;
            this.properties = properties;
            this.alive = alive;
            // stagger start by a random offset to spread out converge
            float stagger = rand.nextFloat() * def.convergeTicks * 0.3f;
            Vec3 c = target.get();
            this.currCtr = c;
            this.prevCtr = c;
            this.state = State.SPIRAL;
            this.age = -stagger;
            this.pos = def.spiralPos(0, c);
            this.vel = Vec3.ZERO;
        }

        @Override
        public void updateFXObjectTick(IFXObject obj) {
            if (runtime == null || obj != runtime.getRoot()) return;
            if (!alive.getAsBoolean()) {
                kill();
                return;
            }
            prevCtr = currCtr;
            currCtr = currCtr.add(targetSupplier.get().subtract(currCtr).scale(centerLerp));

            switch (state) {
                case SPIRAL  -> tickSpiral();
                case ORBIT   -> tickOrbit();
                case REENTRY -> tickReentry();
            }
        }

        private void tickSpiral() {
            age += speed;
            if (age < 0) return; // stagger delay
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
                vel = def.orbitVel((float)phase, currCtr).scale(speed);
                state = State.REENTRY;
                return;
            }
            age += speed;
            phase += def.orbitSpeed * speed;
            pos = def.orbitPos((float)phase, currCtr);
        }

        private void tickReentry() {
            float[] endPhase = new float[1];
            Vec3 targetPos = def.nearest(pos, currCtr, endPhase);
            Vec3 targetVel = def.orbitVel(endPhase[0], currCtr).scale(speed);

            Vec3 accel = targetPos.subtract(pos).scale(KP)
                    .add(targetVel.subtract(vel).scale(KD))
                    .scale(speed);

            vel = vel.add(accel);
            pos = pos.add(vel);

            if (pos.distanceTo(targetPos) < def.orbitRadius * 0.1f) {
                state = State.ORBIT;
                age = 0;
                phase = endPhase[0];
                pos = targetPos;
            }
        }

        @Override
        public void updateFXObjectFrame(IFXObject obj, float partialTicks) {
            if (runtime == null || obj != runtime.getRoot()) return;
            Vec3 center = prevCtr.lerp(currCtr, partialTicks);
            Vec3 framePos;

            switch (state) {
                case SPIRAL -> {
                    float t = age < 0 ? 0 : Math.min(1f, (age + partialTicks) / def.convergeTicks);
                    framePos = def.spiralPos(t, center);
                }
                case ORBIT -> {
                    float p = (float)(phase + partialTicks * def.orbitSpeed * speed);
                    framePos = def.orbitPos(p, center);
                }
                case REENTRY -> {
                    framePos = pos.add(vel.scale(partialTicks));
                }
                default -> framePos = pos;
            }

            framePos = properties.offset(framePos);
            obj.updatePos(new Vector3f((float)framePos.x, (float)framePos.y, (float)framePos.z));
        }

        @Override
        public void start() {
            runtime = fx.createRuntime(true);
            Vec3 startPos = properties.offset(pos);
            runtime.getRoot().updatePos(new Vector3f(
                    (float)startPos.x, (float)startPos.y, (float)startPos.z));
            properties.apply(runtime);
            runtime.emmit(this, 0);
        }

        void kill() {
            if (runtime != null) { runtime.destroy(true); runtime = null; }
        }

        boolean isAlive() {
            if (runtime != null && runtime.isAlive()) return true;
            runtime = null;
            return false;
        }

        void applyProperties() {
            if (runtime != null) properties.apply(runtime);
        }

        void setCenterLerp(float centerLerp) {
            this.centerLerp = centerLerp;
        }

        void setOrbitRadius(float orbitRadius) {
            def.setOrbitRadius(orbitRadius);
        }

        void setSpeed(float speed) {
            vel = vel.scale(this.speed == 0 ? speed : speed / this.speed);
            this.speed = speed;
        }

        void setOrbitCenter(Supplier<Vec3> targetSupplier, Vec3 center) {
            this.targetSupplier = targetSupplier;
            if (center.equals(currCtr)) return;

            Vec3 oldCenter = currCtr;
            if (state == State.SPIRAL) {
                float t = age < 0 ? 0 : Math.min(1f, age / def.convergeTicks);
                pos = def.spiralPos(t, oldCenter);
                vel = Vec3.ZERO;
            } else if (state == State.ORBIT) {
                pos = def.orbitPos((float) phase, oldCenter);
                vel = def.orbitVel((float) phase, oldCenter).scale(speed);
            }
            prevCtr = center;
            currCtr = center;
            state = State.REENTRY;
        }

        void setAlive(BooleanSupplier alive) {
            this.alive = alive;
        }
    }
}
