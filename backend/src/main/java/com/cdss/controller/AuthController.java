package com.cdss.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cdss.dto.CsrfTokenResponse;
import com.cdss.dto.CurrentUserResponse;
import com.cdss.dto.LoginRequest;
import com.cdss.service.UserAccountService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * Login with a server-side session. Logout ({@code POST /api/auth/logout}) is
 * handled by Spring Security itself; see SecurityConfig.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthenticationManager authenticationManager;

	private final SessionAuthenticationStrategy sessionStrategy;

	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

	private final SessionRegistry sessionRegistry;

	public AuthController(AuthenticationManager authenticationManager, CsrfTokenRepository csrfTokenRepository,
			SessionRegistry sessionRegistry) {
		this.authenticationManager = authenticationManager;
		this.sessionRegistry = sessionRegistry;
		// After login: a new session ID (so a session ID planted before login is useless)
		// and a new CSRF token (so a token seen before login is useless).
		this.sessionStrategy = new CompositeSessionAuthenticationStrategy(List.of(
				new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokenRepository)));
	}

	/** The CSRF token for this session. Fetch it again after login and logout, which replace it. */
	@GetMapping("/csrf")
	public CsrfTokenResponse csrf(CsrfToken token) {
		return new CsrfTokenResponse(token.getHeaderName(), token.getToken());
	}

	/** 200 with the user on success; 401 with the same message for any wrong username or password. */
	@PostMapping("/login")
	public CurrentUserResponse login(@Valid @RequestBody LoginRequest login, HttpServletRequest request,
			HttpServletResponse response) {
		// No stored password can be longer, and BCrypt would reject it with an error.
		if (login.password().getBytes(StandardCharsets.UTF_8).length > UserAccountService.MAX_PASSWORD_BYTES) {
			throw new BadCredentialsException("Password too long");
		}
		Authentication authentication = authenticationManager
			.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(login.username().strip(), login.password()));
		sessionStrategy.onAuthentication(authentication, request, response);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
		// Registered so an administrator can end this session (see UserAdminService).
		sessionRegistry.registerNewSession(request.getSession().getId(), authentication.getPrincipal());
		return CurrentUserResponse.from(authentication);
	}

	@GetMapping("/me")
	public CurrentUserResponse me(Authentication authentication) {
		return CurrentUserResponse.from(authentication);
	}

}
