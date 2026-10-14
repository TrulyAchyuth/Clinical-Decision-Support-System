package com.cdss;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Checks V11__rename_seed_patients.sql against the V3 seed rows: every rename
 * is guarded by all of a seed row's original values (so edited records are
 * never touched), the new names contain no placeholder words, and the
 * migration only updates rows (no deletes, inserts or schema changes).
 */
class SeedPatientRenameMigrationTests {

	private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

	private static final Pattern SEED_ROW = Pattern.compile(
			"\\('([^']+)',\\s*'(\\d{4}-\\d{2}-\\d{2})',\\s*'([A-Z]+)',\\s*(NULL|'[^']*'),\\s*'([^']+)',\\s*(NULL|'[^']*'),");

	@Test
	void everySeedPatientIsRenamedOnlyIfAllItsSeedValuesStillMatch() throws IOException {
		String v3 = Files.readString(MIGRATIONS.resolve("V3__sample_data.sql"));
		String v11 = Files.readString(MIGRATIONS.resolve("V11__rename_seed_patients.sql"));
		Matcher seed = SEED_ROW.matcher(v3);
		int seeds = 0;
		while (seed.find()) {
			seeds++;
			assertThat(v11).contains("WHERE full_name = '" + seed.group(1) + "' AND date_of_birth = '" + seed.group(2)
					+ "' AND gender = '" + seed.group(3) + "'");
			assertThat(v11).contains(condition("phone", seed.group(4)));
			assertThat(v11).contains("email = '" + seed.group(5) + "'");
			assertThat(v11).contains(condition("address", seed.group(6)));
		}
		assertThat(seeds).isEqualTo(4);
	}

	@Test
	void newNamesHaveNoPlaceholderWordsAndTheMigrationOnlyUpdates() throws IOException {
		List<String> lines = Files.readAllLines(MIGRATIONS.resolve("V11__rename_seed_patients.sql"));
		List<String> newNames = lines.stream()
			.map(Pattern.compile("SET full_name = '([^']+)'")::matcher)
			.filter(Matcher::find)
			.map((m) -> m.group(1))
			.toList();

		assertThat(newNames).hasSize(4).doesNotHaveDuplicates()
			.noneMatch((name) -> name.matches("(?i).*(example|sample|demo|fictional|test|placeholder).*"));
		assertThat(lines.stream().filter((line) -> !line.startsWith("--")))
			.noneMatch((line) -> line.matches("(?i)\\s*(DELETE|INSERT|DROP|ALTER|TRUNCATE|CREATE)\\b.*"));
	}

	private static String condition(String column, String seedValue) {
		return seedValue.equals("NULL") ? column + " IS NULL" : column + " = " + seedValue;
	}

}
