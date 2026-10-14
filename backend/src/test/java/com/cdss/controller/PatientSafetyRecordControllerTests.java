package com.cdss.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.cdss.config.SecurityConfig;
import com.cdss.dto.AllergyResponse;
import com.cdss.dto.MedicationResponse;
import com.cdss.dto.PatientAllergiesResponse;
import com.cdss.dto.SafetyCheckResponse;
import com.cdss.dto.SafetyFinding;
import com.cdss.entity.AllergyCategory;
import com.cdss.entity.AllergyRecordStatus;
import com.cdss.entity.AllergySeverity;
import com.cdss.entity.MedicationRoute;
import com.cdss.entity.MedicationStatus;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.exception.SafetyAcknowledgementRequiredException;
import com.cdss.exception.StatusChangeNotAllowedException;
import com.cdss.service.AllergyService;
import com.cdss.service.MedicationSafetyService;
import com.cdss.service.MedicationService;

/** Medications, the safety check and allergies: status codes, validation and the logged-in user. */
@WebMvcTest({ MedicationController.class, AllergyController.class })
@Import(SecurityConfig.class)
@WithMockUser(username = "clinician", roles = "CLINICIAN")
class PatientSafetyRecordControllerTests {

	private static final String VALID_MEDICATION = """
			{"drugId": 5, "dose": 400, "doseUnit": "mg", "route": "ORAL", "frequency": "three times daily",
			 "startDate": "2026-10-14"}
			""";

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private MedicationService medicationService;

	@MockitoBean
	private MedicationSafetyService safetyService;

	@MockitoBean
	private AllergyService allergyService;

