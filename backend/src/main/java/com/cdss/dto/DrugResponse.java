package com.cdss.dto;

import com.cdss.entity.Drug;

public record DrugResponse(Long id, String name, String dosage, String category, String notes) {

	public static DrugResponse from(Drug drug) {
		return new DrugResponse(drug.getId(), drug.getName(), drug.getDosage(), drug.getCategory(), drug.getNotes());
	}

}
