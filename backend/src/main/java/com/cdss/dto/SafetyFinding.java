package com.cdss.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * One result of a medication safety check. Fields that the source does not
 * provide are null; the application never fills them in itself.
 *
 * @param code stable identifier, sent back in {@code acknowledgedWarnings}. It
 * ends with a fingerprint of the finding's content, so an acknowledgement only
 * covers the finding exactly as it was reviewed: if the finding changes
 * materially (e.g. the allergy severity is edited) it gets a new code and must
 * be reviewed again
 * @param checkType ALLERGY, INTERACTION, DUPLICATE_THERAPY or DATA_COMPLETENESS
 * @param resultStatus MATCH_FOUND, NO_MATCH_FOUND or UNCERTAIN (never "safe")
 * @param priority review order only, not clinical urgency: HIGH (something
 * matched), MEDIUM (could not be checked) or LOW (nothing matched)
 * @param title one-line summary of the finding
 * @param severity from the allergy record or interaction row; null when not available
 * @param medications the proposed and existing medications involved
 * @param allergens the recorded allergens involved
 * @param explanation why the finding was reported
 * @param mechanism mechanism or rationale given by the source; null when the source gives none
 * @param recommendedAction the review step suggested by the source, or this
 * check's own review prompt for patient-record findings; null when the source gives none
 * @param source where the information came from
 * @param dataClassification PATIENT_RECORD, ILLUSTRATIVE_SAMPLE or VERIFIED
 * @param sourceVersion version of the source dataset; null when not recorded
 * @param sourceReviewedOn when the source entry was last reviewed; null when not recorded
 * @param reviewStatus clinical review status of the source entry; null for patient records
 * @param requiresReview whether a clinician must review and acknowledge it before saving
 */
public record SafetyFinding(String code, String checkType, String resultStatus, String priority, String title,
		String severity, List<String> medications, List<String> allergens, String explanation, String mechanism,
		String recommendedAction, String source, String dataClassification, String sourceVersion,
		LocalDate sourceReviewedOn, String reviewStatus, boolean requiresReview) {
}
