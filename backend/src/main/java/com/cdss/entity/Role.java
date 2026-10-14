package com.cdss.entity;

/**
 * What a user may do. ADMIN can do everything a CLINICIAN can, and also
 * manages the knowledge base and clinical rules and can delete patients
 * (see SecurityConfig).
 */
public enum Role {
	ADMIN,
	CLINICIAN
}
