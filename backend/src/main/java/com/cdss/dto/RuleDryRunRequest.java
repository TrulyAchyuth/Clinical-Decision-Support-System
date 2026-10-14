package com.cdss.dto;

import java.util.Map;

import jakarta.validation.constraints.NotNull;

/** A rule condition (not saved) to try out against one patient's current data. */
public record RuleDryRunRequest(@NotNull Map<String, Object> condition, @NotNull Long patientId) {
}
