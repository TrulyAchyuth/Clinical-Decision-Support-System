package com.cdss.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.cdss.entity.VitalSigns;

public record VitalSignsResponse(Long id, Long patientId, Instant recordedAt, Integer systolicBp, Integer diastolicBp,
		BigDecimal temperature, Integer heartRate, Integer respiratoryRate, Integer oxygenSaturation, String notes,
		Instant createdAt) {

	public static VitalSignsResponse from(VitalSigns vitals) {
		return new VitalSignsResponse(vitals.getId(), vitals.getPatient().getId(), vitals.getRecordedAt(),
				vitals.getSystolicBp(), vitals.getDiastolicBp(), vitals.getTemperature(), vitals.getHeartRate(),
				vitals.getRespiratoryRate(), vitals.getOxygenSaturation(), vitals.getNotes(), vitals.getCreatedAt());
	}

}
