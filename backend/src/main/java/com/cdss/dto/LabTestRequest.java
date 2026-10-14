package com.cdss.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Reference range is numeric: {@code referenceRangeLow} to {@code referenceRangeHigh}, in {@code unit}. */
public record LabTestRequest(
		@NotBlank @Size(max = 150) String name,
		@NotNull @Digits(integer = 8, fraction = 2) BigDecimal referenceRangeLow,
		@NotNull @Digits(integer = 8, fraction = 2) BigDecimal referenceRangeHigh,
		@NotBlank @Size(max = 30) String unit,
		@Size(max = 255) String notes) {

	@AssertTrue(message = "referenceRangeLow must not be greater than referenceRangeHigh")
	public boolean isReferenceRangeValid() {
		return referenceRangeLow == null || referenceRangeHigh == null
				|| referenceRangeLow.compareTo(referenceRangeHigh) <= 0;
	}

}
