package com.cdss.ruleengine;

import java.util.List;
import java.util.stream.Collectors;

/** Thrown when rule JSON is not valid. Lists every problem found. */
public class RuleValidationException extends RuntimeException {

	private final List<RuleError> errors;

	public RuleValidationException(List<RuleError> errors) {
		super(errors.stream().map((error) -> error.path() + ": " + error.message()).collect(Collectors.joining("; ")));
		this.errors = List.copyOf(errors);
	}

	public List<RuleError> getErrors() {
		return errors;
	}

}
