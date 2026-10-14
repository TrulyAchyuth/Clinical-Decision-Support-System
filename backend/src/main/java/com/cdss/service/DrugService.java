package com.cdss.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cdss.dto.DrugRequest;
import com.cdss.dto.DrugResponse;
import com.cdss.entity.Drug;
import com.cdss.exception.DuplicateResourceException;
import com.cdss.exception.ResourceInUseException;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.repository.DrugRepository;
import com.cdss.repository.PatientAllergyRepository;
import com.cdss.repository.PatientMedicationRepository;

@Service
@Transactional
public class DrugService {

	private final DrugRepository drugRepository;

	private final PatientMedicationRepository medicationRepository;

	private final PatientAllergyRepository allergyRepository;

	public DrugService(DrugRepository drugRepository, PatientMedicationRepository medicationRepository,
			PatientAllergyRepository allergyRepository) {
		this.drugRepository = drugRepository;
		this.medicationRepository = medicationRepository;
		this.allergyRepository = allergyRepository;
	}

	@Transactional(readOnly = true)
	public List<DrugResponse> findAll() {
		return drugRepository.findAllByOrderByNameAscDosageAsc().stream().map(DrugResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public DrugResponse findById(Long id) {
		return DrugResponse.from(getDrug(id));
	}

	public DrugResponse create(DrugRequest request) {
		String name = Text.trimToNull(request.name());
		String dosage = Text.trimToNull(request.dosage());
		if (drugRepository.existsByNameIgnoreCaseAndDosageIgnoreCase(name, dosage)) {
			throw duplicate(name, dosage);
		}
		Drug drug = new Drug();
		copy(request, drug);
		return DrugResponse.from(drugRepository.save(drug));
	}

	public DrugResponse update(Long id, DrugRequest request) {
		Drug drug = getDrug(id);
		String name = Text.trimToNull(request.name());
		String dosage = Text.trimToNull(request.dosage());
		if (drugRepository.existsByNameIgnoreCaseAndDosageIgnoreCaseAndIdNot(name, dosage, id)) {
			throw duplicate(name, dosage);
		}
		copy(request, drug);
		return DrugResponse.from(drugRepository.saveAndFlush(drug));
	}

	/** A drug used by a patient medication or drug allergy cannot be deleted (409). */
	public void delete(Long id) {
		Drug drug = getDrug(id);
		if (medicationRepository.existsByDrugId(id) || allergyRepository.existsByDrugId(id)) {
			throw new ResourceInUseException("Drug '" + drug.getName() + " " + drug.getDosage()
					+ "' is used in patient medication or allergy records and cannot be deleted");
		}
		drugRepository.delete(drug);
	}

	private Drug getDrug(Long id) {
		return drugRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Drug", id));
	}

	private static DuplicateResourceException duplicate(String name, String dosage) {
		return new DuplicateResourceException("A drug named '" + name + "' with dosage '" + dosage + "' already exists");
	}

	private static void copy(DrugRequest request, Drug drug) {
		drug.setName(Text.trimToNull(request.name()));
		drug.setDosage(Text.trimToNull(request.dosage()));
		drug.setCategory(Text.trimToNull(request.category()));
		drug.setNotes(Text.trimToNull(request.notes()));
	}

}
