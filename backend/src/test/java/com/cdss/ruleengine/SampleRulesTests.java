package com.cdss.ruleengine;

import static com.cdss.ruleengine.RuleTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Reads the sample rules straight from {@code V6__sample_rules.sql}, so the
 * seeded JSON is checked exactly as it is stored, then checks each rule
 * fires (and does not fire) around its documented thresholds.
 */
class SampleRulesTests {

	private static final Instant TIME = Instant.parse("2026-10-13T08:00:00Z");

	// ('RULE-CODE', ... then a line '{condition}', then a line '["actions"]',
	private static final Pattern RULE = Pattern.compile("\\('([A-Z0-9_-]+)',.*?\\n\\s*'(\\{.*?\\})',\\n\\s*'(\\[.*?\\])',",
			Pattern.DOTALL);

	private static final Map<String, Condition> rules = new LinkedHashMap<>();

	private final RuleEvaluator evaluator = new RuleEvaluator();

	@BeforeAll
	static void loadSampleRules() throws IOException {
		String sql = new ClassPathResource("db/migration/V6__sample_rules.sql").getContentAsString(StandardCharsets.UTF_8);
		RuleParser parser = new RuleParser(RuleTestSupport.catalog());
		Matcher matcher = RULE.matcher(sql);
		while (matcher.find()) {
			rules.put(matcher.group(1), parser.parse(json(matcher.group(2))));
			assertThat((List<?>) json(matcher.group(3))).isNotEmpty();
		}
	}

	@Test
	void allFiveSampleRulesAreValid() {
		assertThat(rules).containsOnlyKeys("SEPSIS-SCREEN", "SPO2-LOW", "HR-HIGH", "BP-HIGH", "GLUCOSE-HIGH");
	}

	@Test
	void highBloodPressure() {
		assertThat(truth("BP-HIGH", vitals("vitals.systolicBp", 139, "vitals.diastolicBp", 89))).isEqualTo(Truth.FALSE);
		assertThat(truth("BP-HIGH", vitals("vitals.systolicBp", 140, "vitals.diastolicBp", 80))).isEqualTo(Truth.TRUE);
		assertThat(truth("BP-HIGH", vitals("vitals.systolicBp", 120, "vitals.diastolicBp", 90))).isEqualTo(Truth.TRUE);
		assertThat(truth("BP-HIGH", new PatientFacts())).isEqualTo(Truth.UNKNOWN);
	}

	@Test
	void elevatedFastingGlucose() {
		assertThat(truth("GLUCOSE-HIGH", lab("6.99"))).isEqualTo(Truth.FALSE);
		assertThat(truth("GLUCOSE-HIGH", lab("7.0"))).isEqualTo(Truth.TRUE);
		assertThat(truth("GLUCOSE-HIGH", new PatientFacts())).isEqualTo(Truth.UNKNOWN);
	}

	@Test
	void lowOxygenSaturation() {
		assertThat(truth("SPO2-LOW", vitals("vitals.oxygenSaturation", 92))).isEqualTo(Truth.FALSE);
		assertThat(truth("SPO2-LOW", vitals("vitals.oxygenSaturation", 91))).isEqualTo(Truth.TRUE);
	}

	@Test
	void tachycardia() {
		assertThat(truth("HR-HIGH", vitals("vitals.heartRate", 100))).isEqualTo(Truth.FALSE);
		assertThat(truth("HR-HIGH", vitals("vitals.heartRate", 101))).isEqualTo(Truth.TRUE);
	}

	@Test
	void possibleSepsisNeedsTwoIndicators() {
		// Only one indicator (fever), others normal.
		assertThat(truth("SEPSIS-SCREEN", vitals("vitals.temperature", 38.5, "vitals.heartRate", 80,
				"vitals.respiratoryRate", 16))).isEqualTo(Truth.FALSE);
		// Fever and fast heart rate.
		assertThat(truth("SEPSIS-SCREEN", vitals("vitals.temperature", 38.5, "vitals.heartRate", 95,
				"vitals.respiratoryRate", 16))).isEqualTo(Truth.TRUE);
		// Low temperature and fast breathing.
		assertThat(truth("SEPSIS-SCREEN", vitals("vitals.temperature", 35.8, "vitals.heartRate", 80,
				"vitals.respiratoryRate", 22))).isEqualTo(Truth.TRUE);
		// Boundaries are exclusive: 38.0, 90 and 20 are not indicators.
		assertThat(truth("SEPSIS-SCREEN", vitals("vitals.temperature", 38.0, "vitals.heartRate", 90,
				"vitals.respiratoryRate", 20))).isEqualTo(Truth.FALSE);
		// Heart and breathing rates high, temperature not recorded: still matches.
		assertThat(truth("SEPSIS-SCREEN", vitals("vitals.heartRate", 95, "vitals.respiratoryRate", 24)))
			.isEqualTo(Truth.TRUE);
		// One indicator and the rest missing: cannot tell, so it is not reported as normal.
		assertThat(truth("SEPSIS-SCREEN", vitals("vitals.heartRate", 95))).isEqualTo(Truth.UNKNOWN);
	}

	@Test
	void sepsisReasonsAreListedOnceEachEvenWhenSeveralBranchesMatch() {
		Evaluation result = evaluator.evaluate(rules.get("SEPSIS-SCREEN"), vitals("vitals.temperature", 38.6,
				"vitals.heartRate", 118, "vitals.respiratoryRate", 24));

		assertThat(result.reasons()).containsExactly(
				"Temperature is 38.6 °C (recorded 2026-10-13 08:00 UTC), which is greater than 38 °C.",
				"Heart rate is 118 beats/min (recorded 2026-10-13 08:00 UTC), which is greater than 90 beats/min.",
				"Respiratory rate is 24 breaths/min (recorded 2026-10-13 08:00 UTC), which is greater than 20 breaths/min.");
	}

	private Truth truth(String ruleCode, PatientFacts facts) {
		return evaluator.evaluate(rules.get(ruleCode), facts).truth();
	}

	private static PatientFacts vitals(Object... factsAndValues) {
		PatientFacts facts = new PatientFacts();
		for (int i = 0; i < factsAndValues.length; i += 2) {
			facts.withNumber((String) factsAndValues[i], new BigDecimal(factsAndValues[i + 1].toString()), TIME);
		}
		return facts;
	}

	private static PatientFacts lab(String glucose) {
		return new PatientFacts().withNumber("lab.Fasting plasma glucose", new BigDecimal(glucose), TIME);
	}

}
