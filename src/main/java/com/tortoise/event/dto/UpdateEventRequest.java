package com.tortoise.event.dto;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * PATCH body: every field is optional, and null means "leave unchanged".
 */
public record UpdateEventRequest(
        @Size(max = 120) String name,
        @Size(max = 2000) String description,
        @Size(max = 200) String venue,
        @Min(1) @Max(200_000) Integer totalSeats,
        Instant registrationOpensAt,
        Instant registrationClosesAt,
        Instant eventStartsAt,
        Instant eventEndsAt) {

    public boolean changesMoreThanDescription() {
        return name != null || venue != null || totalSeats != null
                || registrationOpensAt != null || registrationClosesAt != null
                || eventStartsAt != null || eventEndsAt != null;
    }
}
