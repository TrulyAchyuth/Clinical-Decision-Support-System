package com.cdss.exception;

import java.util.List;

import com.cdss.dto.SafetyCheckResponse;

/**
 * Thrown when a medication is added while the safety check has warnings the
 * clinician has not acknowledged (HTTP 409). The response includes the check,
 * so the warnings can be shown and reviewed.
 */
public class SafetyAcknowledgementRequiredException extends RuntimeException {

	private final transient SafetyCheckResponse safetyCheck;

	private final List<String> unacknowledgedWarnings;

	public SafetyAcknowledgementRequiredException(String message, SafetyCheckResponse safetyCheck,
			List<String> unacknowledgedWarnings) {
		super(message);
		this.safetyCheck = safetyCheck;
		this.unacknowledgedWarnings = List.copyOf(unacknowledgedWarnings);
	}

	public SafetyCheckResponse getSafetyCheck() {
		return safetyCheck;
	}

	public List<String> getUnacknowledgedWarnings() {
		return unacknowledgedWarnings;
	}

}
