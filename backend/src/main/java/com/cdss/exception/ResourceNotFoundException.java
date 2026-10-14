package com.cdss.exception;

/** Thrown when a record with the requested ID does not exist (HTTP 404). */
public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String resource, Long id) {
		super(resource + " with id " + id + " was not found");
	}

}
