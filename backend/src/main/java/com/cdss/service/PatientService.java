package com.cdss.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cdss.dto.PatientRequest;
import com.cdss.dto.PatientResponse;
import com.cdss.entity.Patient;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.repository.PatientRepository;

@Service
@Transactional
public class PatientService {

	private final PatientRepository patientRepository;

	public PatientService(PatientRepository patientRepository) {
		this.patientRepository = patientRepository;
	}

	/** All patients sorted by name, optionally filtered by part of the name. */
	@Transactional(readOnly = true)
	public List<PatientResponse> findAll(String search) {
		String text = Text.trimToNull(search);
		List<Patient> patients = (text == null) ? patientRepository.findAllByOrderByFullNameAsc()
				: patientRepository.findByFullNameContainingIgnoreCaseOrderByFullNameAsc(text);
		return patients.stream().map(PatientResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public PatientResponse findById(Long id) {
		return PatientResponse.from(getPatient(id));
	}

	public PatientResponse create(PatientRequest request) {
		Patient patient = new Patient();
		copy(request, patient);
		return PatientResponse.from(patientRepository.save(patient));
	}

	public PatientResponse update(Long id, PatientRequest request) {
		Patient patient = getPatient(id);
		copy(request, patient);
		// Flush now so the returned updatedAt reflects this change.
		return PatientResponse.from(patientRepository.saveAndFlush(patient));
	}

	public void delete(Long id) {
		patientRepository.delete(getPatient(id));
	}

	private Patient getPatient(Long id) {
		return patientRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Patient", id));
	}

	private static void copy(PatientRequest request, Patient patient) {
		patient.setFullName(Text.trimToNull(request.fullName()));
		patient.setDateOfBirth(request.dateOfBirth());
		patient.setGender(request.gender());
		patient.setPhone(Text.trimToNull(request.phone()));
		patient.setEmail(Text.trimToNull(request.email()));
		patient.setAddress(Text.trimToNull(request.address()));
	}

}
