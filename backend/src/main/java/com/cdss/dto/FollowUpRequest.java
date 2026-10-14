package com.cdss.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Schedules a follow-up. {@code scheduledAt} (ISO-8601 instant) must be in the future. */
public record FollowUpRequest(
		@NotNull Instant scheduledAt,
		@NotBlank @Size(max = 200) String reason,
		@Size(max = 500) String notes) {
}
