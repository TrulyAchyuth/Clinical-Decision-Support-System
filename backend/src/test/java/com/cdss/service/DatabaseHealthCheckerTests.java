package com.cdss.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import com.cdss.dto.ComponentStatus;

class DatabaseHealthCheckerTests {

	private final DataSource dataSource = mock(DataSource.class);

	private final Connection connection = mock(Connection.class);

	private final DatabaseHealthChecker checker = new DatabaseHealthChecker(dataSource);

	@Test
	void upWhenConnectionIsValid() throws SQLException {
		given(dataSource.getConnection()).willReturn(connection);
		given(connection.isValid(DatabaseHealthChecker.VALIDATION_TIMEOUT_SECONDS)).willReturn(true);

		assertThat(checker.check()).isEqualTo(ComponentStatus.UP);
		verify(connection).close();
	}

	@Test
	void downWhenConnectionIsNotValid() throws SQLException {
		given(dataSource.getConnection()).willReturn(connection);
		given(connection.isValid(DatabaseHealthChecker.VALIDATION_TIMEOUT_SECONDS)).willReturn(false);

		assertThat(checker.check()).isEqualTo(ComponentStatus.DOWN);
		verify(connection).close();
	}

	@Test
	void downWhenConnectionCannotBeObtained() throws SQLException {
		given(dataSource.getConnection()).willThrow(new SQLException("Connection refused"));

		assertThat(checker.check()).isEqualTo(ComponentStatus.DOWN);
	}

}
