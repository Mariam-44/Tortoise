package com.tortoise.event;

import java.util.List;
import java.util.Optional;

/**
 * Storage abstraction. Level 1 uses an in-memory implementation; Level 2 swaps in JPA
 * without touching the service or controller.
 */
public interface EventRepository {

    /**
     * Inserts or updates an event. Active (non-cancelled) events are unique by name + venue + UTC day,
     * ignoring case. The uniqueness check and the write happen as one atomic step, the same way a
     * database unique constraint works.
     *
     * @throws DuplicateEventException if another active event already holds that name, venue and day
     */
    Event save(Event event);

    Optional<Event> findById(Long id);

    List<Event> findAll();
}
