package com.cdss.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.cdss.dto.DiseaseRequest;
import com.cdss.dto.DiseaseResponse;
import com.cdss.dto.DrugRequest;
import com.cdss.dto.DrugResponse;
import com.cdss.dto.LabTestRequest;
import com.cdss.dto.LabTestResponse;
import com.cdss.exception.DuplicateResourceException;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.service.DiseaseService;
import com.cdss.service.DrugService;
import com.cdss.service.LabTestService;

@WebMvcTest({ DrugController.class, DiseaseController.class, LabTestController.class })
@Import(SecurityConfig.class)
@WithMockUser
class KnowledgeBaseControllerTests {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private DrugService drugService;

	@MockitoBean
	private DiseaseService diseaseService;

	@MockitoBean
	private LabTestService labTestService;

	// ---- drugs ----

	@Test
	void createDrugReturns201() throws Exception {
		given(drugService.create(any(DrugRequest.class)))
			.willReturn(new DrugResponse(3L, "Amoxicillin", "500 mg", "Antibiotic", null));

		mvc.perform(post("/api/drugs").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Amoxicillin\", \"dosage\": \"500 mg\", \"category\": \"Antibiotic\"}"))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "http://localhost/api/drugs/3"))
			.andExpect(jsonPath("$.dosage").value("500 mg"));
	}

	@Test
	void createDuplicateDrugReturns409() throws Exception {
		given(drugService.create(any(DrugRequest.class)))
			.willThrow(new DuplicateResourceException("A drug named 'Amoxicillin' with dosage '500 mg' already exists"));

		mvc.perform(post("/api/drugs").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Amoxicillin\", \"dosage\": \"500 mg\", \"category\": \"Antibiotic\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("A drug named 'Amoxicillin' with dosage '500 mg' already exists"));
	}

	@Test
	void createDrugWithMissingFieldsReturns400() throws Exception {
		mvc.perform(post("/api/drugs").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"X\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.dosage").exists())
			.andExpect(jsonPath("$.errors.category").exists());
	}

	@Test
	void listGetUpdateAndDeleteDrugs() throws Exception {
		DrugResponse drug = new DrugResponse(1L, "Lisinopril", "10 mg", "ACE inhibitor", null);
		given(drugService.findAll()).willReturn(List.of(drug));
		given(drugService.findById(1L)).willReturn(drug);
		given(drugService.update(eq(1L), any(DrugRequest.class))).willReturn(drug);

		mvc.perform(get("/api/drugs")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Lisinopril"));
		mvc.perform(get("/api/drugs/1")).andExpect(status().isOk()).andExpect(jsonPath("$.category").value("ACE inhibitor"));
		mvc.perform(put("/api/drugs/1").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Lisinopril\", \"dosage\": \"10 mg\", \"category\": \"ACE inhibitor\"}"))
			.andExpect(status().isOk());
		mvc.perform(delete("/api/drugs/1")).andExpect(status().isNoContent());
		verify(drugService).delete(1L);
	}

	@Test
	void missingDrugReturns404() throws Exception {
		given(drugService.findById(42L)).willThrow(new ResourceNotFoundException("Drug", 42L));
		willThrow(new ResourceNotFoundException("Drug", 42L)).given(drugService).delete(42L);

		mvc.perform(get("/api/drugs/42")).andExpect(status().isNotFound());
		mvc.perform(delete("/api/drugs/42")).andExpect(status().isNotFound());
	}

	// ---- diseases ----

	@Test
	void createDiseaseReturns201() throws Exception {
		given(diseaseService.create(any(DiseaseRequest.class)))
			.willReturn(new DiseaseResponse(5L, "Essential (primary) hypertension", "I10"));

		mvc.perform(post("/api/diseases").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Essential (primary) hypertension\", \"icd10Code\": \"i10\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.icd10Code").value("I10"));
	}

	@Test
	void createDiseaseWithInvalidIcd10CodeReturns400() throws Exception {
		mvc.perform(post("/api/diseases").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Something\", \"icd10Code\": \"12345\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.icd10Code").value("must be an ICD-10 code such as I10 or E11.9"));
	}

	@Test
	void duplicateIcd10CodeReturns409() throws Exception {
		given(diseaseService.update(eq(1L), any(DiseaseRequest.class)))
			.willThrow(new DuplicateResourceException("A disease with ICD-10 code 'I10' already exists"));

		mvc.perform(put("/api/diseases/1").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Hypertension\", \"icd10Code\": \"I10\"}"))
			.andExpect(status().isConflict());
	}

	@Test
	void listGetAndMissingDiseases() throws Exception {
		given(diseaseService.findAll()).willReturn(List.of(new DiseaseResponse(1L, "Asthma, unspecified", "J45.9")));
		given(diseaseService.findById(9L)).willThrow(new ResourceNotFoundException("Disease", 9L));

		mvc.perform(get("/api/diseases")).andExpect(status().isOk()).andExpect(jsonPath("$[0].icd10Code").value("J45.9"));
		mvc.perform(get("/api/diseases/9")).andExpect(status().isNotFound());
	}

	// ---- lab tests ----

	@Test
	void createLabTestReturns201() throws Exception {
		given(labTestService.create(any(LabTestRequest.class))).willReturn(
				new LabTestResponse(2L, "Potassium", new BigDecimal("3.50"), new BigDecimal("5.10"), "mmol/L", null));

		mvc.perform(post("/api/lab-tests").contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "Potassium", "referenceRangeLow": 3.5, "referenceRangeHigh": 5.1, "unit": "mmol/L"}
				"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.referenceRangeLow").value(3.5))
			.andExpect(jsonPath("$.unit").value("mmol/L"));
	}

	@Test
	void labTestWithLowAboveHighReturns400() throws Exception {
		mvc.perform(post("/api/lab-tests").contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "Potassium", "referenceRangeLow": 6, "referenceRangeHigh": 3, "unit": "mmol/L"}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.referenceRangeValid")
				.value("referenceRangeLow must not be greater than referenceRangeHigh"));
	}

	@Test
	void labTestWithMissingFieldsReturns400() throws Exception {
		mvc.perform(post("/api/lab-tests").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Potassium\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.referenceRangeLow").exists())
			.andExpect(jsonPath("$.errors.referenceRangeHigh").exists())
			.andExpect(jsonPath("$.errors.unit").exists());
	}

	@Test
	void listUpdateDeleteAndMissingLabTests() throws Exception {
		LabTestResponse labTest = new LabTestResponse(1L, "HbA1c", new BigDecimal("4.00"), new BigDecimal("5.60"), "%", null);
		given(labTestService.findAll()).willReturn(List.of(labTest));
		given(labTestService.update(eq(1L), any(LabTestRequest.class))).willReturn(labTest);
		given(labTestService.findById(9L)).willThrow(new ResourceNotFoundException("Lab test", 9L));

		mvc.perform(get("/api/lab-tests")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("HbA1c"));
		mvc.perform(put("/api/lab-tests/1").contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "HbA1c", "referenceRangeLow": 4.0, "referenceRangeHigh": 5.6, "unit": "%"}
				"""))
			.andExpect(status().isOk());
		mvc.perform(get("/api/lab-tests/9")).andExpect(status().isNotFound());
		mvc.perform(delete("/api/lab-tests/1")).andExpect(status().isNoContent());
	}

}
