package com.cdss.controller;

import java.net.URI;

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

import com.cdss.dto.AllergyRequest;
import com.cdss.dto.AllergyResponse;
import com.cdss.dto.AllergyUpdateRequest;
import com.cdss.dto.PatientAllergiesResponse;
import com.cdss.dto.ReasonRequest;
import com.cdss.service.AllergyService;

import jakarta.validation.Valid;

/** A patient's allergies and allergy-history status. No DELETE: allergies are resolved so history is kept. */
@RestController
@RequestMapping("/api/patients/{patientId}/allergies")
public class AllergyController {

	private final AllergyService allergyService;

	public AllergyController(AllergyService allergyService) {
		this.allergyService = allergyService;
	}

	@GetMapping
	public PatientAllergiesResponse list(@PathVariable Long patientId) {
		return allergyService.list(patientId);
	}

	@PostMapping
	public ResponseEntity<AllergyResponse> add(@PathVariable Long patientId, @Valid @RequestBody AllergyRequest request,
			Authentication authentication) {
		AllergyResponse created = allergyService.add(patientId, request, authentication.getName());
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);
	}

	@PutMapping("/{id}")
	public AllergyResponse update(@PathVariable Long patientId, @PathVariable Long id,
			@Valid @RequestBody AllergyUpdateRequest request) {
		return allergyService.update(patientId, id, request);
	}

	@PostMapping("/{id}/resolve")
	public AllergyResponse resolve(@PathVariable Long patientId, @PathVariable Long id,
			@Valid @RequestBody ReasonRequest request, Authentication authentication) {
		return allergyService.resolve(patientId, id, request.reason(), authentication.getName());
	}

	/** Records "no known allergies"; 409 while any allergy is active. */
	@PostMapping("/no-known-allergies")
	public PatientAllergiesResponse confirmNoKnownAllergies(@PathVariable Long patientId,
			Authentication authentication) {
		return allergyService.confirmNoKnownAllergies(patientId, authentication.getName());
	}

}
