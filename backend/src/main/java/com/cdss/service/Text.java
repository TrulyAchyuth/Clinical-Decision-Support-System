package com.cdss.service;

/** Small helpers for cleaning up text received from clients. */
final class Text {

	private Text() {
	}

	/** Trims whitespace; returns {@code null} for null or blank input. */
	static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.strip();
		return trimmed.isEmpty() ? null : trimmed;
	}

}
