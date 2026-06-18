package com.astune.gyromancy.element;

/**
 * Interface for element tick processing strategies.
 * Allows swapping between direct integration, threaded, or forked implementations.
 */
public interface IElementTickProcessor {

    /** Called each server tick to process element concentrations. */
    void onServerTick(net.minecraft.server.level.ServerLevel level);

    /** Called when the server starts to initialize the processor. */
    default void onServerStart(net.minecraft.server.MinecraftServer server) {}

    /** Called when the server stops to clean up resources. */
    default void onServerStop(net.minecraft.server.MinecraftServer server) {}
}
