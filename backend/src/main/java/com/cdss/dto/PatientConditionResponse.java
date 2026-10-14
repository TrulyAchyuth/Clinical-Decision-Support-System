package com.cdss.dto;

import java.time.LocalDate;

import com.cdss.entity.ConditionStatus;
import com.cdss.entity.PatientCondition;

public record PatientConditionResponse(Long id, Long patientId, Long diseaseId, String diseaseName, String icd10Code,
		ConditionStatus status, LocalDate onsetDate, String notes) {

	public static PatientConditionResponse from(PatientCondition condition) {
		return new PatientConditionResponse(condition.getId(), condition.getPatient().getId(),
				condition.getDisease().getId(), condition.getDisease().getName(), condition.getDisease().getIcd10Code(),
				condition.getStatus(), condition.getOnsetDate(), condition.getNotes());
	}

}
