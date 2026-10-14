package com.cdss.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.cdss.dto.FactResponse;
import com.cdss.dto.RuleDryRunRequest;
import com.cdss.dto.RuleDryRunResponse;
import com.cdss.dto.RuleRequest;
import com.cdss.dto.RuleResponse;
import com.cdss.dto.RuleSummaryResponse;
import com.cdss.dto.RuleValidationResponse;
import com.cdss.service.ClinicalRuleService;
import com.cdss.service.DecisionService;

import jakarta.validation.Valid;

/**
 * Clinical rules stored as JSON. Invalid rules are rejected with 400 and a list
 * of problems. Everything here is ADMIN only except {@code GET /summaries}
 * (see SecurityConfig), so clinicians never receive the JSON definitions.
 */
@RestController
@RequestMapping("/api/rules")
public class ClinicalRuleController {

	private final ClinicalRuleService ruleService;

	private final DecisionService decisionService;

	public ClinicalRuleController(ClinicalRuleService ruleService, DecisionService decisionService) {
		this.ruleService = ruleService;
		this.decisionService = decisionService;
	}

	@GetMapping
	public List<RuleResponse> list() {
		return ruleService.findAll();
	}

	/** Rules in plain English without their JSON definitions; the only rule endpoint clinicians may use. */
	@GetMapping("/summaries")
	public List<RuleSummaryResponse> summaries() {
		return ruleService.summaries();
	}

	@GetMapping("/{id}")
	public RuleResponse get(@PathVariable Long id) {
		return ruleService.findById(id);
	}

	/** The facts and operators a rule condition may use. */
	@GetMapping("/facts")
	public List<FactResponse> facts() {
		return ruleService.facts();
	}

	/** Checks a rule without saving it: 200 with {@code valid} true/false and any problems. */
	@PostMapping("/validate")
	public RuleValidationResponse validate(@Valid @RequestBody RuleRequest request) {
		return ruleService.validate(request);
	}

	/** Tries a condition (not saved) against one patient's current data. Nothing is stored. */
	@PostMapping("/dry-run")
	public RuleDryRunResponse dryRun(@Valid @RequestBody RuleDryRunRequest request) {
		return decisionService.dryRun(request);
	}

	/** Switches a rule on after validating it again; 400 with the problems if it is no longer valid. */
	@PostMapping("/{id}/activate")
	public RuleResponse activate(@PathVariable Long id) {
		return ruleService.activate(id);
	}

	/** Switches a rule off (works even if the rule has become invalid). */
	@PostMapping("/{id}/deactivate")
	public RuleResponse deactivate(@PathVariable Long id) {
		return ruleService.deactivate(id);
	}

	@PostMapping
	public ResponseEntity<RuleResponse> create(@Valid @RequestBody RuleRequest request) {
		RuleResponse created = ruleService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);
	}

	@PutMapping("/{id}")
	public RuleResponse update(@PathVariable Long id, @Valid @RequestBody RuleRequest request) {
		return ruleService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		ruleService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
