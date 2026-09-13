package com.astune.gyromancy.entity.field;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.field.CircularFieldShape;
import com.astune.gyromancy.api.field.FieldDirection;
import com.astune.gyromancy.api.field.MagicFieldShape;
import com.astune.gyromancy.api.field.RectangularFieldShape;
import com.astune.gyromancy.compile.operator.EntityPayload;
import com.astune.gyromancy.compile.operator.WindFieldPushOp;
import com.astune.gyromancy.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A stationary wind field. The field owns the immutable geometry, direction,
 * and energy; its push behaviour is supplied by {@link WindFieldPushOp}.
 */
public final class WindFieldEntity extends MagicFieldEntity {
    private static final String RECTANGULAR_SHAPE = "rectangular";
    private static final String CIRCULAR_SHAPE = "circular";
    private static final String SHAPE_TYPE = "Type";
    private static final String SHAPE_LENGTH = "Length";
    private static final String SHAPE_WIDTH = "Width";
    private static final String SHAPE_HEIGHT = "Height";

    public static final double DEFAULT_ENERGY = 1.0;
    public static final FieldDirection DEFAULT_DIRECTION =
            new FieldDirection(new Vec3(0.0, 0.0, 1.0), 0.0F);

    /** Constructor used by Minecraft's entity type loader. */
    public WindFieldEntity(EntityType<WindFieldEntity> type, Level level) {
        this(type, level, new RectangularFieldShape(), DEFAULT_DIRECTION, DEFAULT_ENERGY);
    }

    public WindFieldEntity(EntityType<WindFieldEntity> type, Level level,
                           MagicFieldShape shape, FieldDirection direction,
                           double energy) {
        super(type, level, shape, direction, ElementType.WIND, energy);
        syncShapeData();
    }

    /** Creates and positions a wind field emitted by a field operator. */
    public WindFieldEntity(Level level, Vec3 position, MagicFieldShape shape,
                           FieldDirection direction, double energy) {
        this(ModEntities.WIND_FIELD.get(), level, shape, direction, energy);
        setPos(position);
    }

    public WindFieldEntity(Level level, Vec3 position, MagicFieldShape shape,
                           FieldDirection direction) {
        this(level, position, shape, direction, DEFAULT_ENERGY);
    }

    @Override
    protected List<? extends EntityPayload> defaultPayload() {
        return List.of(new WindFieldPushOp());
    }

    @Override
    protected void addShapeData(CompoundTag tag) {
        if (shape() instanceof RectangularFieldShape rectangle) {
            tag.putString(SHAPE_TYPE, RECTANGULAR_SHAPE);
            tag.putFloat(SHAPE_LENGTH, rectangle.length());
            tag.putFloat(SHAPE_WIDTH, rectangle.width());
            tag.putFloat(SHAPE_HEIGHT, rectangle.height());
        } else if (shape() instanceof CircularFieldShape circle) {
            tag.putString(SHAPE_TYPE, CIRCULAR_SHAPE);
            tag.putFloat(SHAPE_LENGTH, circle.length());
            tag.putFloat(SHAPE_WIDTH, circle.width());
            tag.putFloat(SHAPE_HEIGHT, circle.height());
        }
    }

    @Override
    protected MagicFieldShape readShape(CompoundTag tag) {
        String type = tag.getString(SHAPE_TYPE);
        if (!RECTANGULAR_SHAPE.equals(type) && !CIRCULAR_SHAPE.equals(type)) return shape();
        try {
            float length = tag.getFloat(SHAPE_LENGTH);
            float width = tag.getFloat(SHAPE_WIDTH);
            float height = tag.getFloat(SHAPE_HEIGHT);
            return RECTANGULAR_SHAPE.equals(type)
                    ? new RectangularFieldShape(length, width, height)
                    : new CircularFieldShape(length, width, height);
        } catch (IllegalArgumentException ignored) {
            // Invalid saved data must not make a chunk unloadable. Retain the
            // loader's safe one-block shape instead.
            return shape();
        }
    }
}
