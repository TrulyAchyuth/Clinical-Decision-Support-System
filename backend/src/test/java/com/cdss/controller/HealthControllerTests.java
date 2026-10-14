package com.cdss.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.cdss.config.SecurityConfig;
import com.cdss.dto.ComponentStatus;
import com.cdss.service.DatabaseHealthChecker;

@WebMvcTest(HealthController.class)
@Import({ SecurityConfig.class, HealthControllerTests.FixedClockConfig.class })
class HealthControllerTests {

	static final Instant NOW = Instant.parse("2026-01-15T10:30:00Z");

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private DatabaseHealthChecker databaseHealthChecker;

	@Test
	void reportsUpWhenDatabaseIsUp() throws Exception {
		given(databaseHealthChecker.check()).willReturn(ComponentStatus.UP);

		mvc.perform(get("/api/health"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.components.application").value("UP"))
			.andExpect(jsonPath("$.components.database").value("UP"))
			.andExpect(jsonPath("$.timestamp").value("2026-01-15T10:30:00Z"));
	}

	@Test
	void reportsDegradedWith503WhenDatabaseIsDown() throws Exception {
		given(databaseHealthChecker.check()).willReturn(ComponentStatus.DOWN);

		mvc.perform(get("/api/health"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.status").value("DEGRADED"))
			.andExpect(jsonPath("$.components.application").value("UP"))
			.andExpect(jsonPath("$.components.database").value("DOWN"));
	}

	@Test
	void isPubliclyAccessibleWithoutAuthentication() throws Exception {
		given(databaseHealthChecker.check()).willReturn(ComponentStatus.UP);

		mvc.perform(get("/api/health")).andExpect(status().isOk());
	}

	@Test
	void otherEndpointsRequireAuthentication() throws Exception {
		mvc.perform(get("/api/patients")).andExpect(status().isUnauthorized());
		mvc.perform(get("/")).andExpect(status().isUnauthorized());
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfig {

		@Bean
		Clock clock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}

	}

}
