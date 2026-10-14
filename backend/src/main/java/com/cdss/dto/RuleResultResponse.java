package com.cdss.dto;

import java.util.List;

import com.cdss.entity.DecisionRuleResult;
import com.cdss.entity.Severity;

/**
 * One rule in a decision. For a matched rule, {@code reasons} lists the
 * observations that met the rule's conditions; for insufficient data it lists
 * what was missing; for an invalid rule it lists the problems found.
 */
public record RuleResultResponse(String ruleId, String name, Severity severity, int priority, List<String> reasons,
		List<String> recommendedActions) {

	public static RuleResultResponse from(DecisionRuleResult result) {
		return new RuleResultResponse(result.getRuleCode(), result.getRuleName(), result.getSeverity(),
				result.getPriority(), result.getReasons(), result.getRecommendedActions());
	}

}
