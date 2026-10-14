package com.cdss.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.cdss.dto.DecisionResponse;
import com.cdss.entity.ClinicalDecision;
import com.cdss.entity.ClinicalRule;
import com.cdss.entity.DecisionOutcome;
import com.cdss.entity.Gender;
import com.cdss.entity.Patient;
import com.cdss.entity.Severity;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.repository.ClinicalDecisionRepository;
import com.cdss.repository.ClinicalRuleRepository;
import com.cdss.repository.PatientRepository;
import com.cdss.ruleengine.FactCatalog;
import com.cdss.ruleengine.FactDefinition;
import com.cdss.ruleengine.PatientFacts;

class DecisionServiceTests {

	private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");

	private final PatientRepository patientRepository = mock(PatientRepository.class);

	private final ClinicalRuleRepository ruleRepository = mock(ClinicalRuleRepository.class);

	private final ClinicalDecisionRepository decisionRepository = mock(ClinicalDecisionRepository.class);

	private final ClinicalRuleService ruleService = mock(ClinicalRuleService.class);

	private final PatientFactsService factsService = mock(PatientFactsService.class);

	private final DecisionService service = new DecisionService(patientRepository, ruleRepository, decisionRepository,
			ruleService, factsService, Clock.fixed(NOW, ZoneOffset.UTC));

	private final Patient patient = patient(1L);

	@BeforeEach
	void setUp() {
		given(patientRepository.findById(1L)).willReturn(Optional.of(patient));
		given(ruleService.factCatalog()).willReturn(new FactCatalog(Map.of("Fasting plasma glucose", "mmol/L")));
		given(decisionRepository.save(any(ClinicalDecision.class))).willAnswer((invocation) -> invocation.getArgument(0));
		given(factsService.describeMissing(any())).willAnswer(
				(invocation) -> "No " + invocation.<FactDefinition>getArgument(0).label() + " recorded");
	}

	@Test
	void matchedRuleIsReturnedWithReasonsAndActions() {
		rules(tachycardiaRule());
		facts(new PatientFacts().withNumber("vitals.heartRate", new BigDecimal("118"), NOW.minusSeconds(600)));

		DecisionResponse decision = service.requestDecision(1L);

		assertThat(decision.outcome()).isEqualTo(DecisionOutcome.RULES_MATCHED);
		assertThat(decision.rulesEvaluated()).isEqualTo(1);
		assertThat(decision.requestedAt()).isEqualTo(NOW);
		assertThat(decision.matchedRules()).singleElement().satisfies((rule) -> {
			assertThat(rule.ruleId()).isEqualTo("HR-HIGH");
			assertThat(rule.severity()).isEqualTo(Severity.MEDIUM);
			assertThat(rule.reasons()).containsExactly(
					"Heart rate is 118 beats/min (recorded 2026-10-13 11:50 UTC), which is greater than 100 beats/min.");
			assertThat(rule.recommendedActions()).containsExactly("Re-check the heart rate at rest");
		});
		assertThat(decision.summary()).startsWith("1 of 1 active rules matched").contains("not a diagnosis");
		assertThat(decision.disclaimer()).contains("not diagnoses").contains("not clinically validated");
		verify(decisionRepository).save(any(ClinicalDecision.class));
	}

	@Test
	void noMatchIsReportedClearlyAndStillRecorded() {
		rules(tachycardiaRule());
		facts(new PatientFacts().withNumber("vitals.heartRate", new BigDecimal("72"), NOW));

		DecisionResponse decision = service.requestDecision(1L);

		assertThat(decision.outcome()).isEqualTo(DecisionOutcome.NO_RULES_MATCHED);
		assertThat(decision.matchedRules()).isEmpty();
		assertThat(decision.insufficientData()).isEmpty();
		assertThat(decision.rulesNotMatched()).isEqualTo(1);
		assertThat(decision.summary()).isEqualTo(
				"No active rule matched the recorded data. This does not rule out any condition.");
		verify(decisionRepository).save(any(ClinicalDecision.class));
	}

	@Test
	void missingDataIsReportedAsInsufficientNotAsNormal() {
		rules(tachycardiaRule());
		facts(new PatientFacts());

		DecisionResponse decision = service.requestDecision(1L);

		assertThat(decision.outcome()).isEqualTo(DecisionOutcome.NO_RULES_MATCHED);
		assertThat(decision.rulesNotMatched()).isZero();
		assertThat(decision.insufficientData()).singleElement().satisfies((rule) -> {
			assertThat(rule.ruleId()).isEqualTo("HR-HIGH");
			assertThat(rule.reasons()).containsExactly("No Heart rate recorded");
			assertThat(rule.recommendedActions()).isEmpty();
		});
		assertThat(decision.summary()).contains("1 rule could not be evaluated because data was missing or too old");
	}

