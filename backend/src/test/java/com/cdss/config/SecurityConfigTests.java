package com.cdss.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.cdss.dto.ComponentStatus;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.service.AllergyService;
import com.cdss.service.ClinicalRecordService;
import com.cdss.service.ClinicalRuleService;
import com.cdss.service.DatabaseHealthChecker;
import com.cdss.service.DecisionService;
import com.cdss.service.DiseaseService;
import com.cdss.service.DrugService;
import com.cdss.service.FollowUpService;
import com.cdss.service.LabTestService;
import com.cdss.service.MedicationSafetyService;
import com.cdss.service.MedicationService;
import com.cdss.service.PatientService;
import com.cdss.service.UserAdminService;

/**
 * Checks the URL rules in SecurityConfig for every endpoint: who gets 401,
 * who gets 403, and who is let through to the controller. Services are mocks,
 * so a request that is let through may still end in 200, 204, 400 or 404;
 * what matters here is that it is not refused with 401 or 403.
 */
@WebMvcTest
@Import({ SecurityConfig.class, SecurityConfigTests.FixedClockConfig.class })
class SecurityConfigTests {

	/** One endpoint (method + example path). */
	record Endpoint(HttpMethod method, String path) {

		@Override
		public String toString() {
			return method + " " + path;
		}

	}

	/** Endpoints for any logged-in user (ADMIN or CLINICIAN). */
	static List<Endpoint> clinicalEndpoints() {
		return List.of(
				new Endpoint(HttpMethod.GET, "/api/auth/me"),
				new Endpoint(HttpMethod.GET, "/api/patients"),
				new Endpoint(HttpMethod.POST, "/api/patients"),
				new Endpoint(HttpMethod.GET, "/api/patients/1"),
				new Endpoint(HttpMethod.PUT, "/api/patients/1"),
				new Endpoint(HttpMethod.GET, "/api/patients/1/vital-signs"),
				new Endpoint(HttpMethod.POST, "/api/patients/1/vital-signs"),
				new Endpoint(HttpMethod.DELETE, "/api/patients/1/vital-signs/2"),
				new Endpoint(HttpMethod.GET, "/api/patients/1/lab-results"),
				new Endpoint(HttpMethod.POST, "/api/patients/1/lab-results"),
				new Endpoint(HttpMethod.DELETE, "/api/patients/1/lab-results/2"),
				new Endpoint(HttpMethod.GET, "/api/patients/1/conditions"),
				new Endpoint(HttpMethod.POST, "/api/patients/1/conditions"),
				new Endpoint(HttpMethod.DELETE, "/api/patients/1/conditions/2"),
				new Endpoint(HttpMethod.POST, "/api/patients/1/decisions"),
				new Endpoint(HttpMethod.GET, "/api/patients/1/decisions"),
				new Endpoint(HttpMethod.GET, "/api/patients/1/decisions/2"),
				new Endpoint(HttpMethod.GET, "/api/drugs"),
				new Endpoint(HttpMethod.GET, "/api/drugs/1"),
				new Endpoint(HttpMethod.GET, "/api/diseases"),
				new Endpoint(HttpMethod.GET, "/api/diseases/1"),
				new Endpoint(HttpMethod.GET, "/api/lab-tests"),
				new Endpoint(HttpMethod.GET, "/api/lab-tests/1"),
				new Endpoint(HttpMethod.GET, "/api/rules/summaries"),
				new Endpoint(HttpMethod.GET, "/api/patients/1/medications"),
				new Endpoint(HttpMethod.GET, "/api/patients/1/medications/2"),
				new Endpoint(HttpMethod.POST, "/api/patients/1/medications/safety-check"),
				new Endpoint(HttpMethod.POST, "/api/patients/1/medications"),
				new Endpoint(HttpMethod.PUT, "/api/patients/1/medications/2"),
				new Endpoint(HttpMethod.POST, "/api/patients/1/medications/2/discontinue"),
				new Endpoint(HttpMethod.POST, "/api/patients/1/medications/2/complete"),
				new Endpoint(HttpMethod.GET, "/api/patients/1/allergies"),
				new Endpoint(HttpMethod.POST, "/api/patients/1/allergies"),
				new Endpoint(HttpMethod.PUT, "/api/patients/1/allergies/2"),
				new Endpoint(HttpMethod.POST, "/api/patients/1/allergies/2/resolve"),
				new Endpoint(HttpMethod.POST, "/api/patients/1/allergies/no-known-allergies"),
				new Endpoint(HttpMethod.GET, "/api/drug-interactions"),
				new Endpoint(HttpMethod.GET, "/api/follow-ups"),
				new Endpoint(HttpMethod.GET, "/api/patients/1/follow-ups"),
				new Endpoint(HttpMethod.POST, "/api/patients/1/follow-ups"),
				new Endpoint(HttpMethod.POST, "/api/patients/1/follow-ups/2/reschedule"),
				new Endpoint(HttpMethod.POST, "/api/patients/1/follow-ups/2/complete"),
				new Endpoint(HttpMethod.POST, "/api/patients/1/follow-ups/2/cancel"));
	}

