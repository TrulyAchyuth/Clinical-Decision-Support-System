package com.cdss.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.cdss.ruleengine.RuleError;
import com.cdss.ruleengine.RuleValidationException;

/**
 * Turns exceptions into JSON error responses (RFC 9457 Problem Details).
 * Spring's base class already handles malformed JSON, wrong parameter types
 * and unknown URLs; this class adds validation details, invalid rules,
 * failed logins (401), 404, 409 (including status changes that are not
 * allowed and unacknowledged medication safety warnings) and a generic 500 that never exposes
 * internal details. 401/403 from the security filters are written by
 * {@code com.cdss.config.SecurityErrorResponses}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ResourceNotFoundException.class)
	ProblemDetail handleNotFound(ResourceNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(DuplicateResourceException.class)
	ProblemDetail handleDuplicate(DuplicateResourceException ex) {
		return problem(HttpStatus.CONFLICT, ex.getMessage());
	}

	@ExceptionHandler(ResourceInUseException.class)
	ProblemDetail handleInUse(ResourceInUseException ex) {
		return problem(HttpStatus.CONFLICT, ex.getMessage());
	}

	@ExceptionHandler(StatusChangeNotAllowedException.class)
	ProblemDetail handleStatusChange(StatusChangeNotAllowedException ex) {
		return problem(HttpStatus.CONFLICT, ex.getMessage());
	}

	/** Lists the safety-check warnings that must be acknowledged before the medication can be saved. */
	@ExceptionHandler(SafetyAcknowledgementRequiredException.class)
	ProblemDetail handleSafetyAcknowledgement(SafetyAcknowledgementRequiredException ex) {
		ProblemDetail body = problem(HttpStatus.CONFLICT, ex.getMessage());
		body.setProperty("code", "SAFETY_ACKNOWLEDGEMENT_REQUIRED");
		body.setProperty("unacknowledgedWarnings", ex.getUnacknowledgedWarnings());
		body.setProperty("safetyCheck", ex.getSafetyCheck());
		return body;
	}

	@ExceptionHandler(InvalidRequestException.class)
	ProblemDetail handleInvalidRequest(InvalidRequestException ex) {
		return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	/** Failed login. The same message for an unknown user, a wrong password or a disabled account. */
	@ExceptionHandler(AuthenticationException.class)
	ProblemDetail handleFailedLogin(AuthenticationException ex) {
		return problem(HttpStatus.UNAUTHORIZED, "Invalid username or password");
	}

	/** Invalid rule JSON: lists each problem by its location in the rule. */
	@ExceptionHandler(RuleValidationException.class)
	ProblemDetail handleInvalidRule(RuleValidationException ex) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (RuleError error : ex.getErrors()) {
			errors.merge(error.path(), error.message(), (first, second) -> first + "; " + second);
		}
		ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Invalid rule");
		body.setProperty("errors", errors);
		return body;
	}

	// Safety net for unique/check constraints not caught by the services.
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
		log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getClass().getSimpleName());
		return problem(HttpStatus.CONFLICT, "The request conflicts with existing data");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		for (ObjectError error : ex.getBindingResult().getGlobalErrors()) {
			errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage());
		}
		ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Validation failed");
		body.setProperty("errors", errors);
		return ResponseEntity.badRequest().body(body);
	}

	private static ProblemDetail problem(HttpStatus status, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(status.getReasonPhrase());
		return problem;
	}

}
