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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.cdss.dto.PatientRequest;
import com.cdss.dto.PatientResponse;
import com.cdss.service.PatientService;

import jakarta.validation.Valid;

/** Patient management. {@code GET /api/patients?search=ann} filters by part of the name. */
@RestController
@RequestMapping("/api/patients")
public class PatientController {

	private final PatientService patientService;

	public PatientController(PatientService patientService) {
		this.patientService = patientService;
	}

	@GetMapping
	public List<PatientResponse> list(@RequestParam(required = false) String search) {
		return patientService.findAll(search);
	}

	@GetMapping("/{id}")
	public PatientResponse get(@PathVariable Long id) {
		return patientService.findById(id);
	}

	@PostMapping
	public ResponseEntity<PatientResponse> create(@Valid @RequestBody PatientRequest request) {
		PatientResponse created = patientService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);
	}

	@PutMapping("/{id}")
	public PatientResponse update(@PathVariable Long id, @Valid @RequestBody PatientRequest request) {
		return patientService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		patientService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
