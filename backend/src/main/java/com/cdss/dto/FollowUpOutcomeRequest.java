package com.cdss.dto;

import jakarta.validation.constraints.Size;

/** Notes when completing a follow-up (optional), or the reason when cancelling one (required). */
public record FollowUpOutcomeRequest(@Size(max = 500) String notes) {
}
