package com.cdss.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.cdss.dto.PatientRequest;
import com.cdss.dto.PatientResponse;
import com.cdss.entity.Gender;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.service.PatientService;

@WebMvcTest(PatientController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "CLINICIAN")
class PatientControllerTests {

	private static final String VALID_PATIENT = """
			{"fullName": "Daniel Okafor", "dateOfBirth": "1958-04-12", "gender": "MALE",
			 "phone": "+1 555 0101", "email": "daniel@example.com", "address": "14 Harbour Lane"}
			""";

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private PatientService patientService;

	@Test
	void createReturns201WithLocationAndBody() throws Exception {
		given(patientService.create(any(PatientRequest.class))).willReturn(patient(7L, "Daniel Okafor"));

		mvc.perform(post("/api/patients").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_PATIENT))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "http://localhost/api/patients/7"))
			.andExpect(jsonPath("$.id").value(7))
			.andExpect(jsonPath("$.fullName").value("Daniel Okafor"))
			.andExpect(jsonPath("$.dateOfBirth").value("1958-04-12"))
			.andExpect(jsonPath("$.gender").value("MALE"));
	}

	@Test
	void createWithMissingRequiredFieldsReturns400WithFieldErrors() throws Exception {
		mvc.perform(post("/api/patients").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Validation failed"))
			.andExpect(jsonPath("$.errors.fullName").exists())
			.andExpect(jsonPath("$.errors.dateOfBirth").exists())
			.andExpect(jsonPath("$.errors.gender").exists());
		verify(patientService, never()).create(any());
	}

	@Test
	void createWithInvalidValuesReturns400() throws Exception {
		String body = """
				{"fullName": "  ", "dateOfBirth": "2999-01-01", "gender": "MALE",
				 "phone": "call me", "email": "not-an-email"}
				""";
		mvc.perform(post("/api/patients").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.fullName").exists())
			.andExpect(jsonPath("$.errors.dateOfBirth").exists())
			.andExpect(jsonPath("$.errors.phone").exists())
			.andExpect(jsonPath("$.errors.email").exists());
	}

	@Test
	void createWithUnknownGenderOrMalformedJsonReturns400() throws Exception {
		mvc.perform(post("/api/patients").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(VALID_PATIENT.replace("\"MALE\"", "\"ROBOT\"")))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/api/patients").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{not json"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void listReturnsPatientsAndPassesSearch() throws Exception {
		given(patientService.findAll("dan")).willReturn(List.of(patient(1L, "Daniel Okafor")));

		mvc.perform(get("/api/patients").param("search", "dan"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].fullName").value("Daniel Okafor"));
	}

	@Test
	void getReturnsPatient() throws Exception {
		given(patientService.findById(1L)).willReturn(patient(1L, "Daniel Okafor"));

		mvc.perform(get("/api/patients/1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(1))
			.andExpect(jsonPath("$.email").value("daniel@example.com"));
	}

	@Test
	void getMissingPatientReturns404() throws Exception {
		given(patientService.findById(99L)).willThrow(new ResourceNotFoundException("Patient", 99L));

		mvc.perform(get("/api/patients/99"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.detail").value("Patient with id 99 was not found"));
	}

	@Test
	void getWithNonNumericIdReturns400() throws Exception {
		mvc.perform(get("/api/patients/abc")).andExpect(status().isBadRequest());
	}

	@Test
	void updateReturns200() throws Exception {
		given(patientService.update(eq(1L), any(PatientRequest.class))).willReturn(patient(1L, "Daniel Okafor-Updated"));

		mvc.perform(put("/api/patients/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_PATIENT))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fullName").value("Daniel Okafor-Updated"));
	}

	@Test
	void updateMissingPatientReturns404() throws Exception {
		given(patientService.update(eq(99L), any(PatientRequest.class)))
			.willThrow(new ResourceNotFoundException("Patient", 99L));

		mvc.perform(put("/api/patients/99").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_PATIENT))
			.andExpect(status().isNotFound());
	}

	@Test
	void updateWithInvalidBodyReturns400() throws Exception {
		mvc.perform(put("/api/patients/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"fullName\": \"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.fullName").exists());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void deleteReturns204() throws Exception {
		mvc.perform(delete("/api/patients/1").with(csrf())).andExpect(status().isNoContent());
		verify(patientService).delete(1L);
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void deleteMissingPatientReturns404() throws Exception {
		willThrow(new ResourceNotFoundException("Patient", 99L)).given(patientService).delete(99L);

		mvc.perform(delete("/api/patients/99").with(csrf())).andExpect(status().isNotFound());
	}

	@Test
	void unexpectedErrorReturnsGeneric500WithoutDetails() throws Exception {
		given(patientService.findById(1L)).willThrow(new IllegalStateException("secret internal detail"));

		mvc.perform(get("/api/patients/1"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
			.andExpect(jsonPath("$.trace").doesNotExist());
	}

	private static PatientResponse patient(Long id, String name) {
		return new PatientResponse(id, name, LocalDate.of(1958, 4, 12), Gender.MALE, "+1 555 0101",
				"daniel@example.com", "14 Harbour Lane", Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-01-01T00:00:00Z"));
	}

}
