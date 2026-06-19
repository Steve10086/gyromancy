package com.astune.gyromancy.element.event;

import com.astune.gyromancy.api.element.ElementType;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Lightweight internal event bus for the element concentration system.
 *
 * <p>Independent of NeoForge's EventBus — designed for high-frequency element events
 * where overhead must be minimal. Listeners are indexed by element type for fast dispatch.</p>
 *
 * <p>Pseudo-entities and magic array effects subscribe to this bus to react to
 * elemental changes in their vicinity.</p>
 */
public final class ElementEventBus {

    private ElementEventBus() {}

    // ── Threshold listeners: keyed by element type ──
    private static final Map<ElementType, List<ThresholdEntry>> thresholdListeners = new EnumMap<>(ElementType.class);

    // ── Change listeners: keyed by element type ──
    private static final Map<ElementType, List<ChangeEntry>> changeListeners = new EnumMap<>(ElementType.class);

    // ── Lifecycle listeners (global) ──
    private static final List<BiConsumer<ServerLevel, ElementActivatedEvent>> activationListeners = new CopyOnWriteArrayList<>();
    private static final List<BiConsumer<ServerLevel, ElementCleanedUpEvent>> cleanupListeners = new CopyOnWriteArrayList<>();

    static {
        for (ElementType type : ElementType.values()) {
            thresholdListeners.put(type, new CopyOnWriteArrayList<>());
            changeListeners.put(type, new CopyOnWriteArrayList<>());
        }
    }

    // ── Registration ──

    /**
     * Registers a threshold listener. Fires when the element value crosses the given threshold.
     *
     * @param element   the element type to monitor
     * @param threshold the value threshold
     * @param direction the crossing direction to trigger on
     * @param listener  callback receiving the server level and event
     * @return a subscription handle for later unsubscription
     */
    public static ElementEventSubscription registerThresholdListener(
            ElementType element,
            long threshold,
            ThresholdDirection direction,
            BiConsumer<ServerLevel, ElementThresholdEvent> listener) {

        ThresholdEntry entry = new ThresholdEntry(threshold, direction, listener);
        thresholdListeners.get(element).add(entry);

        return new SimpleSubscription(() -> thresholdListeners.get(element).remove(entry));
    }

    /**
     * Registers a change listener. Fires when the element value change exceeds minDelta.
     *
     * @param element  the element type to monitor
     * @param minDelta minimum absolute change to trigger
     * @param listener callback receiving the server level and event
     * @return a subscription handle for later unsubscription
     */
    public static ElementEventSubscription registerChangeListener(
            ElementType element,
            long minDelta,
            BiConsumer<ServerLevel, ElementChangeEvent> listener) {

        ChangeEntry entry = new ChangeEntry(minDelta, listener);
        changeListeners.get(element).add(entry);

        return new SimpleSubscription(() -> changeListeners.get(element).remove(entry));
    }

    /**
     * Registers a lifecycle listener for coordinate activation (first override written).
     */
    public static ElementEventSubscription registerActivationListener(
            BiConsumer<ServerLevel, ElementActivatedEvent> listener) {
        activationListeners.add(listener);
        return new SimpleSubscription(() -> activationListeners.remove(listener));
    }

    /**
     * Registers a lifecycle listener for coordinate cleanup (override removed).
     */
    public static ElementEventSubscription registerCleanupListener(
            BiConsumer<ServerLevel, ElementCleanedUpEvent> listener) {
        cleanupListeners.add(listener);
        return new SimpleSubscription(() -> cleanupListeners.remove(listener));
    }

    // ── Fire methods (called from ElementChunkProcessor) ──

    /** Fires threshold events if any thresholds were crossed. */
    public static void checkAndFireThreshold(ServerLevel level, ElementThresholdEvent event) {
        for (ThresholdEntry entry : thresholdListeners.get(event.element())) {
            if (entry.matches(event)) {
                entry.listener.accept(level, event);
            }
        }
    }

    /** Fires change events if the change exceeds any registered minimum. */
    public static void checkAndFireChange(ServerLevel level, ElementChangeEvent event) {
        for (ChangeEntry entry : changeListeners.get(event.element())) {
            if (Math.abs(event.delta()) >= entry.minDelta) {
                entry.listener.accept(level, event);
            }
        }
    }

    /** Fires activation event. */
    public static void fireActivation(ServerLevel level, ElementActivatedEvent event) {
        for (var listener : activationListeners) {
            listener.accept(level, event);
        }
    }

    /** Fires cleanup event. */
    public static void fireCleanup(ServerLevel level, ElementCleanedUpEvent event) {
        for (var listener : cleanupListeners) {
            listener.accept(level, event);
        }
    }

    // ── Internal types ──

    private record ThresholdEntry(
            long threshold,
            ThresholdDirection direction,
            BiConsumer<ServerLevel, ElementThresholdEvent> listener
    ) {
        boolean matches(ElementThresholdEvent event) {
            if (direction == ThresholdDirection.RISING_ABOVE) {
                return event.oldValue() <= threshold && event.newValue() > threshold;
            } else {
                return event.oldValue() >= threshold && event.newValue() < threshold;
            }
        }
    }

    private record ChangeEntry(
            long minDelta,
            BiConsumer<ServerLevel, ElementChangeEvent> listener
    ) {}

    private static final class SimpleSubscription implements ElementEventSubscription {
        private final Runnable unsubscribeAction;
        private volatile boolean active = true;

        SimpleSubscription(Runnable unsubscribeAction) {
            this.unsubscribeAction = unsubscribeAction;
        }

        @Override
        public void unsubscribe() {
            if (active) {
                active = false;
                unsubscribeAction.run();
            }
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }
}
