package com.cdss.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

class MedicationServiceTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

	private static final SafetyFinding ALLERGY_WARNING = new SafetyFinding("ALLERGY-21", "ALLERGY", "MATCH_FOUND",
			"HIGH", "Recorded drug allergy matches Ibuprofen 400 mg", "SEVERE", List.of("Ibuprofen 400 mg"),
			List.of("Ibuprofen"), "Recorded allergy to Ibuprofen.", null, "Confirm the allergy history.",
			"Patient allergy record", "PATIENT_RECORD", null, null, null, true);

	private static final SafetyFinding NOTHING_FOUND = new SafetyFinding("INTERACTION-NONE-FOUND", "INTERACTION",
			"NO_MATCH_FOUND", "LOW", "No interaction found in the dataset", null, List.of("Ibuprofen 400 mg"),
			List.of(), "No interaction found in the dataset.", null, null, "Drug-interaction dataset",
			"ILLUSTRATIVE_SAMPLE", null, null, null, false);

	private final PatientRepository patientRepository = mock(PatientRepository.class);

	private final DrugRepository drugRepository = mock(DrugRepository.class);

	private final PatientMedicationRepository medicationRepository = mock(PatientMedicationRepository.class);

	private final MedicationSafetyService safetyService = mock(MedicationSafetyService.class);

	private final MedicationService service = new MedicationService(patientRepository, drugRepository,
			medicationRepository, safetyService, Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC));

	private final Patient patient = patient(1L);

	private final Drug ibuprofen = MedicationSafetyCheckerTests.drug(5L, "Ibuprofen", "400 mg", "NSAID");

	@BeforeEach
	void setUp() {
		given(patientRepository.findById(1L)).willReturn(Optional.of(patient));
		given(drugRepository.findById(5L)).willReturn(Optional.of(ibuprofen));
		given(medicationRepository.save(any(PatientMedication.class))).willAnswer((invocation) -> {
			PatientMedication saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 40L);
			return saved;
		});
		given(medicationRepository.saveAndFlush(any(PatientMedication.class))).willAnswer((invocation) -> invocation.getArgument(0));
	}

	@Test
	void addsAMedicationWhenTheSafetyCheckHasNothingToReview() {
		given(safetyService.check(patient, ibuprofen)).willReturn(check(NOTHING_FOUND));

		MedicationResponse created = service.add(1L, request(TODAY, null, List.of(), null), "clinician");

		assertThat(created.id()).isEqualTo(40L);
		assertThat(created.drugName()).isEqualTo("Ibuprofen");
		assertThat(created.status()).isEqualTo(MedicationStatus.ACTIVE);
		assertThat(created.recordedBy()).isEqualTo("clinician");
		assertThat(created.safetyWarnings()).isEmpty();
		assertThat(created.doseUnit()).isEqualTo("mg");
	}

	@Test
	void unacknowledgedWarningsBlockTheSaveEvenThroughTheApi() {
		given(safetyService.check(patient, ibuprofen)).willReturn(check(ALLERGY_WARNING, NOTHING_FOUND));

		assertThatThrownBy(() -> service.add(1L, request(TODAY, null, List.of(), "reviewed"), "clinician"))
			.isInstanceOfSatisfying(SafetyAcknowledgementRequiredException.class, (ex) -> {
				assertThat(ex.getUnacknowledgedWarnings()).containsExactly("ALLERGY-21");
				assertThat(ex.getSafetyCheck().overallStatus()).isEqualTo("WARNINGS");
			});
		verify(medicationRepository, never()).save(any());
	}

	@Test
	void acknowledgedWarningsNeedAReasonAndAreStoredWithTheMedication() {
		given(safetyService.check(patient, ibuprofen)).willReturn(check(ALLERGY_WARNING, NOTHING_FOUND));

		assertThatThrownBy(() -> service.add(1L, request(TODAY, null, List.of("ALLERGY-21"), "  "), "clinician"))
			.isInstanceOf(InvalidRequestException.class);

		MedicationResponse created = service.add(1L,
				request(TODAY, null, List.of("ALLERGY-21"), "Mild rash only; benefit discussed"), "clinician");
		assertThat(created.safetyWarnings()).containsExactly("ALLERGY: Recorded allergy to Ibuprofen.");
		assertThat(created.safetyOverrideReason()).isEqualTo("Mild rash only; benefit discussed");
	}

	@Test
	void rejectsUnknownPatientUnknownDrugBadDatesAndDuplicates() {
		given(patientRepository.findById(99L)).willReturn(Optional.empty());
		assertThatThrownBy(() -> service.add(99L, request(TODAY, null, List.of(), null), "clinician"))
			.isInstanceOf(ResourceNotFoundException.class);

		given(drugRepository.findById(5L)).willReturn(Optional.empty());
		assertThatThrownBy(() -> service.add(1L, request(TODAY, null, List.of(), null), "clinician"))
			.isInstanceOf(InvalidRequestException.class);
		given(drugRepository.findById(5L)).willReturn(Optional.of(ibuprofen));

		assertThatThrownBy(() -> service.add(1L, request(TODAY, TODAY.minusDays(1), List.of(), null), "clinician"))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessageContaining("before the start date");
		assertThatThrownBy(() -> service.add(1L, request(TODAY.minusDays(30), TODAY.minusDays(1), List.of(), null),
				"clinician"))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessageContaining("in the past");

		given(medicationRepository.existsByPatientIdAndDrugIdAndStatus(1L, 5L, MedicationStatus.ACTIVE)).willReturn(true);
		assertThatThrownBy(() -> service.add(1L, request(TODAY, null, List.of(), null), "clinician"))
			.isInstanceOf(DuplicateResourceException.class);
		verify(medicationRepository, never()).save(any());
	}

	@Test
	void discontinuingKeepsTheRecordAndNeedsAReason() {
		PatientMedication active = active(41L, TODAY.minusDays(10));
		given(medicationRepository.findByIdAndPatientId(41L, 1L)).willReturn(Optional.of(active));

		assertThatThrownBy(() -> service.discontinue(1L, 41L, new MedicationStatusChangeRequest(" ", null), "clinician"))
			.isInstanceOf(InvalidRequestException.class);

		MedicationResponse stopped = service.discontinue(1L, 41L,
				new MedicationStatusChangeRequest("Stomach upset", null), "clinician");

		assertThat(stopped.status()).isEqualTo(MedicationStatus.DISCONTINUED);
		assertThat(stopped.statusReason()).isEqualTo("Stomach upset");
		assertThat(stopped.endDate()).isEqualTo(TODAY);
		assertThat(stopped.startDate()).isEqualTo(TODAY.minusDays(10));
		assertThat(stopped.dose()).isEqualByComparingTo("400");
		assertThat(stopped.statusChangedBy()).isEqualTo("clinician");
		verify(medicationRepository, never()).delete(any());
	}

	@Test
	void finishedMedicationsCannotBeEditedOrStoppedAgain() {
		PatientMedication stopped = active(42L, TODAY.minusDays(10));
		stopped.setStatus(MedicationStatus.DISCONTINUED);
		given(medicationRepository.findByIdAndPatientId(42L, 1L)).willReturn(Optional.of(stopped));

		assertThatThrownBy(() -> service.update(1L, 42L, new MedicationUpdateRequest(new BigDecimal("200"), "mg",
				MedicationRoute.ORAL, "twice daily", TODAY, null, null)))
			.isInstanceOf(StatusChangeNotAllowedException.class);
		assertThatThrownBy(() -> service.complete(1L, 42L, new MedicationStatusChangeRequest(null, null), "clinician"))
			.isInstanceOf(StatusChangeNotAllowedException.class);
	}

	@Test
	void completingWithAFutureEndDateIsRejected() {
		given(medicationRepository.findByIdAndPatientId(41L, 1L)).willReturn(Optional.of(active(41L, TODAY.minusDays(3))));

		assertThatThrownBy(() -> service.complete(1L, 41L, new MedicationStatusChangeRequest(null, TODAY.plusDays(1)),
				"clinician"))
			.isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void updateChangesOnlyEditableFieldsOfAnActiveMedication() {
		PatientMedication active = active(41L, TODAY.minusDays(3));
		given(medicationRepository.findByIdAndPatientId(41L, 1L)).willReturn(Optional.of(active));

		MedicationResponse updated = service.update(1L, 41L, new MedicationUpdateRequest(new BigDecimal("200"), " mg ",
				MedicationRoute.ORAL, " twice daily ", TODAY.minusDays(3), TODAY.plusDays(7), " with food "));

		assertThat(updated.dose()).isEqualByComparingTo("200");
		assertThat(updated.frequency()).isEqualTo("twice daily");
		assertThat(updated.notes()).isEqualTo("with food");
		assertThat(updated.drugId()).isEqualTo(5L);
	}

	private MedicationRequest request(LocalDate start, LocalDate end, List<String> acknowledged, String reason) {
		return new MedicationRequest(5L, new BigDecimal("400"), "mg", MedicationRoute.ORAL, "three times daily", start,
				end, null, acknowledged, reason);
	}

	private PatientMedication active(Long id, LocalDate start) {
		PatientMedication medication = MedicationSafetyCheckerTests.medication(id, ibuprofen);
		medication.setPatient(patient);
		medication.setDose(new BigDecimal("400"));
		medication.setDoseUnit("mg");
		medication.setRoute(MedicationRoute.ORAL);
		medication.setFrequency("three times daily");
		medication.setStartDate(start);
		return medication;
	}

	private static SafetyCheckResponse check(SafetyFinding... findings) {
		List<SafetyFinding> list = List.of(findings);
		return new SafetyCheckResponse(1L, 5L, "Ibuprofen 400 mg", MedicationSafetyChecker.overallStatus(list),
				list.stream().anyMatch(SafetyFinding::requiresReview), list, Instant.parse("2026-10-14T10:00:00Z"),
				SafetyCheckResponse.DISCLAIMER);
	}

	static Patient patient(Long id) {
		Patient patient = new Patient();
		ReflectionTestUtils.setField(patient, "id", id);
		patient.setFullName("Daniel Okafor");
		patient.setDateOfBirth(LocalDate.of(1958, 4, 12));
		return patient;
	}

}
