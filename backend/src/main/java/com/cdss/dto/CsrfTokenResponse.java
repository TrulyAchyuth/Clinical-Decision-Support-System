package com.cdss.dto;

/** Result of {@code GET /api/auth/csrf}: send {@code token} in the {@code headerName} header on every change. */
public record CsrfTokenResponse(String headerName, String token) {
}
