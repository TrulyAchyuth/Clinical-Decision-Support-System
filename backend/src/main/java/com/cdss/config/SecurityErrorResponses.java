package com.cdss.config;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes 401 and 403 responses (including sessions ended by an administrator)
 * in the same Problem Details JSON format as GlobalExceptionHandler. A missing
 * or wrong CSRF token is reported with {@code "code": "CSRF"} so the frontend
 * can fetch a new token and retry.
 */
class SecurityErrorResponses implements AuthenticationEntryPoint, AccessDeniedHandler, SessionInformationExpiredStrategy {

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		write(response, HttpStatus.UNAUTHORIZED, "You are not logged in or your session has ended", null);
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
			throws IOException {
		if (exception instanceof CsrfException) {
			write(response, HttpStatus.FORBIDDEN, "Missing or invalid CSRF token", "CSRF");
		}
		else {
			write(response, HttpStatus.FORBIDDEN, "Your role does not allow this action", null);
		}
	}

	/** The session was ended by an administrator (account deactivated or role changed). */
	@Override
	public void onExpiredSessionDetected(SessionInformationExpiredEvent event) throws IOException {
		write(event.getResponse(), HttpStatus.UNAUTHORIZED,
				"Your session has ended because your account was changed by an administrator", null);
	}

	private static void write(HttpServletResponse response, HttpStatus status, String detail, String code)
			throws IOException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", "about:blank");
		body.put("title", status.getReasonPhrase());
		body.put("status", status.value());
		body.put("detail", detail);
		if (code != null) {
			body.put("code", code);
		}
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.getWriter().write(JsonMapper.shared().writeValueAsString(body));
	}

}
