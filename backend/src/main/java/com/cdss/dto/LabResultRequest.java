package com.cdss.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * A lab result for one of the knowledge-base lab tests. {@code unit} is
 * optional; if given it must match the lab test's unit (no conversions).
 */
public record LabResultRequest(
		@NotNull Long labTestId,
		@NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal value,
		@Size(max = 30) String unit,
		@NotNull @PastOrPresent Instant measuredAt,
		@Size(max = 255) String notes) {
}
