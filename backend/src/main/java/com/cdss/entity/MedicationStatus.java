package com.cdss.entity;

/**
 * A patient medication course. ACTIVE can become DISCONTINUED or COMPLETED; those are final.
 */
public enum MedicationStatus {
	ACTIVE,
	DISCONTINUED,
	COMPLETED
}
