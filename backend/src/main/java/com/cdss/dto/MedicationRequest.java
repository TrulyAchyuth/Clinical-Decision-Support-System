package com.cdss.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.cdss.entity.MedicationRoute;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Adds a medication for a patient. The safety check runs again when it is
 * saved: every warning that needs review must be listed (by its {@code code})
 * in {@code acknowledgedWarnings}, with an {@code overrideReason}, otherwise
 * the request is refused with 409.
 */
public record MedicationRequest(
		@NotNull Long drugId,
		@NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal dose,
		@NotBlank @Size(max = 20) String doseUnit,
		@NotNull MedicationRoute route,
		@NotBlank @Size(max = 100) String frequency,
		@NotNull LocalDate startDate,
		LocalDate endDate,
		@Size(max = 255) String notes,
		@Size(max = 20) List<@NotBlank @Size(max = 100) String> acknowledgedWarnings,
		@Size(max = 500) String overrideReason) {
}
