package com.cdss.dto;

import java.time.LocalDate;

import com.cdss.entity.Gender;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body for creating or updating a patient. Contact fields are optional. */
public record PatientRequest(
		@NotBlank @Size(max = 150) String fullName,
		@NotNull @PastOrPresent LocalDate dateOfBirth,
		@NotNull Gender gender,
		@Size(max = 30) @Pattern(regexp = "^$|^[0-9+()\\-. ]{7,30}$",
				message = "must be 7-30 characters of digits, spaces or + ( ) - .") String phone,
		@Size(max = 254) @Email String email,
		@Size(max = 255) String address) {
}
