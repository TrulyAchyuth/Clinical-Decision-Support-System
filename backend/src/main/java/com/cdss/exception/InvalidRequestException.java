package com.cdss.exception;

/** Thrown when a request is well-formed but refers to something invalid (HTTP 400). */
public class InvalidRequestException extends RuntimeException {

	public InvalidRequestException(String message) {
		super(message);
	}

}
