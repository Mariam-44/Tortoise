package com.tortoise.event;

import java.time.Instant;

/**
 * Internal model. Never returned from the API directly: controllers map it to {@code EventResponse}.
 */
public class Event {

    private Long id;
    private String name;
    private String description;
    private String venue;
    private int totalSeats;
    private Instant registrationOpensAt;
    private Instant registrationClosesAt;
    private Instant eventStartsAt;
    private Instant eventEndsAt;
    private EventStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public boolean isRegistrationOpen(Instant now) {
        return status == EventStatus.PUBLISHED
                && !now.isBefore(registrationOpensAt)
                && now.isBefore(registrationClosesAt);
    }

    /** A detached copy, so changes can be validated before they touch the stored event. */
    public Event copy() {
        Event copy = new Event();
        copy.id = id;
        copy.name = name;
        copy.description = description;
        copy.venue = venue;
        copy.totalSeats = totalSeats;
        copy.registrationOpensAt = registrationOpensAt;
        copy.registrationClosesAt = registrationClosesAt;
        copy.eventStartsAt = eventStartsAt;
        copy.eventEndsAt = eventEndsAt;
        copy.status = status;
        copy.createdAt = createdAt;
        copy.updatedAt = updatedAt;
        return copy;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public int getTotalSeats() { return totalSeats; }
    public void setTotalSeats(int totalSeats) { this.totalSeats = totalSeats; }

    public Instant getRegistrationOpensAt() { return registrationOpensAt; }
    public void setRegistrationOpensAt(Instant registrationOpensAt) { this.registrationOpensAt = registrationOpensAt; }

    public Instant getRegistrationClosesAt() { return registrationClosesAt; }
    public void setRegistrationClosesAt(Instant registrationClosesAt) { this.registrationClosesAt = registrationClosesAt; }

    public Instant getEventStartsAt() { return eventStartsAt; }
    public void setEventStartsAt(Instant eventStartsAt) { this.eventStartsAt = eventStartsAt; }

    public Instant getEventEndsAt() { return eventEndsAt; }
    public void setEventEndsAt(Instant eventEndsAt) { this.eventEndsAt = eventEndsAt; }

    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
