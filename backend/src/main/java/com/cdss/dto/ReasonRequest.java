package com.cdss.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A required reason, e.g. for resolving an allergy or cancelling a follow-up. */
public record ReasonRequest(@NotBlank @Size(max = 255) String reason) {
}
