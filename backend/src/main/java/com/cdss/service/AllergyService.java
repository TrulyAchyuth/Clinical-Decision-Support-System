package com.cdss.service;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cdss.dto.AllergyRequest;
import com.cdss.dto.AllergyResponse;
import com.cdss.dto.AllergyUpdateRequest;
import com.cdss.dto.PatientAllergiesResponse;
import com.cdss.entity.AllergyCategory;
import com.cdss.entity.AllergyRecordStatus;
import com.cdss.entity.Drug;
import com.cdss.entity.Patient;
import com.cdss.entity.PatientAllergy;
import com.cdss.exception.DuplicateResourceException;
import com.cdss.exception.InvalidRequestException;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.exception.StatusChangeNotAllowedException;
import com.cdss.repository.DrugRepository;
import com.cdss.repository.PatientAllergyRepository;
import com.cdss.repository.PatientRepository;

/**
 * A patient's allergies and allergy-history status. The status is one of:
 * <ul>
 * <li>ALLERGIES_RECORDED: at least one ACTIVE allergy;</li>
 * <li>NO_KNOWN_ALLERGIES: a user confirmed it explicitly, and no allergy is active;</li>
 * <li>NOT_ASSESSED: anything else. Missing information is never treated as "no allergies".</li>
 * </ul>
 * Recording an allergy clears an earlier "no known allergies" confirmation.
 * Allergies are never deleted; they are resolved with a reason.
 */
@Service
@Transactional
public class AllergyService {

	public static final String ALLERGIES_RECORDED = "ALLERGIES_RECORDED";

	public static final String NO_KNOWN_ALLERGIES = "NO_KNOWN_ALLERGIES";

	public static final String NOT_ASSESSED = "NOT_ASSESSED";

	private final PatientRepository patientRepository;

	private final PatientAllergyRepository allergyRepository;

	private final DrugRepository drugRepository;

	private final Clock clock;

