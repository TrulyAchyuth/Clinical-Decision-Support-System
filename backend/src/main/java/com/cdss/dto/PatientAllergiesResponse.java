package com.cdss.dto;

import java.time.Instant;
import java.util.List;

/**
 * A patient's allergy records and allergy-history status.
 *
 * @param assessment ALLERGIES_RECORDED (at least one active allergy),
 * NO_KNOWN_ALLERGIES (explicitly confirmed, no active allergy) or NOT_ASSESSED
 * (nothing confirmed: this does NOT mean the patient has no allergies)
 */
public record PatientAllergiesResponse(Long patientId, String assessment, String assessmentExplanation,
		Instant noKnownAllergiesConfirmedAt, String noKnownAllergiesConfirmedBy, List<AllergyResponse> allergies) {
}
