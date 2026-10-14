package com.cdss.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DrugRequest(
		@NotBlank @Size(max = 150) String name,
		@NotBlank @Size(max = 100) String dosage,
		@NotBlank @Size(max = 100) String category,
		@Size(max = 255) String notes) {
}
