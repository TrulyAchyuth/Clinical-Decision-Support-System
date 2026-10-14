package com.cdss.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.cdss.dto.DecisionResponse;
import com.cdss.service.DecisionService;

/** Requests a decision for a patient (evaluates all enabled rules) and shows earlier decisions. */
@RestController
@RequestMapping("/api/patients/{patientId}/decisions")
public class DecisionController {

	private final DecisionService decisionService;

	public DecisionController(DecisionService decisionService) {
		this.decisionService = decisionService;
	}

	/** Evaluates the patient's recorded data now. The decision is stored (201). */
	@PostMapping
	public ResponseEntity<DecisionResponse> requestDecision(@PathVariable Long patientId) {
		DecisionResponse decision = decisionService.requestDecision(patientId);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(decision.id()).toUri();
		return ResponseEntity.created(location).body(decision);
	}

	/** Earlier decisions, newest first. */
	@GetMapping
	public List<DecisionResponse> history(@PathVariable Long patientId) {
		return decisionService.findDecisions(patientId);
	}

	@GetMapping("/{decisionId}")
	public DecisionResponse get(@PathVariable Long patientId, @PathVariable Long decisionId) {
		return decisionService.findDecision(patientId, decisionId);
	}

}
