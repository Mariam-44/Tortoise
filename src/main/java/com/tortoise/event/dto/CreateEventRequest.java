package com.tortoise.event.dto;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Single-field rules live here as annotations. Cross-field date rules live in {@code EventService}.
 */
public record CreateEventRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 200) String venue,
        @NotNull @Min(1) @Max(200_000) Integer totalSeats,
        @NotNull Instant registrationOpensAt,
        @NotNull Instant registrationClosesAt,
        @NotNull Instant eventStartsAt,
        @NotNull Instant eventEndsAt) {
}