	@Test
	void addMedicationReturns201AndRecordsTheLoggedInUser() throws Exception {
		given(medicationService.add(eq(1L), any(), eq("clinician"))).willReturn(medication(40L, MedicationStatus.ACTIVE));

		mvc.perform(post("/api/patients/1/medications").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(VALID_MEDICATION))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "http://localhost/api/patients/1/medications/40"))
			.andExpect(jsonPath("$.drugName").value("Ibuprofen"))
			.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void addMedicationWithMissingOrInvalidFieldsReturns400() throws Exception {
		mvc.perform(post("/api/patients/1/medications").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"dose\": -1, \"route\": \"ORAL\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.drugId").exists())
			.andExpect(jsonPath("$.errors.dose").exists())
			.andExpect(jsonPath("$.errors.doseUnit").exists())
			.andExpect(jsonPath("$.errors.frequency").exists())
			.andExpect(jsonPath("$.errors.startDate").exists());
		mvc.perform(post("/api/patients/1/medications").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(VALID_MEDICATION.replace("\"ORAL\"", "\"BY_MAGIC\"")))
			.andExpect(status().isBadRequest());
		verify(medicationService, never()).add(any(), any(), anyString());
	}

	@Test
	void unacknowledgedSafetyWarningsReturn409WithTheCheck() throws Exception {
		SafetyFinding warning = new SafetyFinding("ALLERGY-21", "ALLERGY", "MATCH_FOUND", "HIGH",
				"Recorded drug allergy matches Ibuprofen 400 mg", "SEVERE", List.of("Ibuprofen 400 mg"),
				List.of("Ibuprofen"), "Recorded allergy.", null, "Confirm the allergy history.",
				"Patient allergy record", "PATIENT_RECORD", null, null, null, true);
		SafetyCheckResponse check = new SafetyCheckResponse(1L, 5L, "Ibuprofen 400 mg", "WARNINGS", true,
				List.of(warning), Instant.parse("2026-10-14T10:00:00Z"), SafetyCheckResponse.DISCLAIMER);
		given(medicationService.add(eq(1L), any(), anyString()))
			.willThrow(new SafetyAcknowledgementRequiredException("Must acknowledge", check, List.of("ALLERGY-21")));

		mvc.perform(post("/api/patients/1/medications").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(VALID_MEDICATION))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SAFETY_ACKNOWLEDGEMENT_REQUIRED"))
			.andExpect(jsonPath("$.unacknowledgedWarnings[0]").value("ALLERGY-21"))
			.andExpect(jsonPath("$.safetyCheck.findings[0].resultStatus").value("MATCH_FOUND"));
	}

	@Test
	void safetyCheckEndpointReturnsTheStructuredResult() throws Exception {
		SafetyFinding none = new SafetyFinding("INTERACTION-NONE-FOUND", "INTERACTION", "NO_MATCH_FOUND", "LOW",
				"No interaction found in the dataset", null, List.of("Metformin 500 mg"), List.of(),
				"No interaction found; not a statement of safety.", null, null, "Drug-interaction dataset",
				"ILLUSTRATIVE_SAMPLE", null, null, null, false);
		given(safetyService.check(1L, 3L)).willReturn(new SafetyCheckResponse(1L, 3L, "Metformin 500 mg",
				"NO_ISSUES_FOUND_IN_AVAILABLE_DATA", false, List.of(none), Instant.parse("2026-10-14T10:00:00Z"),
				SafetyCheckResponse.DISCLAIMER));

		mvc.perform(post("/api/patients/1/medications/safety-check").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"drugId\": 3}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.overallStatus").value("NO_ISSUES_FOUND_IN_AVAILABLE_DATA"))
			.andExpect(jsonPath("$.findings[0].dataClassification").value("ILLUSTRATIVE_SAMPLE"))
			.andExpect(jsonPath("$.disclaimer").value(SafetyCheckResponse.DISCLAIMER));
		mvc.perform(post("/api/patients/1/medications/safety-check").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void unknownPatientAndStatusConflictsUseClearStatusCodes() throws Exception {
		given(medicationService.list(99L)).willThrow(new ResourceNotFoundException("Patient", 99L));
		mvc.perform(get("/api/patients/99/medications")).andExpect(status().isNotFound());

		given(medicationService.discontinue(eq(1L), eq(41L), any(), anyString()))
			.willThrow(new StatusChangeNotAllowedException("Only an active medication can be discontinued"));
		mvc.perform(post("/api/patients/1/medications/41/discontinue").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Side effects\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Only an active medication can be discontinued"));
	}

	@Test
	void updateMedicationValidatesTheBody() throws Exception {
		mvc.perform(put("/api/patients/1/medications/41").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.dose").exists());
	}

	@Test
	void allergyListShowsTheAssessmentAndAddValidatesSeverity() throws Exception {
		given(allergyService.list(1L)).willReturn(new PatientAllergiesResponse(1L, "NOT_ASSESSED",
				"Allergy history not assessed", null, null, List.of()));
		mvc.perform(get("/api/patients/1/allergies"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assessment").value("NOT_ASSESSED"))
			.andExpect(jsonPath("$.allergies").isEmpty());

		mvc.perform(post("/api/patients/1/allergies").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"category\": \"FOOD\", \"allergen\": \"Peanut\", \"severity\": \"DEADLY\", "
						+ "\"recordedDate\": \"2026-10-01\"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/api/patients/1/allergies").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"category\": \"FOOD\", \"allergen\": \"Peanut\", \"recordedDate\": \"2999-01-01\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.severity").exists())
			.andExpect(jsonPath("$.errors.recordedDate").exists());
	}

	@Test
	void addAndResolveAllergy() throws Exception {
		given(allergyService.add(eq(1L), any(), eq("clinician"))).willReturn(allergy(AllergyRecordStatus.ACTIVE));
		mvc.perform(post("/api/patients/1/allergies").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"category\": \"FOOD\", \"allergen\": \"Peanut\", \"severity\": \"SEVERE\", "
						+ "\"recordedDate\": \"2026-10-01\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.allergen").value("Peanut"));

		mvc.perform(post("/api/patients/1/allergies/50/resolve").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \" \"}"))
			.andExpect(status().isBadRequest());
		given(allergyService.resolve(1L, 50L, "Outgrown", "clinician")).willReturn(allergy(AllergyRecordStatus.RESOLVED));
		mvc.perform(post("/api/patients/1/allergies/50/resolve").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"Outgrown\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RESOLVED"));
	}

	@Test
	void noKnownAllergiesWhileAnAllergyIsActiveReturns409() throws Exception {
		given(allergyService.confirmNoKnownAllergies(1L, "clinician"))
			.willThrow(new StatusChangeNotAllowedException("The patient has active allergy records"));

		mvc.perform(post("/api/patients/1/allergies/no-known-allergies").with(csrf()))
			.andExpect(status().isConflict());
	}

	private static MedicationResponse medication(Long id, MedicationStatus status) {
		return new MedicationResponse(id, 1L, 5L, "Ibuprofen", "400 mg", "NSAID", new BigDecimal("400"), "mg",
				MedicationRoute.ORAL, "three times daily", LocalDate.of(2026, 10, 14), null, status, null, null, null,
				null, List.of(), null, "clinician", null, null);
	}

	private static AllergyResponse allergy(AllergyRecordStatus status) {
		return new AllergyResponse(50L, 1L, AllergyCategory.FOOD, null, "Peanut", null, AllergySeverity.SEVERE, status,
				LocalDate.of(2026, 10, 1), null, null, null, null, "clinician", null);
	}

}
