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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
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
import com.cdss.dto.LabResultRequest;
import com.cdss.dto.LabResultResponse;
import com.cdss.dto.PatientConditionRequest;
import com.cdss.dto.VitalSignsRequest;
import com.cdss.dto.VitalSignsResponse;
import com.cdss.exception.DuplicateResourceException;
import com.cdss.exception.InvalidRequestException;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.service.ClinicalRecordService;

@WebMvcTest(ClinicalRecordController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "CLINICIAN")
class ClinicalRecordControllerTests {

	private static final Instant TIME = Instant.parse("2026-10-13T08:00:00Z");

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ClinicalRecordService recordService;

	@Test
	void addVitalSignsReturns201() throws Exception {
		given(recordService.addVitalSigns(eq(1L), any(VitalSignsRequest.class))).willReturn(new VitalSignsResponse(4L, 1L,
				TIME, 150, 95, new BigDecimal("37.2"), 88, 16, 97, null, TIME));

		mvc.perform(post("/api/patients/1/vital-signs").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
				{"recordedAt": "2026-10-13T08:00:00Z", "systolicBp": 150, "diastolicBp": 95, "temperature": 37.2,
				 "heartRate": 88, "respiratoryRate": 16, "oxygenSaturation": 97}
				"""))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "http://localhost/api/patients/1/vital-signs/4"))
			.andExpect(jsonPath("$.systolicBp").value(150));
	}

	@Test
	void vitalSignsWithoutAnyValueAreRejected() throws Exception {
		mvc.perform(post("/api/patients/1/vital-signs").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"recordedAt\": \"2026-10-13T08:00:00Z\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.anyMeasurementPresent").value("at least one vital sign must be provided"));
		verify(recordService, never()).addVitalSigns(any(), any());
	}

	@Test
	void implausibleVitalSignsAreRejectedNotStored() throws Exception {
		mvc.perform(post("/api/patients/1/vital-signs").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
				{"recordedAt": "2026-10-13T08:00:00Z", "heartRate": 400, "oxygenSaturation": 120,
				 "temperature": 50.0, "respiratoryRate": 0}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.heartRate").exists())
			.andExpect(jsonPath("$.errors.oxygenSaturation").exists())
			.andExpect(jsonPath("$.errors.temperature").exists())
			.andExpect(jsonPath("$.errors.respiratoryRate").exists());
	}

	@Test
	void bloodPressureMustBeCompleteAndConsistent() throws Exception {
		mvc.perform(post("/api/patients/1/vital-signs").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"recordedAt\": \"2026-10-13T08:00:00Z\", \"systolicBp\": 150}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.bloodPressureComplete").exists());
		mvc.perform(post("/api/patients/1/vital-signs").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"recordedAt\": \"2026-10-13T08:00:00Z\", \"systolicBp\": 80, \"diastolicBp\": 90}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.bloodPressureConsistent").exists());
	}

	@Test
	void timestampIsRequiredAndCannotBeInTheFuture() throws Exception {
		mvc.perform(post("/api/patients/1/vital-signs").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"heartRate\": 80}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.recordedAt").exists());
		mvc.perform(post("/api/patients/1/lab-results").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"labTestId\": 1, \"value\": 5, \"measuredAt\": \"2999-01-01T00:00:00Z\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.measuredAt").exists());
	}

	@Test
	void listForUnknownPatientReturns404() throws Exception {
		given(recordService.listVitalSigns(99L)).willThrow(new ResourceNotFoundException("Patient", 99L));

		mvc.perform(get("/api/patients/99/vital-signs")).andExpect(status().isNotFound());
	}

	@Test
	void labResultsAreListedWithFlag() throws Exception {
		given(recordService.listLabResults(1L)).willReturn(List.of(new LabResultResponse(2L, 1L, 3L,
				"Fasting plasma glucose", new BigDecimal("8.20"), "mmol/L", TIME, new BigDecimal("3.90"),
				new BigDecimal("5.50"), "HIGH", null)));

		mvc.perform(get("/api/patients/1/lab-results"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].labTestName").value("Fasting plasma glucose"))
			.andExpect(jsonPath("$[0].flag").value("HIGH"));
	}

	@Test
	void labResultWithWrongUnitReturns400WithMessage() throws Exception {
		given(recordService.addLabResult(eq(1L), any(LabResultRequest.class)))
			.willThrow(new InvalidRequestException("Unit 'mg/dL' does not match the unit for Fasting plasma glucose (mmol/L)"));

		mvc.perform(post("/api/patients/1/lab-results").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
				{"labTestId": 3, "value": 148, "unit": "mg/dL", "measuredAt": "2026-10-13T08:00:00Z"}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Unit 'mg/dL' does not match the unit for Fasting plasma glucose (mmol/L)"));
	}

	@Test
	void labResultNeedsTestValueAndNonNegativeValue() throws Exception {
		mvc.perform(post("/api/patients/1/lab-results").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"value\": -1, \"measuredAt\": \"2026-10-13T08:00:00Z\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.labTestId").exists())
			.andExpect(jsonPath("$.errors.value").exists());
	}

	@Test
	void duplicateConditionReturns409AndDeleteReturns204() throws Exception {
		given(recordService.addCondition(eq(1L), any(PatientConditionRequest.class)))
			.willThrow(new DuplicateResourceException("Patient 1 already has I10 recorded"));

		mvc.perform(post("/api/patients/1/conditions").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"diseaseId\": 2, \"status\": \"ACTIVE\"}"))
			.andExpect(status().isConflict());
		mvc.perform(delete("/api/patients/1/conditions/5").with(csrf())).andExpect(status().isNoContent());
		verify(recordService).deleteCondition(1L, 5L);
	}

	@Test
	void deletingMissingRecordReturns404() throws Exception {
		willThrow(new ResourceNotFoundException("Lab result", 8L)).given(recordService).deleteLabResult(1L, 8L);

		mvc.perform(delete("/api/patients/1/lab-results/8").with(csrf()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Lab result with id 8 was not found"));
	}

}
