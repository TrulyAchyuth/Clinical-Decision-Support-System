package com.cdss.dto;

import java.time.Instant;
import java.util.List;

import com.cdss.ruleengine.RuleError;

/**
 * Result of a dry run. {@code result} is MATCHED, NOT_MATCHED or
 * INSUFFICIENT_DATA, or null when the condition is invalid ({@code errors}
 * then lists the problems). Nothing is saved and no decision is recorded.
 */
public record RuleDryRunResponse(Long patientId, boolean valid, List<RuleError> errors, String result,
		List<String> reasons, Instant evaluatedAt, String note) {

	public static final String NOTE = "Dry run only: nothing was saved and no decision was recorded. A match is a "
			+ "prompt for clinician review, not a diagnosis.";

}