	public AllergyService(PatientRepository patientRepository, PatientAllergyRepository allergyRepository,
			DrugRepository drugRepository, Clock clock) {
		this.patientRepository = patientRepository;
		this.allergyRepository = allergyRepository;
		this.drugRepository = drugRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PatientAllergiesResponse list(Long patientId) {
		Patient patient = getPatient(patientId);
		List<PatientAllergy> allergies = allergyRepository.findByPatientIdOrderByStatusAscRecordedDateDescIdDesc(patientId);
		long active = allergies.stream().filter((allergy) -> allergy.getStatus() == AllergyRecordStatus.ACTIVE).count();
		String assessment = assessment(active > 0, patient.getNoKnownAllergiesAt() != null);
		return new PatientAllergiesResponse(patientId, assessment, explanation(assessment, active),
				patient.getNoKnownAllergiesAt(), patient.getNoKnownAllergiesBy(),
				allergies.stream().map(AllergyResponse::from).toList());
	}

	public AllergyResponse add(Long patientId, AllergyRequest request, String username) {
		Patient patient = getPatient(patientId);
		PatientAllergy allergy = new PatientAllergy();
		allergy.setPatient(patient);
		allergy.setCategory(request.category());
		if (request.category() == AllergyCategory.DRUG) {
			if (request.drugId() == null) {
				throw new InvalidRequestException("Choose the drug from the knowledge base for a drug allergy");
			}
			Drug drug = drugRepository.findById(request.drugId())
				.orElseThrow(() -> new InvalidRequestException("Drug with id " + request.drugId() + " does not exist"));
			allergy.setDrug(drug);
			allergy.setAllergen(drug.getName());
		}
		else {
			if (request.drugId() != null) {
				throw new InvalidRequestException("Only a DRUG allergy can be linked to a knowledge-base drug");
			}
			String allergen = Text.trimToNull(request.allergen());
			if (allergen == null) {
				throw new InvalidRequestException("Enter the allergen");
			}
			if (request.category() == AllergyCategory.DRUG_CATEGORY && !drugRepository.existsByCategoryIgnoreCase(allergen)) {
				throw new InvalidRequestException("No knowledge-base drug has the category '" + allergen
						+ "'; choose a category used in the drug knowledge base");
			}
			allergy.setAllergen(allergen);
		}
		boolean duplicate = allergyRepository.findByPatientIdAndStatus(patientId, AllergyRecordStatus.ACTIVE)
			.stream()
			.anyMatch((existing) -> existing.getCategory() == allergy.getCategory()
					&& MedicationSafetyChecker.sameName(existing.getAllergen(), allergy.getAllergen()));
		if (duplicate) {
			throw new DuplicateResourceException("An active allergy to '" + allergy.getAllergen()
					+ "' is already recorded; edit it instead");
		}
		allergy.setReaction(Text.trimToNull(request.reaction()));
		allergy.setSeverity(request.severity());
		allergy.setStatus(AllergyRecordStatus.ACTIVE);
		allergy.setRecordedDate(request.recordedDate());
		allergy.setNotes(Text.trimToNull(request.notes()));
		allergy.setRecordedBy(username);
		PatientAllergy saved = allergyRepository.save(allergy);
		// An allergy is now known, so an earlier "no known allergies" confirmation no longer holds.
		patient.setNoKnownAllergies(null, null);
		patientRepository.save(patient);
		return AllergyResponse.from(saved);
	}

	public AllergyResponse update(Long patientId, Long allergyId, AllergyUpdateRequest request) {
		PatientAllergy allergy = getActiveAllergy(patientId, allergyId, "edited");
		allergy.setReaction(Text.trimToNull(request.reaction()));
		allergy.setSeverity(request.severity());
		allergy.setRecordedDate(request.recordedDate());
		allergy.setNotes(Text.trimToNull(request.notes()));
		return AllergyResponse.from(allergyRepository.saveAndFlush(allergy));
	}

	/** Marks an allergy as no longer current. The record is kept as history. */
	public AllergyResponse resolve(Long patientId, Long allergyId, String reason, String username) {
		PatientAllergy allergy = getActiveAllergy(patientId, allergyId, "resolved");
		allergy.setStatus(AllergyRecordStatus.RESOLVED);
		allergy.setResolutionReason(reason.strip());
		allergy.setResolvedAt(clock.instant());
		allergy.setResolvedBy(username);
		return AllergyResponse.from(allergyRepository.saveAndFlush(allergy));
	}

	/** Records that the patient was asked and has no known allergies. Refused while any allergy is active. */
	public PatientAllergiesResponse confirmNoKnownAllergies(Long patientId, String username) {
		Patient patient = getPatient(patientId);
		if (allergyRepository.existsByPatientIdAndStatus(patientId, AllergyRecordStatus.ACTIVE)) {
			throw new StatusChangeNotAllowedException(
					"The patient has active allergy records; resolve them before confirming no known allergies");
		}
		patient.setNoKnownAllergies(clock.instant(), username);
		patientRepository.save(patient);
		return list(patientId);
	}

	static String assessment(boolean hasActiveAllergy, boolean noKnownAllergiesConfirmed) {
		if (hasActiveAllergy) {
			return ALLERGIES_RECORDED;
		}
		return noKnownAllergiesConfirmed ? NO_KNOWN_ALLERGIES : NOT_ASSESSED;
	}

	private static String explanation(String assessment, long activeCount) {
		return switch (assessment) {
			case ALLERGIES_RECORDED -> activeCount + " active allergy record(s).";
			case NO_KNOWN_ALLERGIES -> "No known allergies was confirmed explicitly.";
			default -> "Allergy history not assessed: no active allergy is recorded and 'no known allergies' has not "
					+ "been confirmed. This does not mean the patient has no allergies.";
		};
	}

	private PatientAllergy getActiveAllergy(Long patientId, Long allergyId, String action) {
		getPatient(patientId);
		PatientAllergy allergy = allergyRepository.findByIdAndPatientId(allergyId, patientId)
			.orElseThrow(() -> new ResourceNotFoundException("Allergy", allergyId));
		if (allergy.getStatus() != AllergyRecordStatus.ACTIVE) {
			throw new StatusChangeNotAllowedException("Only an active allergy can be " + action);
		}
		return allergy;
	}

	private Patient getPatient(Long patientId) {
		return patientRepository.findById(patientId)
			.orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
	}

}
