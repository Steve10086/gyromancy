package com.astune.gyromancy.api.element;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.Arrays;

public record ElementConcentrations(long[] values, long[] derivatives) {

    public static final int SIZE = ElementType.COUNT;
    public static final long MAX_VALUE = Integer.MAX_VALUE;
    public static final ElementConcentrations ZERO = new ElementConcentrations(new long[SIZE], new long[SIZE]);

    public static final long MIN_VALUE = -Integer.MAX_VALUE;

    public ElementConcentrations {
        for (int i = 0; i < SIZE; i++) values[i] = clamp(values[i]);
    }

    private static long clamp(long v) { return Math.max(MIN_VALUE, Math.min(v, MAX_VALUE)); }

    public static ElementConcentrations ofValues(long[] v) { return new ElementConcentrations(v, new long[SIZE]); }
    public static ElementConcentrations uniform(long v) {
        long[] a = new long[SIZE]; Arrays.fill(a, clamp(v));
        return new ElementConcentrations(a, new long[SIZE]);
    }

    public long get(ElementType t) { return values[t.ordinal()]; }
    public long getDerivative(ElementType t) { return derivatives[t.ordinal()]; }

    public ElementConcentrations withValue(ElementType t, long v) {
        long[] nv = values.clone(); nv[t.ordinal()] = clamp(v);
        return new ElementConcentrations(nv, derivatives);
    }

    /**
     * 3D diffusion: average of self + up to 26 neighbors (3×3×3 grid).
     * Null entries = non-overridden neighbors, use biome default.
     */
    public ElementConcentrations diffuse(ElementConcentrations[] neighbors, ElementConcentrations biomeDefaults) {
        long[] newValues = new long[SIZE];
        long count = 1; // self
        for (int i = 0; i < SIZE; i++) newValues[i] = values[i];

        for (ElementConcentrations n : neighbors) {
            long[] src = (n != null) ? n.values : biomeDefaults.values;
            for (int i = 0; i < SIZE; i++) newValues[i] += src[i];
            count++;
        }

        for (int i = 0; i < SIZE; i++) newValues[i] = clamp(newValues[i] / count);
        return new ElementConcentrations(newValues, derivatives);
    }

    // ── Single‑tick step: decay → share excess/27 with 26 neighbors ──

    /** Per‑cell share sent to each of the 27 cells (self + 26 neighbors). */
    public long[] excessShare(ElementConcentrations defaults) {
        long[] share = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            long excess = values[i] - defaults.values[i];
            if (excess > 0) share[i] = excess / 27;
        }
        return share;
    }

    // ── Decay: bell‑shaped above default, 25 % recovery below ──
    private static final long BELL_HALF = 50_000L;
    private static final long LOG_DECAY_THRESHOLD = 100_000L;
    private static final long MIN_RATE = 1, MAX_RATE = 50; // per‑mille

    static long bellDecay(long excess) {
        if (excess <= 0) return 0;
        long rate;
        if (excess <= BELL_HALF) {
            rate = MIN_RATE + (MAX_RATE - MIN_RATE) * excess / BELL_HALF;
        } else {
            rate = MAX_RATE;
        }
        long decay = excess * rate / 1000;
        if (excess > LOG_DECAY_THRESHOLD)
            decay += (long) (excess - LOG_DECAY_THRESHOLD - Math.ceil(Math.log(excess - LOG_DECAY_THRESHOLD + 1)));
        return Math.max(1, decay); // ponytail: floor→1 prevents permanent residue when excess < 1000
    }

    public ElementConcentrations decayAndRecover(ElementConcentrations defaults) {
        long[] nv = new long[SIZE], nd = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            long cur = values[i], tgt = defaults.values[i];
            if (cur > tgt)       nv[i] = clamp(cur - bellDecay(cur - tgt));
            else if (cur < tgt)  nv[i] = clamp(cur + (tgt - cur) / 4);
            else                 nv[i] = cur;
            nd[i] = nv[i] - values[i];
        }
        return new ElementConcentrations(nv, nd);
    }

    public boolean isCloseToDefault(ElementConcentrations defaults) {
        for (int i = 0; i < SIZE; i++) {
            long diff = Math.abs(values[i] - defaults.values[i]);
            long threshold = Math.max(10L, defaults.values[i] / 10);
            if (diff >= threshold) return false;
        }
        return true;
    }

    // ── Codec ──
    private static final Codec<long[]> LA = Codec.LONG.listOf().xmap(
            l -> { long[] a=new long[l.size()]; for(int i=0;i<a.length;i++)a[i]=l.get(i); return a; },
            a -> { var l=new java.util.ArrayList<Long>(a.length); for(long v:a)l.add(v); return l; });
    public static final Codec<ElementConcentrations> CODEC = RecordCodecBuilder.create(i ->
            i.group(LA.fieldOf("values").forGetter(ElementConcentrations::values),
                    LA.fieldOf("derivatives").forGetter(ElementConcentrations::derivatives))
             .apply(i, ElementConcentrations::new));

    public record PosEntry(BlockPos pos, ElementConcentrations concentrations) {
        public static final Codec<PosEntry> CODEC = RecordCodecBuilder.create(i ->
                i.group(BlockPos.CODEC.fieldOf("pos").forGetter(PosEntry::pos),
                        ElementConcentrations.CODEC.fieldOf("conc").forGetter(PosEntry::concentrations))
                 .apply(i, PosEntry::new));
    }

    public static Codec<java.util.HashMap<BlockPos, ElementConcentrations>> mapCodec() {
        return PosEntry.CODEC.listOf().xmap(
                l -> { var m=new java.util.HashMap<BlockPos,ElementConcentrations>(); for(var e:l)m.put(e.pos(),e.concentrations()); return m; },
                m -> m.entrySet().stream().map(e->new PosEntry(e.getKey(),e.getValue())).toList());
    }

    @Override public String toString() {
        var sb=new StringBuilder("EC{");
        for(ElementType t:ElementType.values())sb.append(t.name()).append("=").append(get(t)).append(" ");
        return sb.append("}").toString();
    }
}
