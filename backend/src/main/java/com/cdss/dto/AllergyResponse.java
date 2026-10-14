package com.cdss.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.cdss.entity.AllergyCategory;
import com.cdss.entity.AllergyRecordStatus;
import com.cdss.entity.AllergySeverity;
import com.cdss.entity.PatientAllergy;

public record AllergyResponse(Long id, Long patientId, AllergyCategory category, Long drugId, String allergen,
		String reaction, AllergySeverity severity, AllergyRecordStatus status, LocalDate recordedDate, String notes,
		String resolutionReason, Instant resolvedAt, String resolvedBy, String recordedBy, Instant createdAt) {

	public static AllergyResponse from(PatientAllergy allergy) {
		return new AllergyResponse(allergy.getId(), allergy.getPatient().getId(), allergy.getCategory(),
				allergy.getDrug() == null ? null : allergy.getDrug().getId(), allergy.getAllergen(),
				allergy.getReaction(), allergy.getSeverity(), allergy.getStatus(), allergy.getRecordedDate(),
				allergy.getNotes(), allergy.getResolutionReason(), allergy.getResolvedAt(), allergy.getResolvedBy(),
				allergy.getRecordedBy(), allergy.getCreatedAt());
	}

}
