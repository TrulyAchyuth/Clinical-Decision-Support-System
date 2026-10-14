package com.cdss.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * One set of vital signs. Each value is optional, but at least one is needed.
 * Values outside these plausibility limits are rejected (400) rather than
 * stored, so an obviously wrong entry can never reach the rules:
 * blood pressure 40-300 / 20-200 mmHg, temperature 25.0-45.0 °C,
 * heart rate 20-300 beats/min, respiratory rate 4-80 breaths/min,
 * oxygen saturation 50-100 %.
 */
public record VitalSignsRequest(
		@NotNull @PastOrPresent Instant recordedAt,
		@Min(40) @Max(300) Integer systolicBp,
		@Min(20) @Max(200) Integer diastolicBp,
		@DecimalMin("25.0") @DecimalMax("45.0") @Digits(integer = 2, fraction = 1) BigDecimal temperature,
		@Min(20) @Max(300) Integer heartRate,
		@Min(4) @Max(80) Integer respiratoryRate,
		@Min(50) @Max(100) Integer oxygenSaturation,
		@Size(max = 255) String notes) {

	@AssertTrue(message = "at least one vital sign must be provided")
	public boolean isAnyMeasurementPresent() {
		return systolicBp != null || diastolicBp != null || temperature != null || heartRate != null
				|| respiratoryRate != null || oxygenSaturation != null;
	}

	@AssertTrue(message = "systolicBp and diastolicBp must be recorded together")
	public boolean isBloodPressureComplete() {
		return (systolicBp == null) == (diastolicBp == null);
	}

	@AssertTrue(message = "systolicBp must be higher than diastolicBp")
	public boolean isBloodPressureConsistent() {
		return systolicBp == null || diastolicBp == null || systolicBp > diastolicBp;
	}

}
