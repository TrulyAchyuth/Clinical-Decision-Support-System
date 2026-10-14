package com.cdss.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.cdss.dto.RuleRequest;
import com.cdss.dto.RuleResponse;
import com.cdss.dto.RuleSummaryResponse;
import com.cdss.entity.ClinicalRule;
import com.cdss.entity.LabTest;
import com.cdss.entity.Severity;
import com.cdss.exception.DuplicateResourceException;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.repository.ClinicalRuleRepository;
import com.cdss.repository.LabTestRepository;
import com.cdss.ruleengine.RuleValidationException;

class ClinicalRuleServiceTests {

	private final ClinicalRuleRepository ruleRepository = mock(ClinicalRuleRepository.class);

	private final LabTestRepository labTestRepository = mock(LabTestRepository.class);

	private final ClinicalRuleService service = new ClinicalRuleService(ruleRepository, labTestRepository);

	@Test
	void createValidatesAndStoresTheRule() {
		given(labTestRepository.findAllByOrderByNameAsc()).willReturn(List.of(labTest("Fasting plasma glucose", "mmol/L")));
		given(ruleRepository.save(any(ClinicalRule.class))).willAnswer((invocation) -> invocation.getArgument(0));

		RuleResponse created = service.create(request(" glucose-high ",
				Map.of("fact", "lab.Fasting plasma glucose", "operator", "gte", "value", 7.0), null));

		ArgumentCaptor<ClinicalRule> saved = ArgumentCaptor.forClass(ClinicalRule.class);
		verify(ruleRepository).save(saved.capture());
		assertThat(saved.getValue().getRuleCode()).isEqualTo("GLUCOSE-HIGH");
		assertThat(saved.getValue().isEnabled()).isTrue();
		assertThat(saved.getValue().getConditionJson()).contains("\"lab.Fasting plasma glucose\"");
		assertThat(created.condition()).containsEntry("operator", "gte");
		assertThat(created.recommendedActions()).containsExactly("Repeat the test");
	}

	@Test
	void invalidConditionIsRejectedAndNotSaved() {
		given(labTestRepository.findAllByOrderByNameAsc()).willReturn(List.of());

		assertThatThrownBy(() -> service.create(request("BAD", Map.of("fact", "vitals.pulse", "operator", "gt", "value", 1), true)))
			.isInstanceOf(RuleValidationException.class)
			.hasMessageContaining("unknown fact \"vitals.pulse\"");
		verify(ruleRepository, never()).save(any());
	}

	@Test
	void duplicateRuleIdIsRejected() {
		given(ruleRepository.existsByRuleCodeIgnoreCase("HR-HIGH")).willReturn(true);

		assertThatThrownBy(() -> service.create(request("hr-high", Map.of("fact", "vitals.heartRate", "operator", "gt",
				"value", 100), true)))
			.isInstanceOf(DuplicateResourceException.class)
			.hasMessage("A rule with ruleId 'HR-HIGH' already exists");
	}

	@Test
	void validateReportsProblemsWithoutSaving() {
		given(labTestRepository.findAllByOrderByNameAsc()).willReturn(List.of());

		var invalid = service.validate(request("X", Map.of("all", List.of()), true));
		var valid = service.validate(request("X", Map.of("fact", "vitals.heartRate", "operator", "gt", "value", 100), true));

		assertThat(invalid.valid()).isFalse();
		assertThat(invalid.errors()).extracting("path").containsExactly("condition.all");
		assertThat(valid.valid()).isTrue();
		assertThat(valid.errors()).isEmpty();
		verify(ruleRepository, never()).save(any());
	}

