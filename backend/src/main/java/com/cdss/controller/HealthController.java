package com.cdss.controller;

import java.time.Clock;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cdss.dto.ComponentStatus;
import com.cdss.dto.HealthResponse;
import com.cdss.service.DatabaseHealthChecker;

@RestController
@RequestMapping("/api/health")
public class HealthController {

	private final DatabaseHealthChecker databaseHealthChecker;

	private final Clock clock;

	public HealthController(DatabaseHealthChecker databaseHealthChecker, Clock clock) {
		this.databaseHealthChecker = databaseHealthChecker;
		this.clock = clock;
	}

	@GetMapping
	public ResponseEntity<HealthResponse> health() {
		ComponentStatus database = databaseHealthChecker.check();
		HttpStatus status = database == ComponentStatus.UP ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
		HealthResponse response = HealthResponse.of(database, Instant.now(clock));
		return ResponseEntity.status(status).body(response);
	}

}
