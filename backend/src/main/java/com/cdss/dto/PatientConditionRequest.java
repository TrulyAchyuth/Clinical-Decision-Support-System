package com.cdss.dto;

import java.time.LocalDate;

import com.cdss.entity.ConditionStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/** Adds a knowledge-base disease to a patient's history. Status defaults to ACTIVE. */
public record PatientConditionRequest(
		@NotNull Long diseaseId,
		ConditionStatus status,
		@PastOrPresent LocalDate onsetDate,
		@Size(max = 255) String notes) {
}
