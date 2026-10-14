package com.cdss.service;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cdss.dto.DrugInteractionResponse;
import com.cdss.dto.SafetyCheckResponse;
import com.cdss.dto.SafetyFinding;
import com.cdss.entity.AllergyRecordStatus;
import com.cdss.entity.Drug;
import com.cdss.entity.DrugInteraction;
import com.cdss.entity.MedicationStatus;
import com.cdss.entity.Patient;
import com.cdss.exception.InvalidRequestException;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.repository.DrugInteractionRepository;
import com.cdss.repository.DrugRepository;
import com.cdss.repository.PatientAllergyRepository;
import com.cdss.repository.PatientMedicationRepository;
import com.cdss.repository.PatientRepository;

/**
 * Checks a proposed medication against the patient's own active allergies and
 * active medications (see MedicationSafetyChecker). It only reports; it never
 * adds, changes or stops a medication.
 */
@Service
@Transactional(readOnly = true)
public class MedicationSafetyService {

	private final PatientRepository patientRepository;

	private final DrugRepository drugRepository;

	private final PatientMedicationRepository medicationRepository;

	private final PatientAllergyRepository allergyRepository;

	private final DrugInteractionRepository interactionRepository;

	private final Clock clock;

	public MedicationSafetyService(PatientRepository patientRepository, DrugRepository drugRepository,
			PatientMedicationRepository medicationRepository, PatientAllergyRepository allergyRepository,
			DrugInteractionRepository interactionRepository, Clock clock) {
		this.patientRepository = patientRepository;
		this.drugRepository = drugRepository;
		this.medicationRepository = medicationRepository;
		this.allergyRepository = allergyRepository;
		this.interactionRepository = interactionRepository;
		this.clock = clock;
	}

	public SafetyCheckResponse check(Long patientId, Long drugId) {
		Patient patient = patientRepository.findById(patientId)
			.orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
		Drug drug = drugRepository.findById(drugId)
			.orElseThrow(() -> new InvalidRequestException("Drug with id " + drugId + " does not exist"));
		return check(patient, drug);
	}

	/** The interaction dataset, so users can see exactly what the check uses (illustrative sample data). */
	public List<DrugInteractionResponse> interactions() {
		return interactionRepository.findAll()
			.stream()
			.sorted(Comparator.comparing(DrugInteraction::getDrugNameA).thenComparing(DrugInteraction::getDrugNameB))
			.map(DrugInteractionResponse::from)
			.toList();
	}

	SafetyCheckResponse check(Patient patient, Drug drug) {
		List<SafetyFinding> findings = MedicationSafetyChecker.evaluate(drug,
				medicationRepository.findByPatientIdAndStatus(patient.getId(), MedicationStatus.ACTIVE),
				allergyRepository.findByPatientIdAndStatus(patient.getId(), AllergyRecordStatus.ACTIVE),
				patient.getNoKnownAllergiesAt() != null, interactionRepository.findAll());
		return new SafetyCheckResponse(patient.getId(), drug.getId(), drug.getName() + " " + drug.getDosage(),
				MedicationSafetyChecker.overallStatus(findings),
				findings.stream().anyMatch(SafetyFinding::requiresReview), findings, clock.instant(),
				SafetyCheckResponse.DISCLAIMER);
	}

}
