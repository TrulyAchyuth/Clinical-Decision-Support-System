package com.cdss.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cdss.dto.DiseaseRequest;
import com.cdss.dto.DiseaseResponse;
import com.cdss.entity.Disease;
import com.cdss.exception.DuplicateResourceException;
import com.cdss.exception.ResourceInUseException;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.repository.DiseaseRepository;
import com.cdss.repository.PatientConditionRepository;

@Service
@Transactional
public class DiseaseService {

	private final DiseaseRepository diseaseRepository;

	private final PatientConditionRepository conditionRepository;

	public DiseaseService(DiseaseRepository diseaseRepository, PatientConditionRepository conditionRepository) {
		this.diseaseRepository = diseaseRepository;
		this.conditionRepository = conditionRepository;
	}

	@Transactional(readOnly = true)
	public List<DiseaseResponse> findAll() {
		return diseaseRepository.findAllByOrderByNameAsc().stream().map(DiseaseResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public DiseaseResponse findById(Long id) {
		return DiseaseResponse.from(getDisease(id));
	}

	public DiseaseResponse create(DiseaseRequest request) {
		String code = normalizeCode(request.icd10Code());
		if (diseaseRepository.existsByIcd10CodeIgnoreCase(code)) {
			throw duplicate(code);
		}
		Disease disease = new Disease();
		copy(request, disease);
		return DiseaseResponse.from(diseaseRepository.save(disease));
	}

	public DiseaseResponse update(Long id, DiseaseRequest request) {
		Disease disease = getDisease(id);
		String code = normalizeCode(request.icd10Code());
		if (diseaseRepository.existsByIcd10CodeIgnoreCaseAndIdNot(code, id)) {
			throw duplicate(code);
		}
		copy(request, disease);
		return DiseaseResponse.from(diseaseRepository.saveAndFlush(disease));
	}

	/** Refused (409) while a patient's history still refers to this disease. */
	public void delete(Long id) {
		Disease disease = getDisease(id);
		if (conditionRepository.existsByDiseaseId(id)) {
			throw new ResourceInUseException("Disease " + disease.getIcd10Code()
					+ " is recorded in patient histories and cannot be deleted");
		}
		diseaseRepository.delete(disease);
	}

	private Disease getDisease(Long id) {
		return diseaseRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Disease", id));
	}

	/** ICD-10 codes are stored in upper case, e.g. "e11.9" becomes "E11.9". */
	private static String normalizeCode(String code) {
		String trimmed = Text.trimToNull(code);
		return (trimmed == null) ? null : trimmed.toUpperCase(Locale.ROOT);
	}

	private static DuplicateResourceException duplicate(String code) {
		return new DuplicateResourceException("A disease with ICD-10 code '" + code + "' already exists");
	}

	private static void copy(DiseaseRequest request, Disease disease) {
		disease.setName(Text.trimToNull(request.name()));
		disease.setIcd10Code(normalizeCode(request.icd10Code()));
	}

}
