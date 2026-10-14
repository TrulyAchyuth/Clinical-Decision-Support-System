package com.cdss;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Guards configuration decisions recorded in CLAUDE.md: Flyway owns the
 * schema, Hibernate only validates it, and credentials come from the
 * environment.
 */
class ApplicationConfigurationTests {

	private final PropertySource<?> config = load("application.yml");

	@Test
	void hibernateValidatesSchemaInsteadOfGeneratingIt() {
		assertThat(config.getProperty("spring.jpa.hibernate.ddl-auto")).hasToString("validate");
		assertThat(config.getProperty("spring.sql.init.mode")).hasToString("never");
		assertThat(config.getProperty("spring.flyway.enabled")).isEqualTo(true);
	}

	@Test
	void openInViewIsDisabled() {
		assertThat(config.getProperty("spring.jpa.open-in-view")).isEqualTo(false);
	}

	@Test
	void databaseCredentialsComeOnlyFromEnvironment() {
		assertThat(config.getProperty("spring.datasource.username")).hasToString("${CDSS_DB_USERNAME}");
		assertThat(config.getProperty("spring.datasource.password")).hasToString("${CDSS_DB_PASSWORD}");
	}

	@Test
	void errorResponsesDoNotExposeInternals() {
		assertThat(config.getProperty("server.error.include-stacktrace")).hasToString("never");
		assertThat(config.getProperty("server.error.include-message")).hasToString("never");
	}

	@Test
	void decisionsIgnoreObservationsOlderThanConfiguredLimits() {
		assertThat(config.getProperty("cdss.decision.vital-signs-max-age")).hasToString("24h");
		assertThat(config.getProperty("cdss.decision.lab-results-max-age")).hasToString("72h");
	}

	@Test
	void unauthenticatedApiAccessIsOnlyEnabledInDevProfile() {
		assertThat(config.getProperty("cdss.security.open-api")).isNull();
		assertThat(load("application-dev.yml").getProperty("cdss.security.open-api")).isEqualTo(true);
	}

	private static PropertySource<?> load(String name) {
		try {
			List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(name, new ClassPathResource(name));
			assertThat(sources).hasSize(1);
			return sources.get(0);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not load " + name, ex);
		}
	}

}
