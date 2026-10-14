package com.cdss.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.cdss.config.DecisionProperties;
import com.cdss.entity.ConditionStatus;
import com.cdss.entity.Disease;
import com.cdss.entity.LabResult;
import com.cdss.entity.LabTest;
import com.cdss.entity.Patient;
import com.cdss.entity.PatientCondition;
import com.cdss.entity.VitalSigns;
import com.cdss.repository.LabResultRepository;
import com.cdss.repository.PatientConditionRepository;
import com.cdss.repository.VitalSignsRepository;
import com.cdss.ruleengine.FactDefinition;
import com.cdss.ruleengine.FactType;
import com.cdss.ruleengine.PatientFacts;

class PatientFactsServiceTests {

	private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");

	private final VitalSignsRepository vitalSignsRepository = mock(VitalSignsRepository.class);

	private final LabResultRepository labResultRepository = mock(LabResultRepository.class);

	private final PatientConditionRepository conditionRepository = mock(PatientConditionRepository.class);

	private final PatientFactsService service = new PatientFactsService(vitalSignsRepository, labResultRepository,
			conditionRepository, new DecisionProperties(Duration.ofHours(24), Duration.ofHours(72)));

	@Test
	void usesNewestValueOfEachVitalSignWithinTheTimeLimit() {
		Patient patient = patient();
		VitalSigns newest = vitals(NOW.minusSeconds(600));
		newest.setHeartRate(110);
		VitalSigns older = vitals(NOW.minusSeconds(7200));
		older.setHeartRate(80);
		older.setTemperature(new BigDecimal("38.4"));
		given(vitalSignsRepository.findByPatientIdAndRecordedAtGreaterThanEqualOrderByRecordedAtDescIdDesc(1L,
				NOW.minus(Duration.ofHours(24)))).willReturn(List.of(newest, older));
		noLabsOrConditions();

		PatientFacts facts = service.collect(patient, NOW);

		assertThat(facts.get("vitals.heartRate")).get().satisfies((value) -> {
			assertThat(value.number()).isEqualByComparingTo("110");
			assertThat(value.observedAt()).isEqualTo(NOW.minusSeconds(600));
		});
		// Temperature was only in the older recording, which is still within 24 hours.
		assertThat(facts.get("vitals.temperature")).get().extracting(PatientFacts.Value::number)
			.isEqualTo(new BigDecimal("38.4"));
		// Never recorded: missing, not zero or normal.
		assertThat(facts.get("vitals.oxygenSaturation")).isEmpty();
	}

	@Test
	void usesNewestResultPerLabTestAndActiveConditionsAndAge() {
		Patient patient = patient();
		LabTest glucose = labTest(5L, "Fasting plasma glucose");
		given(vitalSignsRepository.findByPatientIdAndRecordedAtGreaterThanEqualOrderByRecordedAtDescIdDesc(1L,
				NOW.minus(Duration.ofHours(24)))).willReturn(List.of());
		given(labResultRepository.findByPatientIdAndMeasuredAtGreaterThanEqualOrderByMeasuredAtDescIdDesc(1L,
				NOW.minus(Duration.ofHours(72)))).willReturn(List.of(labResult(glucose, "8.20"), labResult(glucose, "5.00")));
		given(conditionRepository.findByPatientIdAndStatus(1L, ConditionStatus.ACTIVE))
			.willReturn(List.of(condition("E11.9"), condition("I10")));

		PatientFacts facts = service.collect(patient, NOW);

		assertThat(facts.get("lab.Fasting plasma glucose")).get().extracting(PatientFacts.Value::number)
			.isEqualTo(new BigDecimal("8.20"));
		assertThat(facts.get("conditions")).get().extracting(PatientFacts.Value::codes)
			.isEqualTo(Set.of("E11.9", "I10"));
		// Born 1970-10-14: still 55 on 2026-10-13.
		assertThat(facts.get("patient.ageYears")).get().extracting(PatientFacts.Value::number)
			.isEqualTo(BigDecimal.valueOf(55));
	}

	@Test
	void explainsMissingFactsWithTheTimeLimit() {
		assertThat(service.describeMissing(new FactDefinition("vitals.heartRate", "Heart rate", "beats/min", FactType.NUMBER)))
			.isEqualTo("No heart rate recorded in the last 24 hours");
		assertThat(service.describeMissing(new FactDefinition("lab.HbA1c", "HbA1c", "%", FactType.NUMBER)))
			.isEqualTo("No HbA1c result in the last 72 hours");
	}

	private void noLabsOrConditions() {
		given(labResultRepository.findByPatientIdAndMeasuredAtGreaterThanEqualOrderByMeasuredAtDescIdDesc(1L,
				NOW.minus(Duration.ofHours(72)))).willReturn(List.of());
		given(conditionRepository.findByPatientIdAndStatus(1L, ConditionStatus.ACTIVE)).willReturn(List.of());
	}

	private static Patient patient() {
		Patient patient = new Patient();
		ReflectionTestUtils.setField(patient, "id", 1L);
		patient.setDateOfBirth(LocalDate.of(1970, 10, 14));
		return patient;
	}

	private static VitalSigns vitals(Instant recordedAt) {
		VitalSigns vitals = new VitalSigns();
		vitals.setRecordedAt(recordedAt);
		return vitals;
	}

	private static LabTest labTest(Long id, String name) {
		LabTest labTest = new LabTest();
		ReflectionTestUtils.setField(labTest, "id", id);
		labTest.setName(name);
		return labTest;
	}

	private static LabResult labResult(LabTest labTest, String value) {
		LabResult result = new LabResult();
		result.setLabTest(labTest);
		result.setValue(new BigDecimal(value));
		result.setMeasuredAt(NOW.minusSeconds(3600));
		return result;
	}

	private static PatientCondition condition(String icd10Code) {
		Disease disease = new Disease();
		disease.setIcd10Code(icd10Code);
		PatientCondition condition = new PatientCondition();
		condition.setDisease(disease);
		condition.setStatus(ConditionStatus.ACTIVE);
		return condition;
	}

}
