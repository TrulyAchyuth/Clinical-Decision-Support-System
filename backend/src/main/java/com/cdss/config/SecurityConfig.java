package com.cdss.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Deny by default; only the health endpoint is public.
 * <p>
 * Until login is added (step F), the {@code dev} profile sets
 * {@code cdss.security.open-api=true} so the {@code /api} endpoints can be
 * used locally. Any other profile keeps them closed.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			@Value("${cdss.security.open-api:false}") boolean openApi) throws Exception {
		if (openApi) {
			log.warn("cdss.security.open-api=true: all /api endpoints are open without login (local development only)");
		}
		http
			// JSON API; CSRF is covered by a SameSite=Strict session cookie once login exists (see ARCHITECTURE.md).
			.csrf(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests((authorize) -> {
				authorize.requestMatchers(HttpMethod.GET, "/api/health").permitAll();
				authorize.requestMatchers("/error").permitAll();
				if (openApi) {
					authorize.requestMatchers("/api/**").permitAll();
				}
				authorize.anyRequest().authenticated();
			})
			.exceptionHandling((exceptions) -> exceptions
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
		return http.build();
	}

}
