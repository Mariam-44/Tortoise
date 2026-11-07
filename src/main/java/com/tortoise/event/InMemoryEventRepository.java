package com.tortoise.event;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryEventRepository implements EventRepository {

    private final ConcurrentMap<Long, Event> events = new ConcurrentHashMap<>();
    /** Our "unique index": each active slot (name + venue + day) points to the id of the event holding it. */
    private final ConcurrentMap<Slot, Long> activeSlots = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public Event save(Event event) {
        if (event.getId() == null) {
            event.setId(nextId.getAndIncrement());
        }
        Long id = event.getId();

        // compute() locks this id's entry, so two saves of the same event can't interleave.
        // If the lambda throws, nothing is stored.
        events.compute(id, (key, previous) -> {
            Slot oldSlot = Slot.of(previous);
            Slot newSlot = Slot.of(event);

            if (newSlot != null && !newSlot.equals(oldSlot)) {
                // Check and claim in ONE atomic call: only one thread can win a free slot.
                Long holder = activeSlots.putIfAbsent(newSlot, id);
                if (holder != null) {
                    throw new DuplicateEventException(holder);
                }
            }
            if (oldSlot != null && !oldSlot.equals(newSlot)) {
                activeSlots.remove(oldSlot, id);
            }
            return event;
        });
        return event;
    }

    @Override
    public Optional<Event> findById(Long id) {
        return Optional.ofNullable(events.get(id));
    }

    @Override
    public List<Event> findAll() {
        return new ArrayList<>(events.values());
    }

    private record Slot(String name, String venue, LocalDate day) {

        /** The slot an event occupies, or null if it occupies none (not stored yet, or cancelled). */
        static Slot of(Event event) {
            if (event == null || event.getStatus() == EventStatus.CANCELLED) {
                return null;
            }
            return new Slot(event.getName().toLowerCase(Locale.ROOT),
                    event.getVenue().toLowerCase(Locale.ROOT),
                    event.getEventStartsAt().atZone(ZoneOffset.UTC).toLocalDate());
        }
    }
}
