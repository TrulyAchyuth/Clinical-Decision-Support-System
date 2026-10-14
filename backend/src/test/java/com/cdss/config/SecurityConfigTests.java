package com.cdss.config;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.cdss.controller.PatientController;
import com.cdss.service.PatientService;

/** The /api endpoints are closed unless cdss.security.open-api=true (dev profile only). */
class SecurityConfigTests {

	@Nested
	@WebMvcTest(PatientController.class)
	@Import(SecurityConfig.class)
	class Default {

		@Autowired
		private MockMvc mvc;

		@MockitoBean
		private PatientService patientService;

		@Test
		void apiRequiresAuthentication() throws Exception {
			mvc.perform(get("/api/patients")).andExpect(status().isUnauthorized());
		}

	}

	@Nested
	@WebMvcTest(PatientController.class)
	@Import(SecurityConfig.class)
	@TestPropertySource(properties = "cdss.security.open-api=true")
	class OpenApiForLocalDevelopment {

		@Autowired
		private MockMvc mvc;

		@MockitoBean
		private PatientService patientService;

		@Test
		void apiIsReachableWithoutLogin() throws Exception {
			given(patientService.findAll(null)).willReturn(List.of());

			mvc.perform(get("/api/patients")).andExpect(status().isOk());
		}

	}

}
