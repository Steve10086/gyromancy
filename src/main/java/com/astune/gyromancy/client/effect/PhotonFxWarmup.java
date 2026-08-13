package com.astune.gyromancy.client.effect;

import com.astune.gyromancy.Gyromancy;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Loads this mod's Photon definitions before the first world render.
 * Photon compiles custom materials while deserializing an FX, so doing this
 * from an entity renderer causes a visible one-time render-thread hitch.
 */
@OnlyIn(Dist.CLIENT)
public final class PhotonFxWarmup {
    private static boolean warmed;

    private PhotonFxWarmup() {}

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (warmed || minecraft.level != null) return;

        var resources = minecraft.getResourceManager().listResources(
                "fx",
                id -> id.getNamespace().equals(Gyromancy.MODID)
                        && id.getPath().endsWith(FX.SUFFIX));
        if (resources.isEmpty()) return;

        int loaded = 0;
        for (ResourceLocation resource : resources.keySet()) {
            String path = resource.getPath();
            String fxPath = path.substring(FXHelper.FX_PATH.length(),
                    path.length() - FX.SUFFIX.length());
            ResourceLocation fxId = ResourceLocation.fromNamespaceAndPath(
                    resource.getNamespace(), fxPath);
            if (FXHelper.getFX(fxId) != null) loaded++;
        }

        warmed = true;
        Gyromancy.LOGGER.debug("[Gyromancy] Warmed {} Photon FX definitions", loaded);
    }
}
