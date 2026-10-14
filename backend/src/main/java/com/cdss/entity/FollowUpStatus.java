package com.cdss.entity;

/**
 * A follow-up is SCHEDULED until it is COMPLETED or CANCELLED; those are final.
 */
public enum FollowUpStatus {
	SCHEDULED,
	COMPLETED,
	CANCELLED
}
