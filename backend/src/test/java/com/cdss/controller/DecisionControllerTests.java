package com.cdss.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.cdss.config.SecurityConfig;
import com.cdss.dto.DecisionResponse;
import com.cdss.dto.RuleResultResponse;
import com.cdss.entity.DecisionOutcome;
import com.cdss.entity.Severity;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.service.DecisionService;

@WebMvcTest(DecisionController.class)
@Import(SecurityConfig.class)
@WithMockUser
class DecisionControllerTests {

	private static final Instant TIME = Instant.parse("2026-10-13T12:00:00Z");

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private DecisionService decisionService;

	@Test
	void requestingADecisionReturns201WithMatchesReasonsAndActions() throws Exception {
		given(decisionService.requestDecision(1L)).willReturn(matchedDecision());

		mvc.perform(post("/api/patients/1/decisions"))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "http://localhost/api/patients/1/decisions/12"))
			.andExpect(jsonPath("$.outcome").value("RULES_MATCHED"))
			.andExpect(jsonPath("$.matchedRules[0].ruleId").value("HR-HIGH"))
			.andExpect(jsonPath("$.matchedRules[0].severity").value("MEDIUM"))
			.andExpect(jsonPath("$.matchedRules[0].reasons[0]").value(startsWith("Heart rate is 118")))
			.andExpect(jsonPath("$.matchedRules[0].recommendedActions[0]").value("Re-check the heart rate at rest"))
			.andExpect(jsonPath("$.insufficientData[0].reasons[0]").value("No Fasting plasma glucose result in the last 72 hours"))
			.andExpect(jsonPath("$.disclaimer").value(DecisionResponse.DISCLAIMER));
	}

	@Test
	void noMatchIsAClearResult() throws Exception {
		given(decisionService.requestDecision(1L)).willReturn(new DecisionResponse(13L, 1L, TIME,
				DecisionOutcome.NO_RULES_MATCHED,
				"No active rule matched the recorded data. This does not rule out any condition.", 5, 5, List.of(),
				List.of(), List.of(), DecisionResponse.DISCLAIMER));

		mvc.perform(post("/api/patients/1/decisions"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.outcome").value("NO_RULES_MATCHED"))
			.andExpect(jsonPath("$.matchedRules").isEmpty())
			.andExpect(jsonPath("$.rulesNotMatched").value(5))
			.andExpect(jsonPath("$.summary").value(containsString("does not rule out")));
	}

	@Test
	void unknownPatientReturns404AndBadIdReturns400() throws Exception {
		given(decisionService.requestDecision(99L)).willThrow(new ResourceNotFoundException("Patient", 99L));

		mvc.perform(post("/api/patients/99/decisions"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Patient with id 99 was not found"));
		mvc.perform(post("/api/patients/abc/decisions")).andExpect(status().isBadRequest());
	}

	@Test
	void historyAndSingleDecision() throws Exception {
		given(decisionService.findDecisions(1L)).willReturn(List.of(matchedDecision()));
		given(decisionService.findDecision(1L, 12L)).willReturn(matchedDecision());
		given(decisionService.findDecision(1L, 77L)).willThrow(new ResourceNotFoundException("Decision", 77L));

		mvc.perform(get("/api/patients/1/decisions"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(12));
		mvc.perform(get("/api/patients/1/decisions/12")).andExpect(status().isOk());
		mvc.perform(get("/api/patients/1/decisions/77")).andExpect(status().isNotFound());
	}

	private static DecisionResponse matchedDecision() {
		RuleResultResponse matched = new RuleResultResponse("HR-HIGH", "Tachycardia", Severity.MEDIUM, 30,
				List.of("Heart rate is 118 beats/min (recorded 2026-10-13 11:50 UTC), which is greater than 100 beats/min."),
				List.of("Re-check the heart rate at rest"));
		RuleResultResponse insufficient = new RuleResultResponse("GLUCOSE-HIGH", "Elevated fasting blood glucose",
				Severity.MEDIUM, 50, List.of("No Fasting plasma glucose result in the last 72 hours"), List.of());
		return new DecisionResponse(12L, 1L, TIME, DecisionOutcome.RULES_MATCHED, "1 of 5 active rules matched.", 5, 3,
				List.of(matched), List.of(insufficient), List.of(), DecisionResponse.DISCLAIMER);
	}

}
