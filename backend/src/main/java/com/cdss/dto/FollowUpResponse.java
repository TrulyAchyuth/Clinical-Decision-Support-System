package com.cdss.dto;

import java.time.Instant;

import com.cdss.entity.FollowUp;
import com.cdss.entity.FollowUpStatus;

/**
 * A follow-up. {@code timing} is calculated when it is read: OVERDUE,
 * UPCOMING (within the configured window) or LATER for SCHEDULED follow-ups;
 * COMPLETED or CANCELLED otherwise, so those are never overdue.
 */
public record FollowUpResponse(Long id, Long patientId, String patientName, Instant scheduledAt, String reason,
		String notes, FollowUpStatus status, String timing, int rescheduleCount, String outcomeNotes,
		Instant statusChangedAt, String statusChangedBy, String createdBy, Instant createdAt) {

	public static FollowUpResponse from(FollowUp followUp, String timing) {
		return new FollowUpResponse(followUp.getId(), followUp.getPatient().getId(),
				followUp.getPatient().getFullName(), followUp.getScheduledAt(), followUp.getReason(),
				followUp.getNotes(), followUp.getStatus(), timing, followUp.getRescheduleCount(),
				followUp.getOutcomeNotes(), followUp.getStatusChangedAt(), followUp.getStatusChangedBy(),
				followUp.getCreatedBy(), followUp.getCreatedAt());
	}

}
