package com.cdss.dto;

import java.time.Instant;
import java.util.List;

import com.cdss.entity.ClinicalDecision;
import com.cdss.entity.DecisionOutcome;
import com.cdss.entity.RuleResultType;

/**
 * A recorded decision.
 *
 * @param rulesEvaluated number of enabled rules checked
 * @param rulesNotMatched rules that were evaluated and did not match
 * @param matchedRules rules whose conditions were met, highest priority first
 * @param insufficientData rules that could not be evaluated because data was missing
 * @param invalidRules rules skipped because their stored JSON is not valid
 */
public record DecisionResponse(Long id, Long patientId, Instant requestedAt, DecisionOutcome outcome, String summary,
		int rulesEvaluated, int rulesNotMatched, List<RuleResultResponse> matchedRules,
		List<RuleResultResponse> insufficientData, List<RuleResultResponse> invalidRules, String disclaimer) {

	public static final String DISCLAIMER = "Educational prototype using synthetic data. Matched rules are prompts "
			+ "for clinician review, not diagnoses. The rules are illustrative and not clinically validated.";

	public static DecisionResponse from(ClinicalDecision decision) {
		List<RuleResultResponse> matched = results(decision, RuleResultType.MATCHED);
		List<RuleResultResponse> insufficient = results(decision, RuleResultType.INSUFFICIENT_DATA);
		List<RuleResultResponse> invalid = results(decision, RuleResultType.INVALID_RULE);
		int notMatched = decision.getRulesEvaluated() - matched.size() - insufficient.size() - invalid.size();
		return new DecisionResponse(decision.getId(), decision.getPatient().getId(), decision.getRequestedAt(),
				decision.getOutcome(), decision.getSummary(), decision.getRulesEvaluated(), notMatched, matched,
				insufficient, invalid, DISCLAIMER);
	}

	private static List<RuleResultResponse> results(ClinicalDecision decision, RuleResultType type) {
		return decision.getRuleResults()
			.stream()
			.filter((result) -> result.getResult() == type)
			.map(RuleResultResponse::from)
			.toList();
	}

}