	@Test
	void updateAndDeleteOfMissingRuleGive404() {
		given(ruleRepository.findById(9L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(9L, request("X", Map.of(), true)))
			.isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> service.delete(9L)).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void factsIncludeVitalsConditionsAndKnowledgeBaseLabTests() {
		given(labTestRepository.findAllByOrderByNameAsc()).willReturn(List.of(labTest("Potassium", "mmol/L")));

		assertThat(service.facts()).extracting("name").contains("vitals.heartRate", "conditions", "lab.Potassium");
	}

	@Test
	void ruleThatHasBecomeInvalidCannotBeActivatedButCanBeDeactivated() {
		// The rule uses a lab test that has since been removed from the knowledge base.
		ClinicalRule stale = storedRule(7L, "{\"fact\": \"lab.Fasting plasma glucose\", \"operator\": \"gte\", \"value\": 7}", true);
		given(ruleRepository.findById(7L)).willReturn(Optional.of(stale));
		given(labTestRepository.findAllByOrderByNameAsc()).willReturn(List.of());
		given(ruleRepository.saveAndFlush(any(ClinicalRule.class))).willAnswer((invocation) -> invocation.getArgument(0));

		assertThatThrownBy(() -> service.activate(7L)).isInstanceOf(RuleValidationException.class);

		assertThat(service.deactivate(7L).enabled()).isFalse();
		stale.setEnabled(false);
		assertThatThrownBy(() -> service.activate(7L)).isInstanceOf(RuleValidationException.class);
		assertThat(stale.isEnabled()).isFalse();
	}

	@Test
	void summariesDescribeEachRuleAndFlagInvalidOnesWithoutJson() {
		given(ruleRepository.findAllByOrderByPriorityAscIdAsc()).willReturn(List.of(
				storedRule(1L, "{\"fact\": \"vitals.heartRate\", \"operator\": \"gt\", \"value\": 100}", true),
				storedRule(2L, "{\"fact\": \"lab.Removed test\", \"operator\": \"gt\", \"value\": 1}", true)));
		given(labTestRepository.findAllByOrderByNameAsc()).willReturn(List.of());

		List<RuleSummaryResponse> summaries = service.summaries();

		assertThat(summaries.get(0).conditionSummary()).isEqualTo("Heart rate is greater than 100 beats/min");
		assertThat(summaries.get(0).valid()).isTrue();
		assertThat(summaries.get(1).valid()).isFalse();
		assertThat(summaries.get(1).conditionSummary()).doesNotContain("lab.Removed test");
	}

	@Test
	void listMarksRulesThatNoLongerValidate() {
		given(ruleRepository.findAllByOrderByPriorityAscIdAsc()).willReturn(List.of(
				storedRule(2L, "{\"fact\": \"lab.Removed test\", \"operator\": \"gt\", \"value\": 1}", true)));
		given(labTestRepository.findAllByOrderByNameAsc()).willReturn(List.of());

		RuleResponse response = service.findAll().get(0);
		assertThat(response.valid()).isFalse();
		assertThat(response.validationErrors()).isNotEmpty();
	}

	@Test
	void validRuleCanBeActivatedAndDeactivated() {
		ClinicalRule rule = storedRule(8L, "{\"fact\": \"vitals.heartRate\", \"operator\": \"gt\", \"value\": 100}", false);
		given(ruleRepository.findById(8L)).willReturn(Optional.of(rule));
		given(labTestRepository.findAllByOrderByNameAsc()).willReturn(List.of());
		given(ruleRepository.saveAndFlush(any(ClinicalRule.class))).willAnswer((invocation) -> invocation.getArgument(0));

		assertThat(service.activate(8L).enabled()).isTrue();
		assertThat(service.deactivate(8L).enabled()).isFalse();
		assertThat(rule.getConditionJson()).contains("vitals.heartRate");
	}

	@Test
	void activateAndDeactivateOfMissingRuleGive404() {
		given(ruleRepository.findById(9L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.activate(9L)).isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> service.deactivate(9L)).isInstanceOf(ResourceNotFoundException.class);
	}

	private static ClinicalRule storedRule(Long id, String conditionJson, boolean enabled) {
		ClinicalRule rule = new ClinicalRule();
		ReflectionTestUtils.setField(rule, "id", id);
		rule.setRuleCode("RULE-" + id);
		rule.setName("Stored rule");
		rule.setSeverity(Severity.LOW);
		rule.setPriority(100);
		rule.setEnabled(enabled);
		rule.setConditionJson(conditionJson);
		rule.setRecommendedActions(List.of("Review"));
		return rule;
	}

	private static RuleRequest request(String ruleId, Map<String, Object> condition, Boolean enabled) {
		return new RuleRequest(ruleId, "Test rule", "Educational example", Severity.MEDIUM, 50, enabled, condition,
				List.of(" Repeat the test "));
	}

	private static LabTest labTest(String name, String unit) {
		LabTest labTest = new LabTest();
		ReflectionTestUtils.setField(labTest, "id", 1L);
		labTest.setName(name);
		labTest.setUnit(unit);
		return labTest;
	}

}
