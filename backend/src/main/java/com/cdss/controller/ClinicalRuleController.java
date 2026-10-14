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
import com.cdss.dto.RuleRequest;
import com.cdss.dto.RuleResponse;
import com.cdss.dto.RuleValidationResponse;
import com.cdss.service.ClinicalRuleService;

import jakarta.validation.Valid;

/** Clinical rules stored as JSON. Invalid rules are rejected with 400 and a list of problems. */
@RestController
@RequestMapping("/api/rules")
public class ClinicalRuleController {

	private final ClinicalRuleService ruleService;

	public ClinicalRuleController(ClinicalRuleService ruleService) {
		this.ruleService = ruleService;
	}

	@GetMapping
	public List<RuleResponse> list() {
		return ruleService.findAll();
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
