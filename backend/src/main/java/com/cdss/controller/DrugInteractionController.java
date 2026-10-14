package com.cdss.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cdss.dto.DrugInteractionResponse;
import com.cdss.service.MedicationSafetyService;

/** Read-only view of the drug-interaction dataset used by the safety check (illustrative sample data). */
@RestController
@RequestMapping("/api/drug-interactions")
public class DrugInteractionController {

	private final MedicationSafetyService safetyService;

	public DrugInteractionController(MedicationSafetyService safetyService) {
		this.safetyService = safetyService;
	}

	@GetMapping
	public List<DrugInteractionResponse> list() {
		return safetyService.interactions();
	}

}
