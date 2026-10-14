package com.cdss.ruleengine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Reads the interaction rows in V9__drug_interactions.sql and checks every one
 * is clearly labelled as illustrative, unreviewed sample data, stored once in
 * name order, and names drugs that exist in the sample knowledge base (V3).
 */
class DrugInteractionSampleDataTests {

	private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

	private static final Pattern ROW = Pattern.compile(
			"^\\('([^']+)', '([^']+)', '(MINOR|MODERATE|MAJOR)', '([^']+)', '([^']+)', '([A-Z_]+)', '([A-Z_]+)', UTC_TIMESTAMP\\(6\\)\\)[,;]$");

	@Test
	void everySampleInteractionIsLabelledIllustrativeAndUsesKnownDrugs() throws IOException {
		List<String> lines = Files.readAllLines(MIGRATIONS.resolve("V9__drug_interactions.sql"));
		String drugs = Files.readString(MIGRATIONS.resolve("V3__sample_data.sql"));
		List<Matcher> rows = lines.stream().map(ROW::matcher).filter(Matcher::matches).toList();

		assertThat(rows).isNotEmpty();
		for (Matcher row : rows) {
			assertThat(row.group(1).compareToIgnoreCase(row.group(2))).as("name order").isNegative();
			assertThat(row.group(4)).startsWith("ILLUSTRATIVE SAMPLE:").contains("Not authoritative clinical guidance");
			assertThat(row.group(5)).containsIgnoringCase("not from a licensed or verified");
			assertThat(row.group(6)).isEqualTo("ILLUSTRATIVE_SAMPLE");
			assertThat(row.group(7)).isEqualTo("NOT_CLINICALLY_REVIEWED");
			assertThat(drugs).contains("('" + row.group(1) + "',").contains("('" + row.group(2) + "',");
		}
		assertThat(lines.stream().filter((line) -> line.startsWith("('")).count()).isEqualTo(rows.size());
	}

}
