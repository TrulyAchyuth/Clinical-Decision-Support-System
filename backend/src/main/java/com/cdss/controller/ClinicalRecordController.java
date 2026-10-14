package com.cdss.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.cdss.dto.LabResultRequest;
import com.cdss.dto.LabResultResponse;
import com.cdss.dto.PatientConditionRequest;
import com.cdss.dto.PatientConditionResponse;
import com.cdss.dto.VitalSignsRequest;
import com.cdss.dto.VitalSignsResponse;
import com.cdss.service.ClinicalRecordService;

import jakarta.validation.Valid;

/** A patient's clinical records. Lists are newest first (conditions: in the order added). */
@RestController
@RequestMapping("/api/patients/{patientId}")
public class ClinicalRecordController {

	private final ClinicalRecordService recordService;

	public ClinicalRecordController(ClinicalRecordService recordService) {
		this.recordService = recordService;
	}

	@GetMapping("/vital-signs")
	public List<VitalSignsResponse> listVitalSigns(@PathVariable Long patientId) {
		return recordService.listVitalSigns(patientId);
	}

	@PostMapping("/vital-signs")
	public ResponseEntity<VitalSignsResponse> addVitalSigns(@PathVariable Long patientId,
			@Valid @RequestBody VitalSignsRequest request) {
		VitalSignsResponse created = recordService.addVitalSigns(patientId, request);
		return ResponseEntity.created(location(created.id())).body(created);
	}

	@DeleteMapping("/vital-signs/{id}")
	public ResponseEntity<Void> deleteVitalSigns(@PathVariable Long patientId, @PathVariable Long id) {
		recordService.deleteVitalSigns(patientId, id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/lab-results")
	public List<LabResultResponse> listLabResults(@PathVariable Long patientId) {
		return recordService.listLabResults(patientId);
	}

	@PostMapping("/lab-results")
	public ResponseEntity<LabResultResponse> addLabResult(@PathVariable Long patientId,
			@Valid @RequestBody LabResultRequest request) {
		LabResultResponse created = recordService.addLabResult(patientId, request);
		return ResponseEntity.created(location(created.id())).body(created);
	}

	@DeleteMapping("/lab-results/{id}")
	public ResponseEntity<Void> deleteLabResult(@PathVariable Long patientId, @PathVariable Long id) {
		recordService.deleteLabResult(patientId, id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/conditions")
	public List<PatientConditionResponse> listConditions(@PathVariable Long patientId) {
		return recordService.listConditions(patientId);
	}

	@PostMapping("/conditions")
	public ResponseEntity<PatientConditionResponse> addCondition(@PathVariable Long patientId,
			@Valid @RequestBody PatientConditionRequest request) {
		PatientConditionResponse created = recordService.addCondition(patientId, request);
		return ResponseEntity.created(location(created.id())).body(created);
	}

	@DeleteMapping("/conditions/{id}")
	public ResponseEntity<Void> deleteCondition(@PathVariable Long patientId, @PathVariable Long id) {
		recordService.deleteCondition(patientId, id);
		return ResponseEntity.noContent().build();
	}

	private static URI location(Long id) {
		return ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
	}

}
