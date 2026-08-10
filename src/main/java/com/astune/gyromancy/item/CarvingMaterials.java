package com.astune.gyromancy.item;

import com.astune.gyromancy.api.canvas.Carvable;
import net.minecraft.world.item.Item;

public abstract class CarvingMaterials extends Item implements Carvable {
    public CarvingMaterials(Properties properties) {
        super(properties);
    }
}
