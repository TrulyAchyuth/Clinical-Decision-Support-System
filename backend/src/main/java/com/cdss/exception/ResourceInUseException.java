package com.cdss.exception;

/** Thrown when a record cannot be deleted because other records refer to it (HTTP 409). */
public class ResourceInUseException extends RuntimeException {

	public ResourceInUseException(String message) {
		super(message);
	}

}
