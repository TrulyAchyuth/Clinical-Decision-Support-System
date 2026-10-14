package com.cdss.dto;

import java.time.Instant;
import java.util.List;

/**
 * Result of a medication safety check. {@code overallStatus} is WARNINGS
 * (something matched), UNCERTAIN (data missing or not matchable) or
 * NO_ISSUES_FOUND_IN_AVAILABLE_DATA, which does not mean the medication is safe.
 */
public record SafetyCheckResponse(Long patientId, Long drugId, String drugName, String overallStatus,
		boolean requiresAcknowledgement, List<SafetyFinding> findings, Instant checkedAt, String disclaimer) {

	public static final String DISCLAIMER = "Educational prototype. This check only compares the patient's recorded "
			+ "allergies and active medications with a small illustrative sample of drug interactions. It is not a "
			+ "complete or verified interaction database, finding nothing does not mean the medication is safe, and it "
			+ "never prescribes, stops or changes a medication. Clinical judgement is required.";

}
