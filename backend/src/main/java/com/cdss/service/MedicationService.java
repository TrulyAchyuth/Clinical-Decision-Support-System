package com.cdss.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cdss.dto.MedicationRequest;
import com.cdss.dto.MedicationResponse;
import com.cdss.dto.MedicationStatusChangeRequest;
import com.cdss.dto.MedicationUpdateRequest;
import com.cdss.dto.SafetyCheckResponse;
import com.cdss.dto.SafetyFinding;
import com.cdss.entity.Drug;
import com.cdss.entity.MedicationRoute;
import com.cdss.entity.MedicationStatus;
import com.cdss.entity.Patient;
import com.cdss.entity.PatientMedication;
import com.cdss.exception.DuplicateResourceException;
import com.cdss.exception.InvalidRequestException;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.exception.SafetyAcknowledgementRequiredException;
import com.cdss.exception.StatusChangeNotAllowedException;
import com.cdss.repository.DrugRepository;
import com.cdss.repository.PatientMedicationRepository;
import com.cdss.repository.PatientRepository;

/**
 * A patient's medications. Adding one runs the safety check first; any
 * warning that needs review must be acknowledged, with a reason, or nothing
 * is saved. Medications are never deleted: an ACTIVE medication can be edited,
 * discontinued (with a reason) or completed, and then stays as history.
 * Dates are calendar dates in UTC, like the rest of the application.
 */
@Service
@Transactional
public class MedicationService {

	private final PatientRepository patientRepository;

	private final DrugRepository drugRepository;

	private final PatientMedicationRepository medicationRepository;

	private final MedicationSafetyService safetyService;

	private final Clock clock;

