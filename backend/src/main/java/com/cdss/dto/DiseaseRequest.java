package com.cdss.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DiseaseRequest(
		@NotBlank @Size(max = 200) String name,
		// Letter, two characters, optional "." and up to four more, e.g. I10, E11.9, J45.909
		@NotBlank @Pattern(regexp = "^[A-Za-z][0-9][0-9A-Za-z](\\.[0-9A-Za-z]{1,4})?$",
				message = "must be an ICD-10 code such as I10 or E11.9") String icd10Code) {
}
