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

import com.cdss.dto.LabTestRequest;
import com.cdss.dto.LabTestResponse;
import com.cdss.service.LabTestService;

import jakarta.validation.Valid;

/** Knowledge base: lab tests and their reference ranges. */
@RestController
@RequestMapping("/api/lab-tests")
public class LabTestController {

	private final LabTestService labTestService;

	public LabTestController(LabTestService labTestService) {
		this.labTestService = labTestService;
	}

	@GetMapping
	public List<LabTestResponse> list() {
		return labTestService.findAll();
	}

	@GetMapping("/{id}")
	public LabTestResponse get(@PathVariable Long id) {
		return labTestService.findById(id);
	}

	@PostMapping
	public ResponseEntity<LabTestResponse> create(@Valid @RequestBody LabTestRequest request) {
		LabTestResponse created = labTestService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);
	}

	@PutMapping("/{id}")
	public LabTestResponse update(@PathVariable Long id, @Valid @RequestBody LabTestRequest request) {
		return labTestService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		labTestService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