	public MedicationService(PatientRepository patientRepository, DrugRepository drugRepository,
			PatientMedicationRepository medicationRepository, MedicationSafetyService safetyService, Clock clock) {
		this.patientRepository = patientRepository;
		this.drugRepository = drugRepository;
		this.medicationRepository = medicationRepository;
		this.safetyService = safetyService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<MedicationResponse> list(Long patientId) {
		getPatient(patientId);
		return medicationRepository.findByPatientIdOrderByStartDateDescIdDesc(patientId)
			.stream()
			.map(MedicationResponse::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public MedicationResponse get(Long patientId, Long medicationId) {
		return MedicationResponse.from(getMedication(patientId, medicationId));
	}

	public MedicationResponse add(Long patientId, MedicationRequest request, String username) {
		Patient patient = getPatient(patientId);
		Drug drug = drugRepository.findById(request.drugId())
			.orElseThrow(() -> new InvalidRequestException("Drug with id " + request.drugId() + " does not exist"));
		checkDates(request.startDate(), request.endDate());
		if (medicationRepository.existsByPatientIdAndDrugIdAndStatus(patientId, drug.getId(), MedicationStatus.ACTIVE)) {
			throw new DuplicateResourceException("The patient already has an active medication for " + drug.getName()
					+ " " + drug.getDosage() + "; edit or discontinue it instead");
		}

		SafetyCheckResponse check = safetyService.check(patient, drug);
		List<SafetyFinding> toReview = check.findings().stream().filter(SafetyFinding::requiresReview).toList();
		Set<String> acknowledged = new HashSet<>(
				request.acknowledgedWarnings() == null ? List.of() : request.acknowledgedWarnings());
		List<String> missing = toReview.stream()
			.map(SafetyFinding::code)
			.filter((code) -> !acknowledged.contains(code))
			.toList();
		if (!missing.isEmpty()) {
			throw new SafetyAcknowledgementRequiredException("The safety check found " + missing.size()
					+ " warning(s) that must be reviewed and acknowledged before this medication can be saved",
					check, missing);
		}
		String overrideReason = Text.trimToNull(request.overrideReason());
		if (!toReview.isEmpty() && overrideReason == null) {
			throw new InvalidRequestException("Give a reason for continuing despite the safety warnings");
		}

		PatientMedication medication = new PatientMedication();
		medication.setPatient(patient);
		medication.setDrug(drug);
		applyEditableFields(medication, request.dose(), request.doseUnit(), request.route(), request.frequency(),
				request.startDate(), request.endDate(), request.notes());
		medication.setStatus(MedicationStatus.ACTIVE);
		medication.setSafetyWarnings(
				toReview.stream().map((finding) -> finding.checkType() + ": " + finding.explanation()).toList());
		medication.setSafetyOverrideReason(toReview.isEmpty() ? null : overrideReason);
		medication.setRecordedBy(username);
		return MedicationResponse.from(medicationRepository.save(medication));
	}

	public MedicationResponse update(Long patientId, Long medicationId, MedicationUpdateRequest request) {
		PatientMedication medication = getActiveMedication(patientId, medicationId, "edited");
		checkDates(request.startDate(), request.endDate());
		applyEditableFields(medication, request.dose(), request.doseUnit(), request.route(), request.frequency(),
				request.startDate(), request.endDate(), request.notes());
		return MedicationResponse.from(medicationRepository.saveAndFlush(medication));
	}

	/** Stops an ACTIVE medication. The record, its dates and its safety warnings are kept. */
	public MedicationResponse discontinue(Long patientId, Long medicationId, MedicationStatusChangeRequest request,
			String username) {
		String reason = Text.trimToNull(request.reason());
		if (reason == null) {
			throw new InvalidRequestException("Give a reason for discontinuing the medication");
		}
		return finish(patientId, medicationId, MedicationStatus.DISCONTINUED, reason, request.endDate(), username);
	}

	/** Marks an ACTIVE medication as a completed course. */
	public MedicationResponse complete(Long patientId, Long medicationId, MedicationStatusChangeRequest request,
			String username) {
		return finish(patientId, medicationId, MedicationStatus.COMPLETED, Text.trimToNull(request.reason()),
				request.endDate(), username);
	}

	private MedicationResponse finish(Long patientId, Long medicationId, MedicationStatus status, String reason,
			LocalDate requestedEndDate, String username) {
		PatientMedication medication = getActiveMedication(patientId, medicationId,
				status == MedicationStatus.DISCONTINUED ? "discontinued" : "completed");
		LocalDate today = LocalDate.now(clock);
		LocalDate endDate = requestedEndDate;
		if (endDate == null) {
			// Stopped before it started: end it on its start date so the dates stay consistent.
			endDate = today.isBefore(medication.getStartDate()) ? medication.getStartDate() : today;
		}
		else if (endDate.isAfter(today)) {
			throw new InvalidRequestException("The end date cannot be in the future");
		}
		if (endDate.isBefore(medication.getStartDate())) {
			throw new InvalidRequestException("The end date cannot be before the start date");
		}
		medication.setEndDate(endDate);
		medication.setStatus(status);
		medication.setStatusReason(reason);
		medication.setStatusChangedAt(clock.instant());
		medication.setStatusChangedBy(username);
		return MedicationResponse.from(medicationRepository.saveAndFlush(medication));
	}

	private void checkDates(LocalDate startDate, LocalDate endDate) {
		if (endDate == null) {
			return;
		}
		if (endDate.isBefore(startDate)) {
			throw new InvalidRequestException("The end date cannot be before the start date");
		}
		if (endDate.isBefore(LocalDate.now(clock))) {
			throw new InvalidRequestException(
					"An active medication cannot have an end date in the past; mark it completed instead");
		}
	}

	private static void applyEditableFields(PatientMedication medication, BigDecimal dose, String doseUnit,
			MedicationRoute route, String frequency, LocalDate startDate, LocalDate endDate,
			String notes) {
		medication.setDose(dose);
		medication.setDoseUnit(doseUnit.strip());
		medication.setRoute(route);
		medication.setFrequency(frequency.strip());
		medication.setStartDate(startDate);
		medication.setEndDate(endDate);
		medication.setNotes(Text.trimToNull(notes));
	}

	private PatientMedication getActiveMedication(Long patientId, Long medicationId, String action) {
		PatientMedication medication = getMedication(patientId, medicationId);
		if (medication.getStatus() != MedicationStatus.ACTIVE) {
			throw new StatusChangeNotAllowedException("Only an active medication can be " + action + "; this one is "
					+ medication.getStatus().name().toLowerCase(Locale.ROOT));
		}
		return medication;
	}

	private PatientMedication getMedication(Long patientId, Long medicationId) {
		getPatient(patientId);
		return medicationRepository.findByIdAndPatientId(medicationId, patientId)
			.orElseThrow(() -> new ResourceNotFoundException("Medication", medicationId));
	}

	private Patient getPatient(Long patientId) {
		return patientRepository.findById(patientId)
			.orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
	}

}
