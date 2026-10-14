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

import com.cdss.dto.DrugRequest;
import com.cdss.dto.DrugResponse;
import com.cdss.service.DrugService;

import jakarta.validation.Valid;

/** Knowledge base: drugs. */
@RestController
@RequestMapping("/api/drugs")
public class DrugController {

	private final DrugService drugService;

	public DrugController(DrugService drugService) {
		this.drugService = drugService;
	}

	@GetMapping
	public List<DrugResponse> list() {
		return drugService.findAll();
	}

	@GetMapping("/{id}")
	public DrugResponse get(@PathVariable Long id) {
		return drugService.findById(id);
	}

	@PostMapping
	public ResponseEntity<DrugResponse> create(@Valid @RequestBody DrugRequest request) {
		DrugResponse created = drugService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);
	}

	@PutMapping("/{id}")
	public DrugResponse update(@PathVariable Long id, @Valid @RequestBody DrugRequest request) {
		return drugService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		drugService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
