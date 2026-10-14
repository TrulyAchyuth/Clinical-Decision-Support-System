package com.cdss.dto;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import com.cdss.entity.Role;

/** The logged-in user, as returned by login and {@code GET /api/auth/me}. */
public record CurrentUserResponse(String username, Role role) {

	public static CurrentUserResponse from(Authentication authentication) {
		Set<String> authorities = authentication.getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.collect(Collectors.toSet());
		Role role = Arrays.stream(Role.values())
			.filter((candidate) -> authorities.contains("ROLE_" + candidate.name()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Logged-in user has no known role"));
		return new CurrentUserResponse(authentication.getName(), role);
	}

}
