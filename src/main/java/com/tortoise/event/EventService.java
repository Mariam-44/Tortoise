package com.tortoise.event;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tortoise.common.PageResponse;
import com.tortoise.common.error.ApiError;
import com.tortoise.common.error.ApiException;
import com.tortoise.common.error.ErrorCode;
import com.tortoise.event.dto.CreateEventRequest;
import com.tortoise.event.dto.EventResponse;
import com.tortoise.event.dto.UpdateEventRequest;

@Service
public class EventService {

    /** The draw and confirmations need time between registration closing and the event. */
    static final Duration MIN_GAP_BEFORE_EVENT = Duration.ofHours(24);
    static final int MAX_PAGE_SIZE = 100;

    private final EventRepository repository;
    private final Clock clock;

    public EventService(EventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public EventResponse create(CreateEventRequest request) {
        Instant now = clock.instant();

        Event event = new Event();
        event.setName(request.name().strip());
        event.setDescription(stripToNull(request.description()));
        event.setVenue(request.venue().strip());
        event.setTotalSeats(request.totalSeats());
        event.setRegistrationOpensAt(request.registrationOpensAt());
        event.setRegistrationClosesAt(request.registrationClosesAt());
        event.setEventStartsAt(request.eventStartsAt());
        event.setEventEndsAt(request.eventEndsAt());

        validateRules(event, now);

        event.setStatus(EventStatus.DRAFT);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        return EventResponse.from(store(event), now);
    }

    public PageResponse<EventResponse> list(LocalDate from, LocalDate to, EventStatus status, int page, int size) {
        List<ApiError.FieldError> errors = new ArrayList<>();
        if (page < 0) {
            errors.add(new ApiError.FieldError("page", "must be 0 or greater"));
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            errors.add(new ApiError.FieldError("size", "must be between 1 and " + MAX_PAGE_SIZE));
        }
        if (from != null && to != null && from.isAfter(to)) {
            errors.add(new ApiError.FieldError("to", "must be on or after 'from'"));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request has invalid parameters", errors);
        }

        Instant now = clock.instant();
        List<EventResponse> matching = repository.findAll().stream()
                .filter(e -> status == null || e.getStatus() == status)
                .filter(e -> from == null || !eventDay(e).isBefore(from))
                .filter(e -> to == null || !eventDay(e).isAfter(to))
                .sorted(Comparator.comparing(Event::getEventStartsAt).thenComparing(Event::getId))
                .map(e -> EventResponse.from(e, now))
                .toList();
        return PageResponse.of(matching, page, size);
    }

    public EventResponse get(Long id) {
        return EventResponse.from(findOrThrow(id), clock.instant());
    }

    public EventResponse update(Long id, UpdateEventRequest request) {
        Instant now = clock.instant();
        Event updated = findOrThrow(id).copy();

        switch (updated.getStatus()) {
            case CANCELLED -> throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "A cancelled event can't be changed");
            case PUBLISHED -> {
                if (request.changesMoreThanDescription()) {
                    throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                            "Only the description can change after an event is published");
                }
                if (request.description() != null) {
                    updated.setDescription(stripToNull(request.description()));
                }
            }
            case DRAFT -> {
                if (request.name() != null) updated.setName(request.name().strip());
                if (request.description() != null) updated.setDescription(stripToNull(request.description()));
                if (request.venue() != null) updated.setVenue(request.venue().strip());
                if (request.totalSeats() != null) updated.setTotalSeats(request.totalSeats());
                if (request.registrationOpensAt() != null) updated.setRegistrationOpensAt(request.registrationOpensAt());
                if (request.registrationClosesAt() != null) updated.setRegistrationClosesAt(request.registrationClosesAt());
                if (request.eventStartsAt() != null) updated.setEventStartsAt(request.eventStartsAt());
                if (request.eventEndsAt() != null) updated.setEventEndsAt(request.eventEndsAt());
                validateRules(updated, now);
            }
        }

        updated.setUpdatedAt(now);
        // A DRAFT edit can move the event onto another event's name, venue or day: store() catches that.
        return EventResponse.from(store(updated), now);
    }

    public EventResponse publish(Long id) {
        Instant now = clock.instant();
        Event event = findOrThrow(id).copy();
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only a DRAFT event can be published (current status: " + event.getStatus() + ")");
        }
        // Dates were valid at creation, but time has passed since then.
        validateRules(event, now);
        event.setStatus(EventStatus.PUBLISHED);
        event.setUpdatedAt(now);
        return EventResponse.from(store(event), now);
    }

    public EventResponse cancel(Long id) {
        Instant now = clock.instant();
        Event event = findOrThrow(id).copy();
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "Event is already cancelled");
        }
        event.setStatus(EventStatus.CANCELLED);
        event.setUpdatedAt(now);
        return EventResponse.from(store(event), now);
    }

    private Event findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.EVENT_NOT_FOUND, "Event " + id + " doesn't exist"));
    }

    /**
     * The duplicate check lives in the repository, inside the same atomic step as the write.
     * Checking here first and saving afterwards would leave a gap for a parallel request.
     */
    private Event store(Event event) {
        try {
            return repository.save(event);
        } catch (DuplicateEventException ex) {
            throw new ApiException(ErrorCode.DUPLICATE_EVENT,
                    "An event with this name and venue already exists on that day (id " + ex.existingId() + ")");
        }
    }

    private void validateRules(Event event, Instant now) {
        List<ApiError.FieldError> errors = new ArrayList<>();

        if (event.getName().length() < 3) {
            errors.add(new ApiError.FieldError("name", "must be at least 3 characters"));
        }
        if (event.getVenue().isEmpty()) {
            errors.add(new ApiError.FieldError("venue", "must not be blank"));
        }

        requireFuture("registrationOpensAt", event.getRegistrationOpensAt(), now, errors);
        requireFuture("registrationClosesAt", event.getRegistrationClosesAt(), now, errors);
        requireFuture("eventStartsAt", event.getEventStartsAt(), now, errors);
        requireFuture("eventEndsAt", event.getEventEndsAt(), now, errors);

        if (!event.getRegistrationOpensAt().isBefore(event.getRegistrationClosesAt())) {
            errors.add(new ApiError.FieldError("registrationClosesAt", "must be after registrationOpensAt"));
        }
        if (event.getRegistrationClosesAt().plus(MIN_GAP_BEFORE_EVENT).isAfter(event.getEventStartsAt())) {
            errors.add(new ApiError.FieldError("registrationClosesAt",
                    "must be at least " + MIN_GAP_BEFORE_EVENT.toHours() + " hours before eventStartsAt"));
        }
        if (!event.getEventStartsAt().isBefore(event.getEventEndsAt())) {
            errors.add(new ApiError.FieldError("eventEndsAt", "must be after eventStartsAt"));
        }

        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Event breaks one or more rules", errors);
        }
    }

    private static void requireFuture(String field, Instant value, Instant now, List<ApiError.FieldError> errors) {
        if (!value.isAfter(now)) {
            errors.add(new ApiError.FieldError(field, "must be in the future"));
        }
    }

    private static LocalDate eventDay(Event event) {
        return event.getEventStartsAt().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static String stripToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
