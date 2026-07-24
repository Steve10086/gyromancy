package com.astune.gyromancy.array.runtime.emit;

import net.minecraft.resources.ResourceLocation;

public record EmittedObject(ResourceLocation emitType, Object ref) {
}
