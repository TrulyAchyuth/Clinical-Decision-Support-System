package com.cdss.dto;

import com.cdss.entity.Disease;

public record DiseaseResponse(Long id, String name, String icd10Code) {

	public static DiseaseResponse from(Disease disease) {
		return new DiseaseResponse(disease.getId(), disease.getName(), disease.getIcd10Code());
	}

}
