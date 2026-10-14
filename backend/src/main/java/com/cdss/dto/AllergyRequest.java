package com.cdss.dto;

import java.time.LocalDate;

import com.cdss.entity.AllergyCategory;
import com.cdss.entity.AllergySeverity;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * Records an allergy. For category DRUG give {@code drugId} (a knowledge-base
 * drug); for DRUG_CATEGORY give a knowledge-base drug category as
 * {@code allergen}; for FOOD, ENVIRONMENTAL and OTHER give {@code allergen}
 * as free text.
 */
public record AllergyRequest(
		@NotNull AllergyCategory category,
		Long drugId,
		@Size(max = 150) String allergen,
		@Size(max = 255) String reaction,
		@NotNull AllergySeverity severity,
		@NotNull @PastOrPresent LocalDate recordedDate,
		@Size(max = 255) String notes) {
}
