package com.cdss.dto;

import java.time.LocalDate;

import com.cdss.entity.AllergySeverity;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/** The fields of an ACTIVE allergy that may be changed. The allergen itself cannot be changed. */
public record AllergyUpdateRequest(
		@Size(max = 255) String reaction,
		@NotNull AllergySeverity severity,
		@NotNull @PastOrPresent LocalDate recordedDate,
		@Size(max = 255) String notes) {
}
