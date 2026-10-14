package com.cdss.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.cdss.config.SecurityConfig;
import com.cdss.dto.FactResponse;
import com.cdss.dto.RuleRequest;
import com.cdss.dto.RuleResponse;
import com.cdss.dto.RuleValidationResponse;
import com.cdss.entity.Severity;
import com.cdss.exception.DuplicateResourceException;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.ruleengine.FactType;
import com.cdss.ruleengine.RuleError;
import com.cdss.ruleengine.RuleValidationException;
import com.cdss.service.ClinicalRuleService;

@WebMvcTest(ClinicalRuleController.class)
@Import(SecurityConfig.class)
@WithMockUser
class ClinicalRuleControllerTests {

	private static final String RULE = """
			{"ruleId": "HR-HIGH", "name": "Tachycardia", "description": "Educational example", "severity": "MEDIUM",
			 "priority": 30, "condition": {"fact": "vitals.heartRate", "operator": "gt", "value": 100},
			 "recommendedActions": ["Re-check the heart rate at rest"]}
			""";

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ClinicalRuleService ruleService;

	@Test
	void createReturns201WithTheStoredRule() throws Exception {
		given(ruleService.create(any(RuleRequest.class))).willReturn(response());

		mvc.perform(post("/api/rules").contentType(MediaType.APPLICATION_JSON).content(RULE))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "http://localhost/api/rules/3"))
			.andExpect(jsonPath("$.ruleId").value("HR-HIGH"))
			.andExpect(jsonPath("$.condition.operator").value("gt"))
			.andExpect(jsonPath("$.recommendedActions[0]").value("Re-check the heart rate at rest"));
	}

	@Test
	void invalidConditionReturns400WithEachProblem() throws Exception {
		given(ruleService.create(any(RuleRequest.class))).willThrow(new RuleValidationException(List.of(
				new RuleError("condition.fact", "unknown fact \"vitals.pulse\""),
				new RuleError("condition.operator", "must be one of gt, gte, lt, lte, eq, neq, contains"))));

		mvc.perform(post("/api/rules").contentType(MediaType.APPLICATION_JSON).content(RULE))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Invalid rule"))
			.andExpect(jsonPath("$.errors['condition.fact']").value("unknown fact \"vitals.pulse\""))
			.andExpect(jsonPath("$.errors['condition.operator']").exists());
	}

	@Test
	void missingRuleFieldsReturn400() throws Exception {
		mvc.perform(post("/api/rules").contentType(MediaType.APPLICATION_JSON)
				.content("{\"ruleId\": \"bad id!\", \"priority\": 0, \"recommendedActions\": []}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.ruleId").exists())
			.andExpect(jsonPath("$.errors.name").exists())
			.andExpect(jsonPath("$.errors.severity").exists())
			.andExpect(jsonPath("$.errors.priority").exists())
			.andExpect(jsonPath("$.errors.condition").exists())
			.andExpect(jsonPath("$.errors.recommendedActions").exists());
		verify(ruleService, never()).create(any());
	}

	@Test
	void validateReturns200WithResult() throws Exception {
		given(ruleService.validate(any(RuleRequest.class)))
			.willReturn(new RuleValidationResponse(false, List.of(new RuleError("condition.value", "is required"))));

		mvc.perform(post("/api/rules/validate").contentType(MediaType.APPLICATION_JSON).content(RULE))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.valid").value(false))
			.andExpect(jsonPath("$.errors[0].path").value("condition.value"));
	}

	@Test
	void factsListDescribesAllowedOperators() throws Exception {
		given(ruleService.facts()).willReturn(List.of(new FactResponse("vitals.heartRate", "Heart rate", "beats/min",
				FactType.NUMBER, List.of("gt", "gte", "lt", "lte", "eq", "neq"))));

		mvc.perform(get("/api/rules/facts"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("vitals.heartRate"))
			.andExpect(jsonPath("$[0].operators.length()").value(6));
	}

	@Test
	void listGetUpdateDeleteAndMissing() throws Exception {
		given(ruleService.findAll()).willReturn(List.of(response()));
		given(ruleService.update(eq(3L), any(RuleRequest.class))).willReturn(response());
		given(ruleService.findById(9L)).willThrow(new ResourceNotFoundException("Rule", 9L));

		mvc.perform(get("/api/rules")).andExpect(status().isOk()).andExpect(jsonPath("$[0].priority").value(30));
		mvc.perform(put("/api/rules/3").contentType(MediaType.APPLICATION_JSON).content(RULE)).andExpect(status().isOk());
		mvc.perform(get("/api/rules/9")).andExpect(status().isNotFound());
		mvc.perform(delete("/api/rules/3")).andExpect(status().isNoContent());
		verify(ruleService).delete(3L);
	}

	@Test
	void duplicateRuleIdReturns409() throws Exception {
		willThrow(new DuplicateResourceException("A rule with ruleId 'HR-HIGH' already exists")).given(ruleService)
			.create(any(RuleRequest.class));

		mvc.perform(post("/api/rules").contentType(MediaType.APPLICATION_JSON).content(RULE))
			.andExpect(status().isConflict());
	}

	private static RuleResponse response() {
		return new RuleResponse(3L, "HR-HIGH", "Tachycardia", "Educational example", Severity.MEDIUM, 30, true,
				Map.of("fact", "vitals.heartRate", "operator", "gt", "value", 100),
				List.of("Re-check the heart rate at rest"), Instant.parse("2026-10-13T08:00:00Z"),
				Instant.parse("2026-10-13T08:00:00Z"));
	}

}
