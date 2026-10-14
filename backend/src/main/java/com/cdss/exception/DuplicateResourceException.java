package com.cdss.exception;

/** Thrown when a create or update would duplicate a unique value (HTTP 409). */
public class DuplicateResourceException extends RuntimeException {

	public DuplicateResourceException(String message) {
		super(message);
	}

}
