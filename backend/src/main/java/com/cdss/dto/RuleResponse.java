package com.cdss.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.cdss.entity.Severity;
import com.cdss.ruleengine.RuleError;

/**
 * A stored rule, including its JSON condition (administrators only).
 * {@code id} is the database ID; {@code ruleId} is the rule's own code, e.g.
 * "BP-HIGH". {@code valid} is false when the stored condition no longer passes
 * validation against the current facts (e.g. a lab test was renamed);
 * {@code validationErrors} then lists the problems.
 */
public record RuleResponse(Long id, String ruleId, String name, String description, Severity severity, int priority,
		boolean enabled, Map<String, Object> condition, List<String> recommendedActions, Instant createdAt,
		Instant updatedAt, boolean valid, List<RuleError> validationErrors) {
}
