package com.cdss.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cdss.dto.LabResultRequest;
import com.cdss.dto.LabResultResponse;
import com.cdss.dto.PatientConditionRequest;
import com.cdss.dto.PatientConditionResponse;
import com.cdss.dto.VitalSignsRequest;
import com.cdss.dto.VitalSignsResponse;
import com.cdss.entity.ConditionStatus;
import com.cdss.entity.Disease;
import com.cdss.entity.LabResult;
import com.cdss.entity.LabTest;
import com.cdss.entity.Patient;
import com.cdss.entity.PatientCondition;
import com.cdss.entity.VitalSigns;
import com.cdss.exception.DuplicateResourceException;
import com.cdss.exception.InvalidRequestException;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.repository.DiseaseRepository;
import com.cdss.repository.LabResultRepository;
import com.cdss.repository.LabTestRepository;
import com.cdss.repository.PatientConditionRepository;
import com.cdss.repository.PatientRepository;
import com.cdss.repository.VitalSignsRepository;

/** A patient's clinical records: vital signs, lab results and conditions (history). */
@Service
@Transactional
public class ClinicalRecordService {

	private final PatientRepository patientRepository;

	private final VitalSignsRepository vitalSignsRepository;

	private final LabResultRepository labResultRepository;

	private final LabTestRepository labTestRepository;

	private final PatientConditionRepository conditionRepository;

	private final DiseaseRepository diseaseRepository;

	public ClinicalRecordService(PatientRepository patientRepository, VitalSignsRepository vitalSignsRepository,
			LabResultRepository labResultRepository, LabTestRepository labTestRepository,
			PatientConditionRepository conditionRepository, DiseaseRepository diseaseRepository) {
		this.patientRepository = patientRepository;
		this.vitalSignsRepository = vitalSignsRepository;
		this.labResultRepository = labResultRepository;
		this.labTestRepository = labTestRepository;
		this.conditionRepository = conditionRepository;
		this.diseaseRepository = diseaseRepository;
	}

	// ---- vital signs ----

	@Transactional(readOnly = true)
	public List<VitalSignsResponse> listVitalSigns(Long patientId) {
		getPatient(patientId);
		return vitalSignsRepository.findByPatientIdOrderByRecordedAtDescIdDesc(patientId)
			.stream()
			.map(VitalSignsResponse::from)
			.toList();
	}

	public VitalSignsResponse addVitalSigns(Long patientId, VitalSignsRequest request) {
		VitalSigns vitals = new VitalSigns();
		vitals.setPatient(getPatient(patientId));
		vitals.setRecordedAt(request.recordedAt());
		vitals.setSystolicBp(request.systolicBp());
		vitals.setDiastolicBp(request.diastolicBp());
		vitals.setTemperature(request.temperature());
		vitals.setHeartRate(request.heartRate());
		vitals.setRespiratoryRate(request.respiratoryRate());
		vitals.setOxygenSaturation(request.oxygenSaturation());
		vitals.setNotes(Text.trimToNull(request.notes()));
		return VitalSignsResponse.from(vitalSignsRepository.save(vitals));
	}

	public void deleteVitalSigns(Long patientId, Long vitalSignsId) {
		getPatient(patientId);
		vitalSignsRepository.delete(vitalSignsRepository.findByIdAndPatientId(vitalSignsId, patientId)
			.orElseThrow(() -> new ResourceNotFoundException("Vital signs", vitalSignsId)));
	}

	// ---- lab results ----

	@Transactional(readOnly = true)
	public List<LabResultResponse> listLabResults(Long patientId) {
		getPatient(patientId);
		return labResultRepository.findByPatientIdOrderByMeasuredAtDescIdDesc(patientId)
			.stream()
			.map(LabResultResponse::from)
			.toList();
	}

	public LabResultResponse addLabResult(Long patientId, LabResultRequest request) {
		Patient patient = getPatient(patientId);
		LabTest labTest = labTestRepository.findById(request.labTestId())
			.orElseThrow(() -> new InvalidRequestException("Lab test with id " + request.labTestId() + " does not exist"));
		String unit = Text.trimToNull(request.unit());
		if (unit != null && !unit.toLowerCase(Locale.ROOT).equals(labTest.getUnit().toLowerCase(Locale.ROOT))) {
			throw new InvalidRequestException("Unit '" + unit + "' does not match the unit for " + labTest.getName()
					+ " (" + labTest.getUnit() + "); convert the value before recording it");
		}
		LabResult result = new LabResult();
		result.setPatient(patient);
		result.setLabTest(labTest);
		result.setValue(request.value());
		result.setUnit(labTest.getUnit());
		result.setMeasuredAt(request.measuredAt());
		result.setNotes(Text.trimToNull(request.notes()));
		return LabResultResponse.from(labResultRepository.save(result));
	}

	public void deleteLabResult(Long patientId, Long labResultId) {
		getPatient(patientId);
		labResultRepository.delete(labResultRepository.findByIdAndPatientId(labResultId, patientId)
			.orElseThrow(() -> new ResourceNotFoundException("Lab result", labResultId)));
	}

	// ---- conditions (patient history) ----

	@Transactional(readOnly = true)
	public List<PatientConditionResponse> listConditions(Long patientId) {
		getPatient(patientId);
		return conditionRepository.findByPatientIdOrderByIdAsc(patientId)
			.stream()
			.map(PatientConditionResponse::from)
			.toList();
	}

	public PatientConditionResponse addCondition(Long patientId, PatientConditionRequest request) {
		Patient patient = getPatient(patientId);
		Disease disease = diseaseRepository.findById(request.diseaseId())
			.orElseThrow(() -> new InvalidRequestException("Disease with id " + request.diseaseId() + " does not exist"));
		if (conditionRepository.existsByPatientIdAndDiseaseId(patientId, disease.getId())) {
			throw new DuplicateResourceException(
					"Patient " + patientId + " already has " + disease.getIcd10Code() + " recorded");
		}
		PatientCondition condition = new PatientCondition();
		condition.setPatient(patient);
		condition.setDisease(disease);
		condition.setStatus(request.status() == null ? ConditionStatus.ACTIVE : request.status());
		condition.setOnsetDate(request.onsetDate());
		condition.setNotes(Text.trimToNull(request.notes()));
		return PatientConditionResponse.from(conditionRepository.save(condition));
	}

	public void deleteCondition(Long patientId, Long conditionId) {
		getPatient(patientId);
		conditionRepository.delete(conditionRepository.findByIdAndPatientId(conditionId, patientId)
			.orElseThrow(() -> new ResourceNotFoundException("Condition", conditionId)));
	}

	private Patient getPatient(Long patientId) {
		return patientRepository.findById(patientId)
			.orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
	}

}
