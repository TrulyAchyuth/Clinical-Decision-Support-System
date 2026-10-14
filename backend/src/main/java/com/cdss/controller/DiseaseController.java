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

import com.cdss.dto.DiseaseRequest;
import com.cdss.dto.DiseaseResponse;
import com.cdss.service.DiseaseService;

import jakarta.validation.Valid;

/** Knowledge base: diseases (ICD-10 coded). */
@RestController
@RequestMapping("/api/diseases")
public class DiseaseController {

	private final DiseaseService diseaseService;

	public DiseaseController(DiseaseService diseaseService) {
		this.diseaseService = diseaseService;
	}

	@GetMapping
	public List<DiseaseResponse> list() {
		return diseaseService.findAll();
	}

	@GetMapping("/{id}")
	public DiseaseResponse get(@PathVariable Long id) {
		return diseaseService.findById(id);
	}

	@PostMapping
	public ResponseEntity<DiseaseResponse> create(@Valid @RequestBody DiseaseRequest request) {
		DiseaseResponse created = diseaseService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);
	}

	@PutMapping("/{id}")
	public DiseaseResponse update(@PathVariable Long id, @Valid @RequestBody DiseaseRequest request) {
		return diseaseService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		diseaseService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
