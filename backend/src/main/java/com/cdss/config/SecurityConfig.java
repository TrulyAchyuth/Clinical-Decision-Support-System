package com.cdss.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Who may call which endpoint. Deny by default:
 * <ul>
 * <li>public: {@code GET /api/health}, {@code GET /api/auth/csrf},
 * {@code POST /api/auth/login};</li>
 * <li>ADMIN only: user management ({@code /api/admin/**}); deleting
 * patients; creating, changing or deleting drugs, diseases and lab tests;
 * everything under {@code /api/rules} (the JSON definitions, facts,
 * validation, activation, deactivation and dry runs) except
 * {@code GET /api/rules/summaries};</li>
 * <li>ADMIN or CLINICIAN: every other {@code /api} endpoint, including patient
 * medications, allergies, the medication safety check and follow-ups;</li>
 * <li>anything outside {@code /api}: denied.</li>
 * </ul>
 * Login state lives in the server-side HTTP session. Every POST, PUT and
 * DELETE (login and logout included) must carry the session's CSRF token in
 * the {@code X-CSRF-TOKEN} header; the frontend gets it from
 * {@code GET /api/auth/csrf}.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

	private static final String[] KNOWLEDGE_BASE = { "/api/drugs/**", "/api/diseases/**", "/api/lab-tests/**" };

	/** CSRF tokens are kept in the HTTP session, so JavaScript never reads them from a cookie. */
	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		return new HttpSessionCsrfTokenRepository();
	}

	/** Open sessions per user, so deactivating a user or changing their role can end their sessions. */
	@Bean
	SessionRegistry sessionRegistry() {
		return new SessionRegistryImpl();
	}

	/** Tells the session registry when a session ends (logout or timeout). */
	@Bean
	HttpSessionEventPublisher httpSessionEventPublisher() {
		return new HttpSessionEventPublisher();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokenRepository,
			SessionRegistry sessionRegistry) throws Exception {
		SecurityErrorResponses errorResponses = new SecurityErrorResponses();
		http
			.csrf((csrf) -> csrf.csrfTokenRepository(csrfTokenRepository))
			.authorizeHttpRequests((authorize) -> authorize
				.requestMatchers(HttpMethod.GET, "/api/health", "/api/auth/csrf").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
				.requestMatchers("/error").permitAll()
				.requestMatchers("/api/admin/**").hasRole("ADMIN")
				.requestMatchers(HttpMethod.GET, "/api/rules/summaries").hasAnyRole("ADMIN", "CLINICIAN")
				.requestMatchers("/api/rules/**").hasRole("ADMIN")
				.requestMatchers(HttpMethod.DELETE, "/api/patients/{id}").hasRole("ADMIN")
				.requestMatchers(HttpMethod.POST, KNOWLEDGE_BASE).hasRole("ADMIN")
				.requestMatchers(HttpMethod.PUT, KNOWLEDGE_BASE).hasRole("ADMIN")
				.requestMatchers(HttpMethod.DELETE, KNOWLEDGE_BASE).hasRole("ADMIN")
				.requestMatchers("/api/**").hasAnyRole("ADMIN", "CLINICIAN")
				.anyRequest().denyAll())
			// Logout ends the session; it answers 204 instead of redirecting.
			.logout((logout) -> logout
				.logoutUrl("/api/auth/logout")
				.deleteCookies("JSESSIONID")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			// Sessions are registered at login (AuthController); an expired one answers 401.
			.sessionManagement((sessions) -> sessions.maximumSessions(-1)
				.sessionRegistry(sessionRegistry)
				.expiredSessionStrategy(errorResponses))
			// A JSON API never redirects after login, so refused requests need not be remembered.
			.requestCache((cache) -> cache.disable())
			.exceptionHandling((exceptions) -> exceptions
				.authenticationEntryPoint(errorResponses)
				.accessDeniedHandler(errorResponses));
		return http.build();
	}

}
