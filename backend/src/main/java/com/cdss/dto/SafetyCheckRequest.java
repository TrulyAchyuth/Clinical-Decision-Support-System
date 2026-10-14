package com.cdss.dto;

import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /api/patients/{id}/medications/safety-check}: the drug that is being considered. */
public record SafetyCheckRequest(@NotNull Long drugId) {
}
