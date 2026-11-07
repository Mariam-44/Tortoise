package com.tortoise.event.dto;

import java.time.Instant;

import com.tortoise.event.Event;
import com.tortoise.event.EventStatus;

public record EventResponse(
        Long id,
        String name,
        String description,
        String venue,
        int totalSeats,
        Instant registrationOpensAt,
        Instant registrationClosesAt,
        Instant eventStartsAt,
        Instant eventEndsAt,
        EventStatus status,
        boolean registrationOpen,
        Instant createdAt,
        Instant updatedAt) {

    public static EventResponse from(Event event, Instant now) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getDescription(),
                event.getVenue(),
                event.getTotalSeats(),
                event.getRegistrationOpensAt(),
                event.getRegistrationClosesAt(),
                event.getEventStartsAt(),
                event.getEventEndsAt(),
                event.getStatus(),
                event.isRegistrationOpen(now),
                event.getCreatedAt(),
                event.getUpdatedAt());
    }
}
