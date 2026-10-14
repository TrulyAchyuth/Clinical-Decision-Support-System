package com.cdss.dto;

import java.util.List;
import java.util.Map;

import com.cdss.entity.Severity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A clinical rule as JSON. {@code condition} is checked by the rule parser
 * (see docs/ARCHITECTURE.md for the format); {@code enabled} defaults to true.
 * {@code priority}: 1 is the most important.
 */
public record RuleRequest(
		@NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]*$",
				message = "may contain only letters, digits, '-' and '_'") String ruleId,
		@NotBlank @Size(max = 150) String name,
		@Size(max = 1000) String description,
		@NotNull Severity severity,
		@NotNull @Min(1) @Max(1000) Integer priority,
		Boolean enabled,
		@NotNull Map<String, Object> condition,
		@NotEmpty @Size(max = 10) List<@NotBlank @Size(max = 300) String> recommendedActions) {
}
