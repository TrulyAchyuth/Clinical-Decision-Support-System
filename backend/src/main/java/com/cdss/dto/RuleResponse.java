package com.cdss.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.cdss.entity.Severity;

/** A stored rule. {@code id} is the database ID; {@code ruleId} is the rule's own code, e.g. "BP-HIGH". */
public record RuleResponse(Long id, String ruleId, String name, String description, Severity severity, int priority,
		boolean enabled, Map<String, Object> condition, List<String> recommendedActions, Instant createdAt,
		Instant updatedAt) {
}
