package com.cdss.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.cdss.dto.LabResultRequest;
import com.cdss.dto.PatientConditionRequest;
import com.cdss.dto.VitalSignsRequest;
import com.cdss.entity.ConditionStatus;
import com.cdss.entity.Disease;
import com.cdss.entity.LabResult;
import com.cdss.entity.LabTest;
import com.cdss.entity.Patient;
import com.cdss.entity.PatientCondition;
import com.cdss.entity.VitalSigns;
import com.cdss.exception.DuplicateResourceException;
import com.cdss.exception.InvalidRequestException;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.repository.DiseaseRepository;
import com.cdss.repository.LabResultRepository;
import com.cdss.repository.LabTestRepository;
import com.cdss.repository.PatientConditionRepository;
import com.cdss.repository.PatientRepository;
import com.cdss.repository.VitalSignsRepository;

class ClinicalRecordServiceTests {

	private static final Instant TIME = Instant.parse("2026-10-13T08:00:00Z");

	private final PatientRepository patientRepository = mock(PatientRepository.class);

	private final VitalSignsRepository vitalSignsRepository = mock(VitalSignsRepository.class);

	private final LabResultRepository labResultRepository = mock(LabResultRepository.class);

	private final LabTestRepository labTestRepository = mock(LabTestRepository.class);

	private final PatientConditionRepository conditionRepository = mock(PatientConditionRepository.class);

	private final DiseaseRepository diseaseRepository = mock(DiseaseRepository.class);

	private final ClinicalRecordService service = new ClinicalRecordService(patientRepository, vitalSignsRepository,
			labResultRepository, labTestRepository, conditionRepository, diseaseRepository);

	@BeforeEach
	void setUp() {
		Patient patient = new Patient();
		ReflectionTestUtils.setField(patient, "id", 1L);
		given(patientRepository.findById(1L)).willReturn(Optional.of(patient));
		given(patientRepository.findById(99L)).willReturn(Optional.empty());
	}

	@Test
	void vitalSignsAreStoredForThePatient() {
		given(vitalSignsRepository.save(any(VitalSigns.class))).willAnswer((invocation) -> invocation.getArgument(0));

		var created = service.addVitalSigns(1L, new VitalSignsRequest(TIME, 150, 95, new BigDecimal("37.2"), 88, 16, 97, " "));

		assertThat(created.patientId()).isEqualTo(1L);
		assertThat(created.systolicBp()).isEqualTo(150);
		assertThat(created.temperature()).isEqualByComparingTo("37.2");
		assertThat(created.notes()).isNull();
	}

	@Test
	void recordsForUnknownPatientGive404() {
		assertThatThrownBy(() -> service.addVitalSigns(99L, new VitalSignsRequest(TIME, null, null, null, 80, null, null, null)))
			.isInstanceOf(ResourceNotFoundException.class).hasMessage("Patient with id 99 was not found");
		assertThatThrownBy(() -> service.listLabResults(99L)).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void vitalSignsOfAnotherPatientCannotBeDeleted() {
		given(vitalSignsRepository.findByIdAndPatientId(5L, 1L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.deleteVitalSigns(1L, 5L)).isInstanceOf(ResourceNotFoundException.class);
		verify(vitalSignsRepository, never()).delete(any());
	}

	@Test
	void labResultTakesTheUnitOfTheLabTest() {
		given(labTestRepository.findById(3L)).willReturn(Optional.of(labTest()));
		given(labResultRepository.save(any(LabResult.class))).willAnswer((invocation) -> invocation.getArgument(0));

		var withoutUnit = service.addLabResult(1L, new LabResultRequest(3L, new BigDecimal("8.2"), null, TIME, null));
		var sameUnitOtherCase = service.addLabResult(1L, new LabResultRequest(3L, new BigDecimal("8.2"), "MMOL/L", TIME, null));

		assertThat(withoutUnit.unit()).isEqualTo("mmol/L");
		assertThat(withoutUnit.flag()).isEqualTo("HIGH");
		assertThat(sameUnitOtherCase.unit()).isEqualTo("mmol/L");
	}

	@Test
	void labResultWithWrongUnitOrUnknownTestIsRejected() {
		given(labTestRepository.findById(3L)).willReturn(Optional.of(labTest()));
		given(labTestRepository.findById(4L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.addLabResult(1L, new LabResultRequest(3L, new BigDecimal("148"), "mg/dL", TIME, null)))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("Unit 'mg/dL' does not match the unit for Fasting plasma glucose (mmol/L); convert the value before recording it");
		assertThatThrownBy(() -> service.addLabResult(1L, new LabResultRequest(4L, BigDecimal.ONE, null, TIME, null)))
			.isInstanceOf(InvalidRequestException.class).hasMessage("Lab test with id 4 does not exist");
		verify(labResultRepository, never()).save(any());
	}

	@Test
	void conditionDefaultsToActiveAndCannotBeAddedTwice() {
		Disease disease = new Disease();
		ReflectionTestUtils.setField(disease, "id", 7L);
		disease.setIcd10Code("I10");
		given(diseaseRepository.findById(7L)).willReturn(Optional.of(disease));
		given(conditionRepository.save(any(PatientCondition.class))).willAnswer((invocation) -> invocation.getArgument(0));

		var created = service.addCondition(1L, new PatientConditionRequest(7L, null, null, null));
		assertThat(created.status()).isEqualTo(ConditionStatus.ACTIVE);
		assertThat(created.icd10Code()).isEqualTo("I10");

		given(conditionRepository.existsByPatientIdAndDiseaseId(1L, 7L)).willReturn(true);
		assertThatThrownBy(() -> service.addCondition(1L, new PatientConditionRequest(7L, ConditionStatus.RESOLVED, null, null)))
			.isInstanceOf(DuplicateResourceException.class).hasMessage("Patient 1 already has I10 recorded");
	}

	private static LabTest labTest() {
		LabTest labTest = new LabTest();
		ReflectionTestUtils.setField(labTest, "id", 3L);
		labTest.setName("Fasting plasma glucose");
		labTest.setUnit("mmol/L");
		labTest.setReferenceRangeLow(new BigDecimal("3.90"));
		labTest.setReferenceRangeHigh(new BigDecimal("5.50"));
		return labTest;
	}

}
