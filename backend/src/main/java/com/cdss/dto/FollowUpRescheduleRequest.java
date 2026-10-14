package com.cdss.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

/** Moves a SCHEDULED follow-up to a new time in the future. */
public record FollowUpRescheduleRequest(@NotNull Instant scheduledAt) {
}
