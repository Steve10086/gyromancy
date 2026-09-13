package com.astune.gyromancy.client.entity;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.entity.field.WindFieldEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.WeakHashMap;

/** Default Photon particle renderer for wind fields. */
public final class WindFieldRenderer extends MagicFieldRenderer<WindFieldEntity> {
    private static final ResourceLocation WIND_FIELD_FX =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "flow_particle");

    public WindFieldRenderer(EntityRendererProvider.Context context) {
        super(context, WIND_FIELD_FX);
    }

    @Override
    public void postRender (WindFieldEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                            MultiBufferSource bufferSource, int packedLight){
        emittedEffects.get(entity).forEach(
                e -> {
                    e.setDir(entity.direction().vector());
                    e.tick();
                });

    }
}
