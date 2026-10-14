package com.cdss.exception;

/**
 * Thrown when a record's status does not allow the requested change, e.g.
 * editing a discontinued medication or completing a cancelled follow-up
 * (HTTP 409).
 */
public class StatusChangeNotAllowedException extends RuntimeException {

	public StatusChangeNotAllowedException(String message) {
		super(message);
	}

}
