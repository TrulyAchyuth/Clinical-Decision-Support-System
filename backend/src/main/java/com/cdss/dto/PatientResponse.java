package com.cdss.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.cdss.entity.Gender;
import com.cdss.entity.Patient;

public record PatientResponse(Long id, String fullName, LocalDate dateOfBirth, Gender gender, String phone,
		String email, String address, Instant createdAt, Instant updatedAt) {

	public static PatientResponse from(Patient patient) {
		return new PatientResponse(patient.getId(), patient.getFullName(), patient.getDateOfBirth(),
				patient.getGender(), patient.getPhone(), patient.getEmail(), patient.getAddress(),
				patient.getCreatedAt(), patient.getUpdatedAt());
	}

}
