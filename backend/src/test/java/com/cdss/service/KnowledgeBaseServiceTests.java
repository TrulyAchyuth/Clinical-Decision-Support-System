package com.cdss.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.cdss.dto.DiseaseRequest;
import com.cdss.dto.DrugRequest;
import com.cdss.dto.LabTestRequest;
import com.cdss.entity.Disease;
import com.cdss.entity.Drug;
import com.cdss.entity.LabTest;
import com.cdss.exception.DuplicateResourceException;
import com.cdss.exception.ResourceInUseException;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.repository.DiseaseRepository;
import com.cdss.repository.DrugRepository;
import com.cdss.repository.LabResultRepository;
import com.cdss.repository.LabTestRepository;
import com.cdss.repository.PatientConditionRepository;

class KnowledgeBaseServiceTests {

	private final DrugRepository drugRepository = mock(DrugRepository.class);

	private final DiseaseRepository diseaseRepository = mock(DiseaseRepository.class);

	private final LabTestRepository labTestRepository = mock(LabTestRepository.class);

	private final LabResultRepository labResultRepository = mock(LabResultRepository.class);

	private final PatientConditionRepository conditionRepository = mock(PatientConditionRepository.class);

	private final DrugService drugService = new DrugService(drugRepository);

	private final DiseaseService diseaseService = new DiseaseService(diseaseRepository, conditionRepository);

	private final LabTestService labTestService = new LabTestService(labTestRepository, labResultRepository);

	// ---- drugs ----

	@Test
	void createDrugSavesTrimmedValues() {
		given(drugRepository.save(any(Drug.class))).willAnswer((invocation) -> withId(invocation.getArgument(0), 1L));

		var created = drugService.create(new DrugRequest(" Amoxicillin ", "500 mg ", "Antibiotic", " "));

		assertThat(created.id()).isEqualTo(1L);
		assertThat(created.name()).isEqualTo("Amoxicillin");
		assertThat(created.dosage()).isEqualTo("500 mg");
		assertThat(created.notes()).isNull();
	}

	@Test
	void createDuplicateDrugIsRejected() {
		given(drugRepository.existsByNameIgnoreCaseAndDosageIgnoreCase("Amoxicillin", "500 mg")).willReturn(true);

		assertThatThrownBy(() -> drugService.create(new DrugRequest("Amoxicillin", "500 mg", "Antibiotic", null)))
			.isInstanceOf(DuplicateResourceException.class);
		verify(drugRepository, never()).save(any());
	}

	@Test
	void updateDrugToAnotherDrugsNameAndDosageIsRejected() {
		given(drugRepository.findById(2L)).willReturn(Optional.of(withId(new Drug(), 2L)));
		given(drugRepository.existsByNameIgnoreCaseAndDosageIgnoreCaseAndIdNot("Amoxicillin", "500 mg", 2L))
			.willReturn(true);

		assertThatThrownBy(() -> drugService.update(2L, new DrugRequest("Amoxicillin", "500 mg", "Antibiotic", null)))
			.isInstanceOf(DuplicateResourceException.class);
	}

	@Test
	void deleteMissingDrugThrowsNotFound() {
		given(drugRepository.findById(9L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> drugService.delete(9L)).isInstanceOf(ResourceNotFoundException.class);
	}

	// ---- diseases ----

	@Test
	void createDiseaseStoresIcd10CodeInUpperCase() {
		given(diseaseRepository.save(any(Disease.class))).willAnswer((invocation) -> withId(invocation.getArgument(0), 1L));

		var created = diseaseService.create(new DiseaseRequest("Type 2 diabetes mellitus", " e11.9 "));

		assertThat(created.icd10Code()).isEqualTo("E11.9");
	}

	@Test
	void createDiseaseWithExistingIcd10CodeIsRejected() {
		given(diseaseRepository.existsByIcd10CodeIgnoreCase("I10")).willReturn(true);

		assertThatThrownBy(() -> diseaseService.create(new DiseaseRequest("Hypertension", "i10")))
			.isInstanceOf(DuplicateResourceException.class)
			.hasMessage("A disease with ICD-10 code 'I10' already exists");
	}

	@Test
	void updateDiseaseMayKeepItsOwnCode() {
		Disease existing = withId(new Disease(), 1L);
		given(diseaseRepository.findById(1L)).willReturn(Optional.of(existing));
		given(diseaseRepository.existsByIcd10CodeIgnoreCaseAndIdNot("I10", 1L)).willReturn(false);
		given(diseaseRepository.saveAndFlush(existing)).willReturn(existing);

		var updated = diseaseService.update(1L, new DiseaseRequest("Essential hypertension", "I10"));

		assertThat(updated.name()).isEqualTo("Essential hypertension");
	}

	@Test
	void diseaseUsedInPatientHistoryCannotBeDeleted() {
		Disease disease = withId(new Disease(), 1L);
		disease.setIcd10Code("I10");
		given(diseaseRepository.findById(1L)).willReturn(Optional.of(disease));
		given(conditionRepository.existsByDiseaseId(1L)).willReturn(true);

		assertThatThrownBy(() -> diseaseService.delete(1L)).isInstanceOf(ResourceInUseException.class)
			.hasMessageContaining("I10");
		verify(diseaseRepository, never()).delete(any());
	}

	// ---- lab tests ----

	@Test
	void createLabTestSavesRangeAndUnit() {
		given(labTestRepository.save(any(LabTest.class))).willAnswer((invocation) -> withId(invocation.getArgument(0), 1L));

		var created = labTestService.create(
				new LabTestRequest("Potassium", new BigDecimal("3.5"), new BigDecimal("5.1"), "mmol/L", null));

		assertThat(created.referenceRangeLow()).isEqualByComparingTo("3.5");
		assertThat(created.referenceRangeHigh()).isEqualByComparingTo("5.1");
		assertThat(created.unit()).isEqualTo("mmol/L");
	}

	@Test
	void createDuplicateLabTestNameIsRejected() {
		given(labTestRepository.existsByNameIgnoreCase("Potassium")).willReturn(true);

		assertThatThrownBy(() -> labTestService.create(
				new LabTestRequest("Potassium", BigDecimal.ONE, BigDecimal.TEN, "mmol/L", null)))
			.isInstanceOf(DuplicateResourceException.class);
	}

	@Test
	void findMissingLabTestThrowsNotFound() {
		given(labTestRepository.findById(9L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> labTestService.findById(9L)).isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Lab test with id 9 was not found");
	}

	@Test
	void labTestWithPatientResultsCannotBeDeletedButUnusedOneCan() {
		LabTest used = withId(new LabTest(), 1L);
		used.setName("Potassium");
		LabTest unused = withId(new LabTest(), 2L);
		given(labTestRepository.findById(1L)).willReturn(Optional.of(used));
		given(labTestRepository.findById(2L)).willReturn(Optional.of(unused));
		given(labResultRepository.existsByLabTestId(1L)).willReturn(true);

		assertThatThrownBy(() -> labTestService.delete(1L)).isInstanceOf(ResourceInUseException.class)
			.hasMessage("Lab test 'Potassium' has recorded patient results and cannot be deleted");
		labTestService.delete(2L);
		verify(labTestRepository).delete(unused);
		verify(labTestRepository, never()).delete(used);
	}

	private static <T> T withId(T entity, Long id) {
		ReflectionTestUtils.setField(entity, "id", id);
		return entity;
	}

}
