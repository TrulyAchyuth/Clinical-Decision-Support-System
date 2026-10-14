package com.cdss.entity;

/**
 * An allergy record is ACTIVE until it is resolved; resolved records are kept as history.
 */
public enum AllergyRecordStatus {
	ACTIVE,
	RESOLVED
}