	/** Endpoints for ADMIN only. */
	static List<Endpoint> adminEndpoints() {
		return List.of(
				new Endpoint(HttpMethod.DELETE, "/api/patients/1"),
				new Endpoint(HttpMethod.POST, "/api/drugs"),
				new Endpoint(HttpMethod.PUT, "/api/drugs/1"),
				new Endpoint(HttpMethod.DELETE, "/api/drugs/1"),
				new Endpoint(HttpMethod.POST, "/api/diseases"),
				new Endpoint(HttpMethod.PUT, "/api/diseases/1"),
				new Endpoint(HttpMethod.DELETE, "/api/diseases/1"),
				new Endpoint(HttpMethod.POST, "/api/lab-tests"),
				new Endpoint(HttpMethod.PUT, "/api/lab-tests/1"),
				new Endpoint(HttpMethod.DELETE, "/api/lab-tests/1"),
				new Endpoint(HttpMethod.POST, "/api/rules/validate"),
				new Endpoint(HttpMethod.POST, "/api/rules"),
				new Endpoint(HttpMethod.PUT, "/api/rules/1"),
				new Endpoint(HttpMethod.DELETE, "/api/rules/1"),
				new Endpoint(HttpMethod.POST, "/api/rules/dry-run"),
				new Endpoint(HttpMethod.POST, "/api/rules/1/activate"),
				new Endpoint(HttpMethod.POST, "/api/rules/1/deactivate"),
				new Endpoint(HttpMethod.GET, "/api/rules"),
				new Endpoint(HttpMethod.GET, "/api/rules/1"),
				new Endpoint(HttpMethod.GET, "/api/rules/facts"),
				new Endpoint(HttpMethod.GET, "/api/admin/users"),
				new Endpoint(HttpMethod.POST, "/api/admin/users"),
				new Endpoint(HttpMethod.POST, "/api/admin/users/2/activate"),
				new Endpoint(HttpMethod.POST, "/api/admin/users/2/deactivate"),
				new Endpoint(HttpMethod.PUT, "/api/admin/users/2/role"));
	}

	static List<Endpoint> protectedEndpoints() {
		return Stream.concat(clinicalEndpoints().stream(), adminEndpoints().stream()).toList();
	}

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private PatientService patientService;

	@MockitoBean
	private ClinicalRecordService clinicalRecordService;

	@MockitoBean
	private DecisionService decisionService;

	@MockitoBean
	private DrugService drugService;

	@MockitoBean
	private DiseaseService diseaseService;

	@MockitoBean
	private LabTestService labTestService;

	@MockitoBean
	private ClinicalRuleService ruleService;

	@MockitoBean
	private DatabaseHealthChecker databaseHealthChecker;

	@MockitoBean
	private MedicationService medicationService;

	@MockitoBean
	private MedicationSafetyService medicationSafetyService;

	@MockitoBean
	private AllergyService allergyService;

	@MockitoBean
	private FollowUpService followUpService;

	@MockitoBean
	private UserAdminService userAdminService;

	@MockitoBean
	private AuthenticationManager authenticationManager;

	@BeforeEach
	void stubs() {
		given(decisionService.requestDecision(anyLong())).willThrow(new ResourceNotFoundException("Patient", 1L));
		given(databaseHealthChecker.check()).willReturn(ComponentStatus.UP);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("protectedEndpoints")
	void withoutLoginEveryProtectedEndpointReturns401(Endpoint endpoint) throws Exception {
		mvc.perform(call(endpoint).with(csrf()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("You are not logged in or your session has ended"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("adminEndpoints")
	void clinicianGets403OnAdminEndpoints(Endpoint endpoint) throws Exception {
		mvc.perform(call(endpoint).with(csrf()).with(clinician()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value("Your role does not allow this action"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("clinicalEndpoints")
	void clinicianIsAllowedOnClinicalEndpoints(Endpoint endpoint) throws Exception {
		mvc.perform(call(endpoint).with(csrf()).with(clinician())).andExpect(notRefused());
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("protectedEndpoints")
	void adminIsAllowedEverywhere(Endpoint endpoint) throws Exception {
		mvc.perform(call(endpoint).with(csrf()).with(admin())).andExpect(notRefused());
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("clinicalEndpoints")
	void loggedInUserWithoutAKnownRoleGets403(Endpoint endpoint) throws Exception {
		mvc.perform(call(endpoint).with(csrf()).with(user("visitor").roles("USER"))).andExpect(status().isForbidden());
	}

	@Test
	void publicEndpointsNeedNoLogin() throws Exception {
		mvc.perform(get("/api/health")).andExpect(status().isOk());
		mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
		mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void pathsOutsideTheApiAreDenied() throws Exception {
		mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
		mvc.perform(get("/actuator/env").with(admin())).andExpect(status().isForbidden());
	}

	@Test
	void changesWithoutCsrfTokenAreRefusedEvenWhenLoggedIn() throws Exception {
		mvc.perform(post("/api/patients").with(clinician()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF"));
		mvc.perform(delete("/api/drugs/1").with(admin()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF"));
		mvc.perform(post("/api/patients").with(clinician()).with(csrf().useInvalidToken())
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF"));
	}

	@Test
	void loginAndLogoutAlsoNeedTheCsrfToken() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\": \"admin\", \"password\": \"whatever1\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF"));
		mvc.perform(post("/api/auth/logout").with(clinician()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF"));
	}

	private static MockHttpServletRequestBuilder call(Endpoint endpoint) {
		MockHttpServletRequestBuilder builder = request(endpoint.method(), endpoint.path());
		if (endpoint.method() == HttpMethod.POST || endpoint.method() == HttpMethod.PUT) {
			builder.contentType(MediaType.APPLICATION_JSON).content("{}");
		}
		return builder;
	}

	private static RequestPostProcessor clinician() {
		return user("clinician").roles("CLINICIAN");
	}

	private static RequestPostProcessor admin() {
		return user("admin").roles("ADMIN");
	}

	private static ResultMatcher notRefused() {
		return (result) -> assertThat(result.getResponse().getStatus()).as("status").isNotIn(401, 403);
	}

	@TestConfiguration
	static class FixedClockConfig {

		@Bean
		Clock clock() {
			return Clock.systemUTC();
		}

	}

}
