package com.cdss.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.cdss.entity.MedicationRoute;
import com.cdss.entity.MedicationStatus;
import com.cdss.entity.PatientMedication;

public record MedicationResponse(Long id, Long patientId, Long drugId, String drugName, String drugStrength,
		String drugCategory, BigDecimal dose, String doseUnit, MedicationRoute route, String frequency,
		LocalDate startDate, LocalDate endDate, MedicationStatus status, String notes, String statusReason,
		Instant statusChangedAt, String statusChangedBy, List<String> safetyWarnings, String safetyOverrideReason,
		String recordedBy, Instant createdAt, Instant updatedAt) {

	public static MedicationResponse from(PatientMedication medication) {
		return new MedicationResponse(medication.getId(), medication.getPatient().getId(),
				medication.getDrug().getId(), medication.getDrug().getName(), medication.getDrug().getDosage(),
				medication.getDrug().getCategory(), medication.getDose(), medication.getDoseUnit(),
				medication.getRoute(), medication.getFrequency(), medication.getStartDate(), medication.getEndDate(),
				medication.getStatus(), medication.getNotes(), medication.getStatusReason(),
				medication.getStatusChangedAt(), medication.getStatusChangedBy(),
				List.copyOf(medication.getSafetyWarnings()), medication.getSafetyOverrideReason(),
				medication.getRecordedBy(), medication.getCreatedAt(), medication.getUpdatedAt());
	}

}
