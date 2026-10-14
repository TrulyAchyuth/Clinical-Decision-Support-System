package com.cdss.dto;

import java.util.List;

import com.cdss.entity.Severity;

/**
 * A rule as clinicians see it: what it checks in plain English, without the
 * JSON definition (which only administrators can read or change).
 */
public record RuleSummaryResponse(Long id, String ruleId, String name, String description, Severity severity,
		int priority, boolean enabled, boolean valid, String conditionSummary, List<String> recommendedActions) {
}
