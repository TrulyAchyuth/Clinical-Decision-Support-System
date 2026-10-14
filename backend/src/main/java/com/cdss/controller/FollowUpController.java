package com.cdss.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.cdss.dto.FollowUpOutcomeRequest;
import com.cdss.dto.FollowUpRequest;
import com.cdss.dto.FollowUpRescheduleRequest;
import com.cdss.dto.FollowUpResponse;
import com.cdss.service.FollowUpService;

import jakarta.validation.Valid;

/** Follow-ups: all patients ({@code /api/follow-ups}) and per patient. No DELETE: they are completed or cancelled. */
@RestController
@RequestMapping("/api")
public class FollowUpController {

	private final FollowUpService followUpService;

	public FollowUpController(FollowUpService followUpService) {
		this.followUpService = followUpService;
	}

	@GetMapping("/follow-ups")
	public List<FollowUpResponse> listAll() {
		return followUpService.listAll();
	}

	@GetMapping("/patients/{patientId}/follow-ups")
	public List<FollowUpResponse> listForPatient(@PathVariable Long patientId) {
		return followUpService.listForPatient(patientId);
	}

	@PostMapping("/patients/{patientId}/follow-ups")
	public ResponseEntity<FollowUpResponse> schedule(@PathVariable Long patientId,
			@Valid @RequestBody FollowUpRequest request, Authentication authentication) {
		FollowUpResponse created = followUpService.schedule(patientId, request, authentication.getName());
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);
	}

	@PostMapping("/patients/{patientId}/follow-ups/{id}/reschedule")
	public FollowUpResponse reschedule(@PathVariable Long patientId, @PathVariable Long id,
			@Valid @RequestBody FollowUpRescheduleRequest request) {
		return followUpService.reschedule(patientId, id, request.scheduledAt());
	}

	@PostMapping("/patients/{patientId}/follow-ups/{id}/complete")
	public FollowUpResponse complete(@PathVariable Long patientId, @PathVariable Long id,
			@Valid @RequestBody FollowUpOutcomeRequest request, Authentication authentication) {
		return followUpService.complete(patientId, id, request, authentication.getName());
	}

	@PostMapping("/patients/{patientId}/follow-ups/{id}/cancel")
	public FollowUpResponse cancel(@PathVariable Long patientId, @PathVariable Long id,
			@Valid @RequestBody FollowUpOutcomeRequest request, Authentication authentication) {
		return followUpService.cancel(patientId, id, request, authentication.getName());
	}

}
