package com.tortoise.event;

/**
 * Lifecycle of an event. More states (DRAWN, COMPLETED) arrive with the draw in later levels.
 * Whether registration is currently open is computed from the time window, not stored here.
 */
public enum EventStatus {
    DRAFT,
    PUBLISHED,
    CANCELLED
}
