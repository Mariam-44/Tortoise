package com.tortoise.event;

/**
 * Thrown by the repository when a save would break the "one active event per name + venue + day" rule.
 * Storage-level, like a database constraint violation: the service turns it into an API error.
 */
public class DuplicateEventException extends RuntimeException {

    private final long existingId;

    public DuplicateEventException(long existingId) {
        super("Event " + existingId + " already holds this name, venue and day");
        this.existingId = existingId;
    }

    public long existingId() {
        return existingId;
    }
}
