package com.cdss.dto;

import java.util.List;

import com.cdss.ruleengine.RuleError;

/** Result of {@code POST /api/rules/validate}. */
public record RuleValidationResponse(boolean valid, List<RuleError> errors) {
}
