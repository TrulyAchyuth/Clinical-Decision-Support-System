package com.cdss.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.cdss.dto.PatientRequest;
import com.cdss.dto.PatientResponse;
import com.cdss.entity.Gender;
import com.cdss.entity.Patient;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.repository.PatientRepository;

class PatientServiceTests {

	private final PatientRepository repository = mock(PatientRepository.class);

	private final PatientService service = new PatientService(repository);

	@Test
	void createTrimsTextAndStoresBlankContactFieldsAsNull() {
		given(repository.save(any(Patient.class))).willAnswer((invocation) -> withId(invocation.getArgument(0), 5L));

		PatientResponse created = service.create(new PatientRequest("  Alex Example ", LocalDate.of(1958, 4, 12),
				Gender.MALE, " ", "alex@example.com", ""));

		assertThat(created.id()).isEqualTo(5L);
		assertThat(created.fullName()).isEqualTo("Alex Example");
		assertThat(created.phone()).isNull();
		assertThat(created.email()).isEqualTo("alex@example.com");
		assertThat(created.address()).isNull();
	}

	@Test
	void findByIdReturnsPatient() {
		given(repository.findById(1L)).willReturn(Optional.of(patient(1L, "Jordan Sample")));

		assertThat(service.findById(1L).fullName()).isEqualTo("Jordan Sample");
	}

	@Test
	void findByIdThrowsNotFoundForMissingPatient() {
		given(repository.findById(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.findById(99L)).isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Patient with id 99 was not found");
	}

	@Test
	void findAllUsesSearchOnlyWhenGiven() {
		given(repository.findAllByOrderByFullNameAsc()).willReturn(List.of(patient(1L, "A"), patient(2L, "B")));
		given(repository.findByFullNameContainingIgnoreCaseOrderByFullNameAsc("ale"))
			.willReturn(List.of(patient(1L, "Alex Example")));

		assertThat(service.findAll(null)).hasSize(2);
		assertThat(service.findAll("  ")).hasSize(2);
		assertThat(service.findAll(" ale ")).extracting(PatientResponse::fullName).containsExactly("Alex Example");
	}

	@Test
	void updateChangesExistingPatient() {
		Patient existing = patient(1L, "Old Name");
		given(repository.findById(1L)).willReturn(Optional.of(existing));
		given(repository.saveAndFlush(existing)).willReturn(existing);

		PatientResponse updated = service.update(1L, new PatientRequest("New Name", LocalDate.of(1990, 1, 5),
				Gender.FEMALE, "+1 555 0199", null, "2 Demo Road"));

		assertThat(updated.fullName()).isEqualTo("New Name");
		assertThat(updated.gender()).isEqualTo(Gender.FEMALE);
		assertThat(existing.getAddress()).isEqualTo("2 Demo Road");
	}

	@Test
	void updateMissingPatientThrowsNotFound() {
		given(repository.findById(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(99L,
				new PatientRequest("X", LocalDate.of(1990, 1, 1), Gender.OTHER, null, null, null)))
			.isInstanceOf(ResourceNotFoundException.class);
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void deleteRemovesExistingPatient() {
		Patient existing = patient(1L, "Alex Example");
		given(repository.findById(1L)).willReturn(Optional.of(existing));

		service.delete(1L);

		verify(repository).delete(existing);
	}

	@Test
	void deleteMissingPatientThrowsNotFound() {
		given(repository.findById(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(ResourceNotFoundException.class);
		verify(repository, never()).delete(any());
	}

	private static Patient patient(Long id, String name) {
		Patient patient = new Patient();
		patient.setFullName(name);
		patient.setDateOfBirth(LocalDate.of(1958, 4, 12));
		patient.setGender(Gender.MALE);
		return withId(patient, id);
	}

	private static Patient withId(Patient patient, Long id) {
		ReflectionTestUtils.setField(patient, "id", id);
		return patient;
	}

}
