package com.cdss.service;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.cdss.dto.ComponentStatus;

/**
 * Checks that a database connection can be obtained and validated.
 * Failure details are logged server-side only, never returned to clients.
 */
@Service
public class DatabaseHealthChecker {

	static final int VALIDATION_TIMEOUT_SECONDS = 2;

	private static final Logger log = LoggerFactory.getLogger(DatabaseHealthChecker.class);

	private final DataSource dataSource;

	public DatabaseHealthChecker(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public ComponentStatus check() {
		try (Connection connection = dataSource.getConnection()) {
			if (connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
				return ComponentStatus.UP;
			}
			log.warn("Database health check failed: connection is not valid");
		}
		catch (SQLException ex) {
			log.warn("Database health check failed: {}", ex.getClass().getSimpleName());
		}
		return ComponentStatus.DOWN;
	}

}
