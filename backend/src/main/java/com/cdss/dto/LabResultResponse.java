package com.cdss.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.cdss.entity.LabResult;
import com.cdss.entity.LabTest;

/**
 * A lab result. {@code flag} compares the value with the lab test's
 * (educational) reference range: LOW, NORMAL or HIGH.
 */
public record LabResultResponse(Long id, Long patientId, Long labTestId, String labTestName, BigDecimal value,
		String unit, Instant measuredAt, BigDecimal referenceRangeLow, BigDecimal referenceRangeHigh, String flag,
		String notes) {

	public static LabResultResponse from(LabResult result) {
		LabTest test = result.getLabTest();
		String flag = (result.getValue().compareTo(test.getReferenceRangeLow()) < 0) ? "LOW"
				: (result.getValue().compareTo(test.getReferenceRangeHigh()) > 0) ? "HIGH" : "NORMAL";
		return new LabResultResponse(result.getId(), result.getPatient().getId(), test.getId(), test.getName(),
				result.getValue(), result.getUnit(), result.getMeasuredAt(), test.getReferenceRangeLow(),
				test.getReferenceRangeHigh(), flag, result.getNotes());
	}

}
