package com.astune.gyromancy.element.event;

/**
 * Handle for a subscription to the element event bus.
 * Allows listeners to unsubscribe when no longer needed.
 */
public interface ElementEventSubscription {

    /** Removes this subscription from the event bus. Safe to call multiple times. */
    void unsubscribe();

    /** Returns true if this subscription is still active. */
    boolean isActive();
}
