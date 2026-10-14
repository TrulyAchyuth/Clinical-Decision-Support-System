package com.cdss.dto;

import java.time.Instant;

/**
 * Overall status is {@code UP} only when every component is up; otherwise
 * {@code DEGRADED}, since the API process itself is still answering.
 */
public record HealthResponse(String status, Components components, Instant timestamp) {

	public record Components(ComponentStatus application, ComponentStatus database) {
	}

	public static HealthResponse of(ComponentStatus database, Instant timestamp) {
		String overall = database == ComponentStatus.UP ? "UP" : "DEGRADED";
		return new HealthResponse(overall, new Components(ComponentStatus.UP, database), timestamp);
	}

}
