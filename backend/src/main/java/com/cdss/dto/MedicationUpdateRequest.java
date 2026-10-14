package com.cdss.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.cdss.entity.MedicationRoute;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The fields of an ACTIVE medication that may be changed. The drug cannot be
 * changed (record a new medication instead), so the safety check stays valid.
 */
public record MedicationUpdateRequest(
		@NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal dose,
		@NotBlank @Size(max = 20) String doseUnit,
		@NotNull MedicationRoute route,
		@NotBlank @Size(max = 100) String frequency,
		@NotNull LocalDate startDate,
		LocalDate endDate,
		@Size(max = 255) String notes) {
}
