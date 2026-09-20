package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.Gyromancy;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Persistence support for compiled vector operations. */
public final class VectorOpSerialization {
    public static final Codec<Vec3> VEC3_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("x").forGetter(vector -> vector.x),
            Codec.DOUBLE.fieldOf("y").forGetter(vector -> vector.y),
            Codec.DOUBLE.fieldOf("z").forGetter(vector -> vector.z)
    ).apply(instance, Vec3::new));

    public static final Codec<SerializedVector> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("type").forGetter(SerializedVector::type),
            Codec.PASSTHROUGH.fieldOf("data").forGetter(SerializedVector::data)
    ).apply(instance, SerializedVector::new));

    private static final Codec<StaticVectorOp.TermKind> TERM_KIND_CODEC = Codec.STRING.xmap(
            value -> StaticVectorOp.TermKind.valueOf(value.toUpperCase(Locale.ROOT)),
            value -> value.name().toLowerCase(Locale.ROOT));
    private static final Codec<StaticVectorOp.Term> TERM_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TERM_KIND_CODEC.fieldOf("kind").forGetter(StaticVectorOp.Term::kind),
            VEC3_CODEC.fieldOf("value").forGetter(StaticVectorOp.Term::value)
    ).apply(instance, StaticVectorOp.Term::new));
    private static final Codec<SerializedInput> INPUT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CODEC.fieldOf("vector").forGetter(SerializedInput::vector),
            compositionModeCodec().fieldOf("mode").forGetter(SerializedInput::mode)
    ).apply(instance, SerializedInput::new));
    private static final Codec<StaticVectorData> STATIC_VECTOR_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TERM_CODEC.listOf().fieldOf("terms").forGetter(StaticVectorData::terms),
            INPUT_CODEC.listOf().fieldOf("inputs").forGetter(StaticVectorData::inputs),
            Codec.BOOL.fieldOf("curl").forGetter(StaticVectorData::curl)
    ).apply(instance, StaticVectorData::new));
    private static final Codec<RotationVectorData> ROTATION_VECTOR_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            INPUT_CODEC.listOf().fieldOf("axis_inputs").forGetter(RotationVectorData::axisInputs),
            INPUT_CODEC.listOf().fieldOf("vector_inputs").forGetter(RotationVectorData::vectorInputs),
            Codec.DOUBLE.fieldOf("rotation_speed").forGetter(RotationVectorData::rotationSpeed)
    ).apply(instance, RotationVectorData::new));
    private static final Codec<StaticRotationVectorData> STATIC_ROTATION_VECTOR_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            INPUT_CODEC.listOf().fieldOf("inputs").forGetter(StaticRotationVectorData::inputs),
            Codec.BOOL.fieldOf("curl").forGetter(StaticRotationVectorData::curl)
    ).apply(instance, StaticRotationVectorData::new));
    private static final Codec<RevertVectorData> REVERT_VECTOR_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            INPUT_CODEC.listOf().fieldOf("inputs").forGetter(RevertVectorData::inputs)
    ).apply(instance, RevertVectorData::new));

    private static final List<Adapter<?>> ADAPTERS = new CopyOnWriteArrayList<>();

    static {
        register(new Adapter<StaticVectorOp>() {
            @Override
            public ResourceLocation id() {
                return adapterId("static_vector");
            }

            @Override
            public Class<StaticVectorOp> vectorType() {
                return StaticVectorOp.class;
            }

            @Override
            public Codec<StaticVectorOp> codec() {
                return STATIC_VECTOR_CODEC.xmap(
                        data -> new StaticVectorOp(VectorOp.RUNTIME_ID, null, List.of(), 0,
                                data.terms(), deserializeInputs(data.inputs()), data.curl()),
                        vector -> new StaticVectorData(vector.terms(), serializeInputs(vector.vectorInputs()),
                                vector.curl()));
            }
        });
        register(new Adapter<RotationVectorOp>() {
            @Override
            public ResourceLocation id() {
                return adapterId("rotation_vector");
            }

            @Override
            public Class<RotationVectorOp> vectorType() {
                return RotationVectorOp.class;
            }

            @Override
            public Codec<RotationVectorOp> codec() {
                return ROTATION_VECTOR_CODEC.xmap(
                        data -> new RotationVectorOp(VectorOp.RUNTIME_ID, null, List.of(), 0,
                                deserializeInputs(data.axisInputs()), deserializeInputs(data.vectorInputs()),
                                data.rotationSpeed()),
                        vector -> new RotationVectorData(serializeInputs(vector.axisInputs()),
                                serializeInputs(vector.vectorInputs()), vector.rotationSpeed()));
            }
        });
        register(new Adapter<StaticRotationVectorOp>() {
            @Override
            public ResourceLocation id() {
                return adapterId("static_rotation_vector");
            }

            @Override
            public Class<StaticRotationVectorOp> vectorType() {
                return StaticRotationVectorOp.class;
            }

            @Override
            public Codec<StaticRotationVectorOp> codec() {
                return STATIC_ROTATION_VECTOR_CODEC.xmap(
                        data -> new StaticRotationVectorOp(VectorOp.RUNTIME_ID, null, List.of(), 0,
                                deserializeInputs(data.inputs()), data.curl()),
                        vector -> new StaticRotationVectorData(serializeInputs(vector.vectorInputs()),
                                vector.curl()));
            }
        });
        register(new Adapter<ArrayNormalVectorOp>() {
            @Override
            public ResourceLocation id() {
                return adapterId("array_normal");
            }

            @Override
            public Class<ArrayNormalVectorOp> vectorType() {
                return ArrayNormalVectorOp.class;
            }

            @Override
            public Codec<ArrayNormalVectorOp> codec() {
                return Codec.DOUBLE.xmap(
                        scale -> new ArrayNormalVectorOp(VectorOp.RUNTIME_ID, null, List.of(), 0, scale),
                        ArrayNormalVectorOp::scale);
            }

            @Override
            public Optional<LegacyData> encodeLegacy(ArrayNormalVectorOp vector) {
                return Optional.of(LegacyData.scalar(vector.scale(), "array_normal", false));
            }
        });
        register(new Adapter<GravityVectorOp>() {
            @Override
            public ResourceLocation id() {
                return adapterId("gravity_vector");
            }

            @Override
            public Class<GravityVectorOp> vectorType() {
                return GravityVectorOp.class;
            }

            @Override
            public Codec<GravityVectorOp> codec() {
                return Codec.unit(() -> new GravityVectorOp(VectorOp.RUNTIME_ID, null, List.of(), 0));
            }
        });
        register(new Adapter<RevertVectorOp>() {
            @Override
            public ResourceLocation id() {
                return adapterId("revert_vector");
            }

            @Override
            public Class<RevertVectorOp> vectorType() {
                return RevertVectorOp.class;
            }

            @Override
            public Codec<RevertVectorOp> codec() {
                return REVERT_VECTOR_CODEC.xmap(
                        data -> new RevertVectorOp(VectorOp.RUNTIME_ID, null, List.of(), 0,
                                deserializeInputs(data.inputs())),
                        vector -> new RevertVectorData(serializeInputs(vector.vectorInputs())));
            }
        });
    }

    private VectorOpSerialization() {}

    private static ResourceLocation adapterId(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, path);
    }

    private static Codec<VectorComposition.Mode> compositionModeCodec() {
        return Codec.STRING.xmap(
                value -> VectorComposition.Mode.valueOf(value.toUpperCase(Locale.ROOT)),
                value -> value.name().toLowerCase(Locale.ROOT));
    }

    public static void register(Adapter<?> adapter) {
        ADAPTERS.removeIf(existing -> existing.id().equals(adapter.id()));
        ADAPTERS.add(adapter);
    }

    public static Optional<SerializedVector> encode(VectorOp vector) {
        return ADAPTERS.stream()
                .filter(adapter -> adapter.vectorType().isInstance(vector))
                .findFirst()
                .flatMap(adapter -> encode(adapter, vector));
    }

    public static Optional<LegacyData> encodeLegacy(VectorOp vector) {
        if (vector instanceof StaticVectorOp staticVector && staticVector.terms().size() == 1) {
            StaticVectorOp.Term term = staticVector.terms().getFirst();
            return switch (term.kind()) {
                case WORLD -> Optional.of(LegacyData.world(term.value(), "compile_snapshot", false));
                case LOCAL_FRAME -> Optional.of(LegacyData.local(term.value(), "array_local"));
                case LIVE_FRAME -> Optional.of(LegacyData.local(term.value(), "live_array"));
                case VELOCITY -> Optional.of(LegacyData.scalar(term.value().x, "current_velocity", true));
                case FACING -> Optional.of(LegacyData.scalar(term.value().x, "entity_facing", true));
                case NORMAL -> Optional.of(LegacyData.scalar(term.value().x, "array_normal", false));
            };
        }
        return ADAPTERS.stream()
                .filter(adapter -> adapter.vectorType().isInstance(vector))
                .findFirst()
                .flatMap(adapter -> encodeLegacy(adapter, vector));
    }

    public static VectorOp decode(SerializedVector serialized) {
        Adapter<?> adapter = ADAPTERS.stream()
                .filter(candidate -> candidate.id().equals(serialized.type()))
                .findFirst()
                .orElse(null);
        if (adapter == null) return decodeLegacySerialized(serialized);
        return decode(adapter, serialized.data());
    }

    private static VectorOp decodeLegacySerialized(SerializedVector serialized) {
        String type = serialized.type().getPath().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "compile" -> direct(new StaticVectorOp.Term(StaticVectorOp.TermKind.WORLD,
                    serialized.data().decode(VEC3_CODEC).getOrThrow().getFirst()));
            case "activation" -> direct(new StaticVectorOp.Term(StaticVectorOp.TermKind.LOCAL_FRAME,
                    serialized.data().decode(VEC3_CODEC).getOrThrow().getFirst()));
            case "live" -> direct(new StaticVectorOp.Term(StaticVectorOp.TermKind.LIVE_FRAME,
                    serialized.data().decode(VEC3_CODEC).getOrThrow().getFirst()));
            case "velocity" -> direct(new StaticVectorOp.Term(StaticVectorOp.TermKind.VELOCITY,
                    new Vec3(serialized.data().decode(Codec.DOUBLE).getOrThrow().getFirst(), 0.0, 0.0)));
            case "facing" -> direct(new StaticVectorOp.Term(StaticVectorOp.TermKind.FACING,
                    new Vec3(serialized.data().decode(Codec.DOUBLE).getOrThrow().getFirst(), 0.0, 0.0)));
            case "normal" -> direct(new StaticVectorOp.Term(StaticVectorOp.TermKind.NORMAL,
                    new Vec3(serialized.data().decode(Codec.DOUBLE).getOrThrow().getFirst(), 0.0, 0.0)));
            default -> throw new IllegalArgumentException(
                    "Unknown vector op adapter: " + serialized.type());
        };
    }

    /** Decodes the pre-vector-op Momentum payload format. */
    public static VectorOp decodeLegacy(LegacyData data) {
        String frame = data.directionFrame()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .orElse("");
        if (frame.isEmpty()) {
            return data.alongFacing()
                    ? direct(new StaticVectorOp.Term(StaticVectorOp.TermKind.VELOCITY,
                    new Vec3(data.magnitude(), 0.0, 0.0)))
                    : direct(new StaticVectorOp.Term(StaticVectorOp.TermKind.WORLD,
                    scaled(data.direction(), data.magnitude())));
        }
        return switch (frame) {
            case "compile", "compile_snapshot" -> direct(new StaticVectorOp.Term(
                    StaticVectorOp.TermKind.WORLD, scaled(data.direction(), data.magnitude())));
            case "activation", "activation_snapshot", "array_local" -> direct(new StaticVectorOp.Term(
                    StaticVectorOp.TermKind.LOCAL_FRAME, scaled(data.localDirection(), data.magnitude())));
            case "live", "live_array" -> direct(new StaticVectorOp.Term(
                    StaticVectorOp.TermKind.LIVE_FRAME, scaled(data.localDirection(), data.magnitude())));
            case "velocity", "current_velocity" -> direct(new StaticVectorOp.Term(
                    StaticVectorOp.TermKind.VELOCITY, new Vec3(data.magnitude(), 0.0, 0.0)));
            case "facing", "entity_facing" -> direct(new StaticVectorOp.Term(
                    StaticVectorOp.TermKind.FACING, new Vec3(data.magnitude(), 0.0, 0.0)));
            case "normal", "array_normal" -> direct(new StaticVectorOp.Term(
                    StaticVectorOp.TermKind.NORMAL, new Vec3(data.magnitude(), 0.0, 0.0)));
            default -> throw new IllegalArgumentException("Unknown legacy vector frame: " + frame);
        };
    }

    private static StaticVectorOp direct(StaticVectorOp.Term term) {
        return StaticVectorOp.direct(VectorOp.RUNTIME_ID, term);
    }

    private static List<SerializedInput> serializeInputs(List<VectorComposition.Input> inputs) {
        return inputs.stream()
                .map(input -> new SerializedInput(
                        encode(input.vector()).orElseThrow(() -> new IllegalStateException(
                                "VectorOp has no persistence adapter: "
                                        + input.vector().getClass().getName())),
                        input.mode()))
                .toList();
    }

    private static List<VectorComposition.Input> deserializeInputs(List<SerializedInput> inputs) {
        return inputs.stream()
                .map(input -> new VectorComposition.Input(decode(input.vector()), input.mode()))
                .toList();
    }

    private static <T extends VectorOp> Optional<SerializedVector> encode(
            Adapter<T> adapter, VectorOp vector) {
        T typed = adapter.vectorType().cast(vector);
        return adapter.codec().encodeStart(NbtOps.INSTANCE, typed)
                .result()
                .map(data -> new SerializedVector(adapter.id(), new Dynamic<>(NbtOps.INSTANCE, data)));
    }

    private static <T extends VectorOp> Optional<LegacyData> encodeLegacy(
            Adapter<T> adapter, VectorOp vector) {
        return adapter.encodeLegacy(adapter.vectorType().cast(vector));
    }

    private static <T extends VectorOp> VectorOp decode(Adapter<T> adapter, Dynamic<?> data) {
        return data.decode(adapter.codec()).getOrThrow().getFirst();
    }

    private static Vec3 scaled(Vec3 vector, double magnitude) {
        if (vector == null || vector.lengthSqr() < 1.0E-8 || Math.abs(magnitude) < 1.0E-8) {
            return Vec3.ZERO;
        }
        return vector.normalize().scale(magnitude);
    }

    public interface Adapter<T extends VectorOp> {
        ResourceLocation id();

        Class<T> vectorType();

        Codec<T> codec();

        default Optional<LegacyData> encodeLegacy(T vector) {
            return Optional.empty();
        }
    }

    public record SerializedVector(ResourceLocation type, Dynamic<?> data) {
        public SerializedVector {
            if (type == null || data == null) {
                throw new IllegalArgumentException("A serialized VectorOp needs type and data");
            }
        }
    }

    private record SerializedInput(SerializedVector vector, VectorComposition.Mode mode) {}

    private record StaticVectorData(List<StaticVectorOp.Term> terms,
                                    List<SerializedInput> inputs, boolean curl) {}

    private record RotationVectorData(List<SerializedInput> axisInputs,
                                      List<SerializedInput> vectorInputs,
                                      double rotationSpeed) {}

    private record StaticRotationVectorData(List<SerializedInput> inputs, boolean curl) {}

    private record RevertVectorData(List<SerializedInput> inputs) {}

    public record LegacyData(Vec3 direction, double magnitude, boolean alongFacing,
                             Vec3 localDirection, Optional<String> directionFrame) {
        public LegacyData {
            direction = direction == null ? Vec3.ZERO : direction;
            localDirection = localDirection == null ? Vec3.ZERO : localDirection;
            directionFrame = directionFrame == null ? Optional.empty() : directionFrame;
        }

        private static LegacyData world(Vec3 vector, String frame, boolean alongFacing) {
            return new LegacyData(normalized(vector), vector == null ? 0.0 : vector.length(),
                    alongFacing, normalized(vector), Optional.of(frame));
        }

        private static LegacyData local(Vec3 vector, String frame) {
            return new LegacyData(Vec3.ZERO, vector == null ? 0.0 : vector.length(),
                    false, normalized(vector), Optional.of(frame));
        }

        private static LegacyData scalar(double magnitude, String frame, boolean alongFacing) {
            return new LegacyData(Vec3.ZERO, magnitude, alongFacing, Vec3.ZERO, Optional.of(frame));
        }

        private static Vec3 normalized(Vec3 vector) {
            return vector == null || vector.lengthSqr() < 1.0E-8 ? Vec3.ZERO : vector.normalize();
        }
    }
}
