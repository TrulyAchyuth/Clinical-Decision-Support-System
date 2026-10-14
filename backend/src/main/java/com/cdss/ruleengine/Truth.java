package com.cdss.ruleengine;

/**
 * Three-valued result. {@code UNKNOWN} means required data was missing, so the
 * condition could be neither confirmed nor ruled out.
 */
public enum Truth {
	TRUE,
	FALSE,
	UNKNOWN
}