	@Test
	void invalidStoredRuleIsReportedAndOtherRulesStillRun() {
		ClinicalRule broken = rule(9L, "BROKEN", 5, "{\"fact\": \"lab.Sodium\", \"operator\": \"lt\", \"value\": 130}");
		rules(broken, tachycardiaRule());
		facts(new PatientFacts().withNumber("vitals.heartRate", new BigDecimal("130"), NOW));

		DecisionResponse decision = service.requestDecision(1L);

		assertThat(decision.invalidRules()).singleElement().satisfies((rule) -> {
			assertThat(rule.ruleId()).isEqualTo("BROKEN");
			assertThat(rule.reasons()).containsExactly("condition.fact: unknown fact \"lab.Sodium\"");
		});
		assertThat(decision.matchedRules()).extracting("ruleId").containsExactly("HR-HIGH");
		assertThat(decision.summary()).contains("1 rule was skipped because the rule definition is invalid");
	}

	@Test
	void matchedRulesAreListedInPriorityOrder() {
		ClinicalRule lowPriority = rule(2L, "LOW-PRIORITY", 90, "{\"fact\": \"vitals.heartRate\", \"operator\": \"gt\", \"value\": 50}");
		rules(tachycardiaRule(), lowPriority);
		facts(new PatientFacts().withNumber("vitals.heartRate", new BigDecimal("120"), NOW));

		assertThat(service.requestDecision(1L).matchedRules()).extracting("ruleId").containsExactly("HR-HIGH",
				"LOW-PRIORITY");
	}

	@Test
	void noEnabledRulesIsExplained() {
		rules();
		facts(new PatientFacts());

		DecisionResponse decision = service.requestDecision(1L);

		assertThat(decision.rulesEvaluated()).isZero();
		assertThat(decision.summary()).isEqualTo("No active rules are configured, so nothing was evaluated.");
	}

	@Test
	void unknownPatientGives404AndNothingIsStored() {
		given(patientRepository.findById(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.requestDecision(99L)).isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> service.findDecisions(99L)).isInstanceOf(ResourceNotFoundException.class);
		verify(decisionRepository, never()).save(any());
	}

	@Test
	void historyAndSingleDecisionComeFromTheRepository() {
		ClinicalDecision stored = new ClinicalDecision();
		stored.setPatient(patient);
		stored.setOutcome(DecisionOutcome.NO_RULES_MATCHED);
		stored.setSummary("x");
		given(decisionRepository.findByPatientIdOrderByRequestedAtDescIdDesc(1L)).willReturn(List.of(stored));
		given(decisionRepository.findByIdAndPatientId(5L, 1L)).willReturn(Optional.of(stored));
		given(decisionRepository.findByIdAndPatientId(6L, 1L)).willReturn(Optional.empty());

		assertThat(service.findDecisions(1L)).hasSize(1);
		assertThat(service.findDecision(1L, 5L).outcome()).isEqualTo(DecisionOutcome.NO_RULES_MATCHED);
		assertThatThrownBy(() -> service.findDecision(1L, 6L)).isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Decision with id 6 was not found");
	}

	private void rules(ClinicalRule... rules) {
		given(ruleRepository.findByEnabledTrueOrderByPriorityAscIdAsc()).willReturn(List.of(rules));
	}

	private void facts(PatientFacts facts) {
		given(factsService.collect(eq(patient), eq(NOW))).willReturn(facts);
	}

	private static ClinicalRule tachycardiaRule() {
		return rule(1L, "HR-HIGH", 30, "{\"fact\": \"vitals.heartRate\", \"operator\": \"gt\", \"value\": 100}");
	}

	private static ClinicalRule rule(Long id, String code, int priority, String conditionJson) {
		ClinicalRule rule = new ClinicalRule();
		ReflectionTestUtils.setField(rule, "id", id);
		rule.setRuleCode(code);
		rule.setName(code + " rule");
		rule.setSeverity(Severity.MEDIUM);
		rule.setPriority(priority);
		rule.setEnabled(true);
		rule.setConditionJson(conditionJson);
		rule.setRecommendedActions(List.of("Re-check the heart rate at rest"));
		return rule;
	}

	private static Patient patient(Long id) {
		Patient patient = new Patient();
		ReflectionTestUtils.setField(patient, "id", id);
		patient.setFullName("Test Patient");
		patient.setDateOfBirth(LocalDate.of(1970, 1, 1));
		patient.setGender(Gender.FEMALE);
		return patient;
	}

}
