package com.cdss.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.cdss.dto.MedicationRequest;
import com.cdss.dto.MedicationResponse;
import com.cdss.dto.MedicationStatusChangeRequest;
import com.cdss.dto.MedicationUpdateRequest;
import com.cdss.dto.SafetyCheckRequest;
import com.cdss.dto.SafetyCheckResponse;
import com.cdss.service.MedicationSafetyService;
import com.cdss.service.MedicationService;

import jakarta.validation.Valid;

/**
 * A patient's medications and the safety check run before adding one. There
 * is no DELETE: medications are discontinued or completed so history is kept.
 */
@RestController
@RequestMapping("/api/patients/{patientId}/medications")
public class MedicationController {

	private final MedicationService medicationService;

	private final MedicationSafetyService safetyService;

	public MedicationController(MedicationService medicationService, MedicationSafetyService safetyService) {
		this.medicationService = medicationService;
		this.safetyService = safetyService;
	}

	@GetMapping
	public List<MedicationResponse> list(@PathVariable Long patientId) {
		return medicationService.list(patientId);
	}

	@GetMapping("/{id}")
	public MedicationResponse get(@PathVariable Long patientId, @PathVariable Long id) {
		return medicationService.get(patientId, id);
	}

	/** Checks a drug against the patient's allergies and active medications without saving anything. */
	@PostMapping("/safety-check")
	public SafetyCheckResponse safetyCheck(@PathVariable Long patientId, @Valid @RequestBody SafetyCheckRequest request) {
		return safetyService.check(patientId, request.drugId());
	}

	/** 201; 409 with the safety check if warnings are not acknowledged; 409 if already active. */
	@PostMapping
	public ResponseEntity<MedicationResponse> add(@PathVariable Long patientId,
			@Valid @RequestBody MedicationRequest request, Authentication authentication) {
		MedicationResponse created = medicationService.add(patientId, request, authentication.getName());
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);
	}

	@PutMapping("/{id}")
	public MedicationResponse update(@PathVariable Long patientId, @PathVariable Long id,
			@Valid @RequestBody MedicationUpdateRequest request) {
		return medicationService.update(patientId, id, request);
	}

	@PostMapping("/{id}/discontinue")
	public MedicationResponse discontinue(@PathVariable Long patientId, @PathVariable Long id,
			@Valid @RequestBody MedicationStatusChangeRequest request, Authentication authentication) {
		return medicationService.discontinue(patientId, id, request, authentication.getName());
	}

	@PostMapping("/{id}/complete")
	public MedicationResponse complete(@PathVariable Long patientId, @PathVariable Long id,
			@Valid @RequestBody MedicationStatusChangeRequest request, Authentication authentication) {
		return medicationService.complete(patientId, id, request, authentication.getName());
	}

}
