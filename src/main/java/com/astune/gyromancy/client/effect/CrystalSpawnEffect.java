package com.astune.gyromancy.client.effect;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementType;
import com.lowdragmc.photon.client.fx.BlockEffectExecutor;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Plays the one-shot {@code gyromancy:crystal_spawn} effect where a crystal
 * appears, tinted with the spawned crystal's element color.
 */
@OnlyIn(Dist.CLIENT)
public final class CrystalSpawnEffect extends BlockEffectExecutor {
    private static final ResourceLocation FX_ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "crystal_spawn");

    private final DynamicEffectProperties properties = new DynamicEffectProperties();

    private CrystalSpawnEffect(FX fx, Level level, BlockPos pos, int argb) {
        super(fx, level, pos);
        properties.setColor(argb);
        // Spawn bursts are independent: never dedup them and let remnants drop
        // immediately if the anchor disappears.
        setAllowMulti(true);
        setForcedDeath(true);
    }

    /** Plays the spawn effect; silently skips when the definition is missing. */
    public static void play(Level level, BlockPos pos, ElementType element) {
        FX fx = FXHelper.getFX(FX_ID);
        if (fx == null) return;
        new CrystalSpawnEffect(fx, level, pos, element.color()).start();
    }

    @Override
    public void start() {
        super.start();
        applyElementTint();
    }

    /**
     * The spawn notice can reach the client one tick before the crystal block
     * itself, so {@link BlockEffectExecutor}'s anchor check would tear the
     * effect down instantly. This one-shot effect only retires once its
     * runtime finished.
     */
    @Override
    public void updateFXObjectTick(IFXObject fxObject) {
        FXRuntime runtime = getRuntime();
        if (runtime != null && fxObject == runtime.getRoot() && runtimeEnded()) {
            retire(CACHE, pos);
        }
    }

    @Override
    public void updateFXObjectFrame(IFXObject fxObject, float partialTicks) {
        FXRuntime runtime = getRuntime();
        if (runtime != null && fxObject == runtime.getRoot()) {
            applyElementTint();
        }
    }

    private void applyElementTint() {
        FXRuntime runtime = getRuntime();
        if (runtime != null) properties.apply(runtime);
    }
}
