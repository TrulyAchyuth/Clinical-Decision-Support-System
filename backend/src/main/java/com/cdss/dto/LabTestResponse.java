package com.cdss.dto;

import java.math.BigDecimal;

import com.cdss.entity.LabTest;

public record LabTestResponse(Long id, String name, BigDecimal referenceRangeLow, BigDecimal referenceRangeHigh,
		String unit, String notes) {

	public static LabTestResponse from(LabTest labTest) {
		return new LabTestResponse(labTest.getId(), labTest.getName(), labTest.getReferenceRangeLow(),
				labTest.getReferenceRangeHigh(), labTest.getUnit(), labTest.getNotes());
	}

}
