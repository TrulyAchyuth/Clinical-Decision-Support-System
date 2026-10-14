package com.cdss.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.cdss.dto.AllergyRequest;
import com.cdss.dto.AllergyResponse;
import com.cdss.dto.AllergyUpdateRequest;
import com.cdss.dto.PatientAllergiesResponse;
import com.cdss.entity.AllergyCategory;
import com.cdss.entity.AllergyRecordStatus;
import com.cdss.entity.AllergySeverity;
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

class AllergyServiceTests {

	private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

	private final PatientRepository patientRepository = mock(PatientRepository.class);

	private final PatientAllergyRepository allergyRepository = mock(PatientAllergyRepository.class);

	private final DrugRepository drugRepository = mock(DrugRepository.class);

	private final AllergyService service = new AllergyService(patientRepository, allergyRepository, drugRepository,
			Clock.fixed(NOW, ZoneOffset.UTC));

	private final Patient patient = MedicationServiceTests.patient(1L);

	private final Drug amoxicillin = MedicationSafetyCheckerTests.drug(1L, "Amoxicillin", "500 mg",
			"Antibiotic (penicillin)");

	@BeforeEach
	void setUp() {
		given(patientRepository.findById(1L)).willReturn(Optional.of(patient));
		given(drugRepository.findById(1L)).willReturn(Optional.of(amoxicillin));
		given(allergyRepository.save(any(PatientAllergy.class))).willAnswer((invocation) -> {
			PatientAllergy saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 50L);
			return saved;
		});
		given(allergyRepository.saveAndFlush(any(PatientAllergy.class))).willAnswer((invocation) -> invocation.getArgument(0));
	}

	@Test
	void noRecordsAndNothingConfirmedIsNotAssessedNotNoAllergies() {
		given(allergyRepository.findByPatientIdOrderByStatusAscRecordedDateDescIdDesc(1L)).willReturn(List.of());

		PatientAllergiesResponse response = service.list(1L);

		assertThat(response.assessment()).isEqualTo("NOT_ASSESSED");
		assertThat(response.assessmentExplanation()).contains("does not mean the patient has no allergies");
		assertThat(response.noKnownAllergiesConfirmedAt()).isNull();
	}

	@Test
	void onlyResolvedAllergiesAndNoConfirmationIsStillNotAssessed() {
		PatientAllergy resolved = existing(51L, AllergyCategory.FOOD, "Peanut");
		resolved.setStatus(AllergyRecordStatus.RESOLVED);
		given(allergyRepository.findByPatientIdOrderByStatusAscRecordedDateDescIdDesc(1L)).willReturn(List.of(resolved));

		assertThat(service.list(1L).assessment()).isEqualTo("NOT_ASSESSED");
	}

	@Test
	void recordingADrugAllergyLinksTheDrugAndClearsNoKnownAllergies() {
		patient.setNoKnownAllergies(NOW.minusSeconds(3600), "admin");

		AllergyResponse created = service.add(1L, new AllergyRequest(AllergyCategory.DRUG, 1L, "ignored", "Hives",
				AllergySeverity.SEVERE, LocalDate.of(2026, 10, 1), null), "clinician");

		assertThat(created.allergen()).isEqualTo("Amoxicillin");
		assertThat(created.drugId()).isEqualTo(1L);
		assertThat(created.status()).isEqualTo(AllergyRecordStatus.ACTIVE);
		assertThat(created.recordedBy()).isEqualTo("clinician");
		assertThat(patient.getNoKnownAllergiesAt()).isNull();
	}

	@Test
	void validatesDrugLinksCategoriesAndDuplicates() {
		assertThatThrownBy(() -> service.add(1L, request(AllergyCategory.DRUG, null, null), "clinician"))
			.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.add(1L, request(AllergyCategory.FOOD, 1L, "Peanut"), "clinician"))
			.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.add(1L, request(AllergyCategory.FOOD, null, "  "), "clinician"))
			.isInstanceOf(InvalidRequestException.class);
		given(drugRepository.existsByCategoryIgnoreCase("Penicillins")).willReturn(false);
		assertThatThrownBy(() -> service.add(1L, request(AllergyCategory.DRUG_CATEGORY, null, "Penicillins"), "clinician"))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessageContaining("knowledge base");

		given(allergyRepository.findByPatientIdAndStatus(1L, AllergyRecordStatus.ACTIVE))
			.willReturn(List.of(existing(52L, AllergyCategory.FOOD, "peanut ")));
		assertThatThrownBy(() -> service.add(1L, request(AllergyCategory.FOOD, null, "Peanut"), "clinician"))
			.isInstanceOf(DuplicateResourceException.class);
		verify(allergyRepository, never()).save(any());
	}

	@Test
	void resolvingKeepsTheRecordAndAResolvedAllergyCannotBeEdited() {
		PatientAllergy allergy = existing(53L, AllergyCategory.FOOD, "Peanut");
		given(allergyRepository.findByIdAndPatientId(53L, 1L)).willReturn(Optional.of(allergy));

		AllergyResponse resolved = service.resolve(1L, 53L, " Tolerated on challenge ", "clinician");

		assertThat(resolved.status()).isEqualTo(AllergyRecordStatus.RESOLVED);
		assertThat(resolved.resolutionReason()).isEqualTo("Tolerated on challenge");
		assertThat(resolved.resolvedAt()).isEqualTo(NOW);
		assertThat(resolved.allergen()).isEqualTo("Peanut");
		verify(allergyRepository, never()).delete(any());
		assertThatThrownBy(() -> service.update(1L, 53L, new AllergyUpdateRequest(null, AllergySeverity.MILD,
				LocalDate.of(2026, 1, 1), null)))
			.isInstanceOf(StatusChangeNotAllowedException.class);
	}

	@Test
	void noKnownAllergiesIsRefusedWhileAnAllergyIsActiveAndRecordedOtherwise() {
		given(allergyRepository.existsByPatientIdAndStatus(1L, AllergyRecordStatus.ACTIVE)).willReturn(true);
		assertThatThrownBy(() -> service.confirmNoKnownAllergies(1L, "clinician"))
			.isInstanceOf(StatusChangeNotAllowedException.class);

		given(allergyRepository.existsByPatientIdAndStatus(1L, AllergyRecordStatus.ACTIVE)).willReturn(false);
		given(allergyRepository.findByPatientIdOrderByStatusAscRecordedDateDescIdDesc(1L)).willReturn(List.of());
		PatientAllergiesResponse response = service.confirmNoKnownAllergies(1L, "clinician");

		assertThat(response.assessment()).isEqualTo("NO_KNOWN_ALLERGIES");
		assertThat(response.noKnownAllergiesConfirmedBy()).isEqualTo("clinician");
		assertThat(response.noKnownAllergiesConfirmedAt()).isEqualTo(NOW);
	}

	@Test
	void unknownPatientIsNotFound() {
		given(patientRepository.findById(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.list(99L)).isInstanceOf(ResourceNotFoundException.class);
	}

	private static AllergyRequest request(AllergyCategory category, Long drugId, String allergen) {
		return new AllergyRequest(category, drugId, allergen, null, AllergySeverity.UNKNOWN, LocalDate.of(2026, 10, 1),
				null);
	}

	private PatientAllergy existing(Long id, AllergyCategory category, String allergen) {
		PatientAllergy allergy = MedicationSafetyCheckerTests.allergy(id, category, allergen, null);
		allergy.setPatient(patient);
		allergy.setRecordedDate(LocalDate.of(2026, 1, 1));
		return allergy;
	}

}
