package com.cdss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.cdss.entity.Role;
import com.cdss.entity.UserAccount;
import com.cdss.repository.UserAccountRepository;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

/**
 * End-to-end checks against the real local MySQL database (Flyway migrations,
 * constraints, JSON columns, queries). Opt-in, because they need MySQL:
 *
 * <pre>
 * CDSS_MYSQL_IT=true ./mvnw test
 * </pre>
 *
 * Uses the {@code dev} profile (reads ../.env), so it connects to the local
 * development database and Flyway applies any pending migration to it when
 * the tests start. Each test runs in a transaction that is rolled back, so no
 * test data (including test users) is left in the database. The demo-user
 * passwords are blanked here, so running these tests never creates demo users.
 * Requests run as an ADMIN, who may call every endpoint; role rules are
 * covered by SecurityConfigTests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = { "cdss.demo-users.admin-password=", "cdss.demo-users.clinician-password=" })
@WithMockUser(roles = "ADMIN")
@Transactional
@EnabledIfEnvironmentVariable(named = "CDSS_MYSQL_IT", matches = "true")
class MySqlIntegrationTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private UserAccountRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void allMigrationsAreAppliedAndSampleRulesAreValid() throws Exception {
		Integer applied = jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);
		assertThat(applied).isGreaterThanOrEqualTo(10);

		long patientId = createPatient("Integration Test Patient");
		mvc.perform(post("/api/patients/{id}/decisions", patientId).with(csrf()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.outcome").value("NO_RULES_MATCHED"))
			.andExpect(jsonPath("$.invalidRules").isEmpty())
			.andExpect(jsonPath("$.insufficientData.length()").value(5));
	}

	@Test
	void abnormalObservationsTriggerEverySampleRuleWithReasons() throws Exception {
		long patientId = createPatient("Integration Abnormal Patient");
		addVitalSigns(patientId, Instant.now().minus(10, ChronoUnit.MINUTES),
				"\"systolicBp\": 152, \"diastolicBp\": 96, \"temperature\": 38.6, \"heartRate\": 118, "
						+ "\"respiratoryRate\": 24, \"oxygenSaturation\": 90");
		addGlucose(patientId, "8.2");

		MvcResult result = mvc.perform(post("/api/patients/{id}/decisions", patientId).with(csrf()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.outcome").value("RULES_MATCHED"))
			.andExpect(jsonPath("$.insufficientData").isEmpty())
			.andExpect(jsonPath("$.matchedRules[2].reasons[0]").value(startsWith("Heart rate is 118 beats/min")))
			.andReturn();
		List<String> matched = JsonPath.read(result.getResponse().getContentAsString(), "$.matchedRules[*].ruleId");
		assertThat(matched).containsExactly("SEPSIS-SCREEN", "SPO2-LOW", "HR-HIGH", "BP-HIGH", "GLUCOSE-HIGH");

		// The decision and its rule results are stored and can be read back.
		mvc.perform(get("/api/patients/{id}/decisions", patientId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].matchedRules.length()").value(5))
			.andExpect(jsonPath("$[0].matchedRules[0].recommendedActions.length()").value(3));
	}

	@Test
	void normalObservationsMatchNoRule() throws Exception {
		long patientId = createPatient("Integration Normal Patient");
		addVitalSigns(patientId, Instant.now().minus(5, ChronoUnit.MINUTES),
				"\"systolicBp\": 118, \"diastolicBp\": 76, \"temperature\": 36.8, \"heartRate\": 72, "
						+ "\"respiratoryRate\": 14, \"oxygenSaturation\": 98");
		addGlucose(patientId, "5.0");

		mvc.perform(post("/api/patients/{id}/decisions", patientId).with(csrf()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.outcome").value("NO_RULES_MATCHED"))
			.andExpect(jsonPath("$.matchedRules").isEmpty())
			.andExpect(jsonPath("$.insufficientData").isEmpty())
			.andExpect(jsonPath("$.rulesNotMatched").value(5));
	}

	@Test
	void observationsOlderThanTheLimitAreTreatedAsMissing() throws Exception {
		long patientId = createPatient("Integration Stale Patient");
		addVitalSigns(patientId, Instant.now().minus(2, ChronoUnit.DAYS), "\"heartRate\": 130");

		MvcResult result = mvc.perform(post("/api/patients/{id}/decisions", patientId).with(csrf()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.matchedRules").isEmpty())
			.andReturn();
		List<String> reasons = JsonPath.read(result.getResponse().getContentAsString(),
				"$.insufficientData[?(@.ruleId == 'HR-HIGH')].reasons[0]");
		assertThat(reasons).containsExactly("No heart rate recorded in the last 24 hours");
	}

	@Test
	void deletingAPatientRemovesTheirRecordsAndDecisions() throws Exception {
		long patientId = createPatient("Integration Delete Patient");
		addVitalSigns(patientId, Instant.now(), "\"heartRate\": 80");
		mvc.perform(post("/api/patients/{id}/decisions", patientId).with(csrf())).andExpect(status().isCreated());
		// In the running app each request has its own persistence context; emulate that here,
		// because this test keeps everything in one transaction.
		entityManager.flush();
		entityManager.clear();

		mvc.perform(delete("/api/patients/{id}", patientId).with(csrf())).andExpect(status().isNoContent());
		entityManager.flush();

		assertThat(count("vital_signs", patientId)).isZero();
		assertThat(count("clinical_decisions", patientId)).isZero();
	}

	@Test
	void labTestWithResultsCannotBeDeletedAndUnitsMustMatch() throws Exception {
		long patientId = createPatient("Integration Lab Patient");
		long glucoseId = addGlucose(patientId, "6.1");

		mvc.perform(delete("/api/lab-tests/{id}", glucoseId).with(csrf()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(startsWith("Lab test 'Fasting plasma glucose' has recorded patient results")));
		mvc.perform(post("/api/patients/{id}/lab-results", patientId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"labTestId\": " + glucoseId + ", \"value\": 110, \"unit\": \"mg/dL\", \"measuredAt\": \""
						+ Instant.now().minus(1, ChronoUnit.MINUTES) + "\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void ruleJsonIsValidatedAndStoredInMySql() throws Exception {
		String rule = """
				{"ruleId": "it-hr-low", "name": "Bradycardia (test)", "severity": "LOW", "priority": 900,
				 "condition": {"all": [{"fact": "vitals.heartRate", "operator": "lt", "value": 50},
				                       {"not": {"fact": "conditions", "operator": "contains", "value": "I10"}}]},
				 "recommendedActions": ["Re-check the heart rate"]}
				""";
		mvc.perform(post("/api/rules").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(rule))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.ruleId").value("IT-HR-LOW"))
			.andExpect(jsonPath("$.condition.all.length()").value(2));
		mvc.perform(post("/api/rules").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(rule.replace("it-hr-low", "it-bad").replace("vitals.heartRate", "vitals.pulse")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors['condition.all[0].fact']").value("unknown fact \"vitals.pulse\""));
	}

	@Test
	void storedUserCanLogInWithBcryptPassword() throws Exception {
		UserAccount user = new UserAccount();
		user.setUsername("it-clinician");
		user.setPasswordHash(passwordEncoder.encode("it-password-123"));
		user.setRole(Role.CLINICIAN);
		user.setEnabled(true);
		userRepository.saveAndFlush(user);

		String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = 'it-clinician'", String.class);
		assertThat(hash).startsWith("$2").doesNotContain("it-password-123");
		mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\": \"it-clinician\", \"password\": \"it-password-123\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("CLINICIAN"));
		mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\": \"it-clinician\", \"password\": \"wrong-password\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void medicationsAllergiesSafetyCheckAndFollowUpsWorkAgainstMySql() throws Exception {
		long patientId = createPatient("Integration Safety Patient");
		Long warfarin = jdbc.queryForObject("SELECT MIN(id) FROM drugs WHERE name = 'Warfarin'", Long.class);
		Long ibuprofen = jdbc.queryForObject("SELECT MIN(id) FROM drugs WHERE name = 'Ibuprofen'", Long.class);
		String base = "/api/patients/" + patientId;

		// Allergy history never assessed: uncertain, never "safe".
		mvc.perform(post(base + "/medications/safety-check").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"drugId\": " + warfarin + "}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.overallStatus").value("UNCERTAIN"));
		mvc.perform(post(base + "/allergies/no-known-allergies").with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assessment").value("NO_KNOWN_ALLERGIES"));
		mvc.perform(post(base + "/medications").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(medication(warfarin, List.of(), null)))
			.andExpect(status().isCreated());

		// The illustrative Warfarin + Ibuprofen sample interaction must be acknowledged.
		MvcResult check = mvc.perform(post(base + "/medications/safety-check").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"drugId\": " + ibuprofen + "}"))
			.andExpect(jsonPath("$.overallStatus").value("WARNINGS"))
			.andReturn();
		List<String> codes = JsonPath.read(check.getResponse().getContentAsString(),
				"$.findings[?(@.requiresReview == true)].code");
		mvc.perform(post(base + "/medications").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(medication(ibuprofen, List.of(), null)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SAFETY_ACKNOWLEDGEMENT_REQUIRED"));
		MvcResult added = mvc.perform(post(base + "/medications").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(medication(ibuprofen, codes, "Integration test acknowledgement")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.safetyWarnings.length()").value(codes.size()))
			.andReturn();
		long ibuprofenMedicationId = ((Number) JsonPath.read(added.getResponse().getContentAsString(), "$.id")).longValue();
		mvc.perform(post(base + "/medications/" + ibuprofenMedicationId + "/discontinue").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Integration test\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DISCONTINUED"));
		entityManager.flush();
		assertThat(count("patient_medications", patientId)).isEqualTo(2);

		mvc.perform(post(base + "/follow-ups").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"scheduledAt\": \"" + Instant.now().plus(2, ChronoUnit.DAYS) + "\", \"reason\": \"Review\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.timing").value("UPCOMING"));
		entityManager.flush();
		assertThat(count("follow_ups", patientId)).isEqualTo(1);
	}

	private static String medication(Long drugId, List<String> acknowledged, String reason) {
		return "{\"drugId\": " + drugId + ", \"dose\": 1, \"doseUnit\": \"tablet\", \"route\": \"ORAL\", "
				+ "\"frequency\": \"once daily\", \"startDate\": \"" + LocalDate.now(ZoneOffset.UTC) + "\", "
				+ "\"acknowledgedWarnings\": [" + String.join(",", acknowledged.stream().map((code) -> "\"" + code + "\"").toList())
				+ "], \"overrideReason\": " + (reason == null ? "null" : "\"" + reason + "\"") + "}";
	}

	private long createPatient(String name) throws Exception {
		MvcResult result = mvc.perform(post("/api/patients").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\": \"" + name + "\", \"dateOfBirth\": \"1970-05-01\", \"gender\": \"UNKNOWN\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}

	private void addVitalSigns(long patientId, Instant recordedAt, String values) throws Exception {
		mvc.perform(post("/api/patients/{id}/vital-signs", patientId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"recordedAt\": \"" + recordedAt + "\", " + values + "}"))
			.andExpect(status().isCreated());
	}

	/** Records a fasting glucose result; returns the lab test's ID. */
	private long addGlucose(long patientId, String value) throws Exception {
		Long labTestId = jdbc.queryForObject("SELECT id FROM lab_tests WHERE name = 'Fasting plasma glucose'", Long.class);
		mvc.perform(post("/api/patients/{id}/lab-results", patientId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"labTestId\": " + labTestId + ", \"value\": " + value + ", \"measuredAt\": \""
						+ Instant.now().minus(1, ChronoUnit.HOURS) + "\"}"))
			.andExpect(status().isCreated());
		return labTestId;
	}

	private int count(String table, long patientId) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE patient_id = ?", Integer.class, patientId);
	}

}
