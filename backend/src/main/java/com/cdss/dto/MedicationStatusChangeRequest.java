package com.cdss.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;

/**
 * Discontinues or completes a medication. {@code reason} is required to
 * discontinue. {@code endDate} defaults to today.
 */
public record MedicationStatusChangeRequest(@Size(max = 255) String reason, LocalDate endDate) {
}
