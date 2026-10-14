package com.cdss.ruleengine;

import static com.cdss.ruleengine.RuleTestSupport.parse;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RuleEvaluatorTests {

	private static final Instant TIME = Instant.parse("2026-10-13T08:00:00Z");

	private final RuleEvaluator evaluator = new RuleEvaluator();

	@ParameterizedTest(name = "{0} {1} {2} -> {3}")
	@CsvSource({
			"100, gt,  100, FALSE", "101, gt,  100, TRUE",
			"100, gte, 100, TRUE", "99,  gte, 100, FALSE",
			"92,  lt,  92,  FALSE", "91,  lt,  92,  TRUE",
			"92,  lte, 92,  TRUE", "93,  lte, 92,  FALSE",
			"90,  eq,  90,  TRUE", "91,  eq,  90,  FALSE",
			"90,  neq, 90,  FALSE", "91,  neq, 90,  TRUE" })
	void numberOperatorsAtAndAroundTheBoundary(int heartRate, String operator, int threshold, Truth expected) {
		Condition condition = parse("{\"fact\": \"vitals.heartRate\", \"operator\": \"" + operator + "\", \"value\": "
				+ threshold + "}");

		assertThat(evaluator.evaluate(condition, heartRate(heartRate)).truth()).isEqualTo(expected);
	}

	@Test
	void decimalBoundaryIsExact() {
		Condition condition = parse("{\"fact\": \"vitals.temperature\", \"operator\": \"gt\", \"value\": 38.0}");

		assertThat(evaluate(condition, temperature("38.0")).truth()).isEqualTo(Truth.FALSE);
		assertThat(evaluate(condition, temperature("38.1")).truth()).isEqualTo(Truth.TRUE);
		assertThat(evaluate(condition, temperature("38.00")).truth()).isEqualTo(Truth.FALSE);
	}

	@Test
	void matchExplainsTheValueTimeAndThreshold() {
		Evaluation result = evaluate(parse("{\"fact\": \"vitals.heartRate\", \"operator\": \"gt\", \"value\": 100}"),
				heartRate(118));

		assertThat(result.matched()).isTrue();
		assertThat(result.reasons()).containsExactly(
				"Heart rate is 118 beats/min (recorded 2026-10-13 08:00 UTC), which is greater than 100 beats/min.");
	}

	@Test
	void missingFactIsUnknownNotNormal() {
		Evaluation result = evaluate(parse("{\"fact\": \"vitals.heartRate\", \"operator\": \"lt\", \"value\": 100}"),
				new PatientFacts());

		assertThat(result.truth()).isEqualTo(Truth.UNKNOWN);
		assertThat(result.missingFacts()).containsExactly("vitals.heartRate");
	}

	@Test
	void allNeedsEveryChild() {
		Condition condition = parse("""
				{"all": [{"fact": "vitals.heartRate", "operator": "gt", "value": 90},
				         {"fact": "vitals.respiratoryRate", "operator": "gt", "value": 20}]}
				""");

		assertThat(evaluate(condition, facts(95, 24)).truth()).isEqualTo(Truth.TRUE);
		assertThat(evaluate(condition, facts(95, 24)).reasons()).hasSize(2);
		assertThat(evaluate(condition, facts(95, 18)).truth()).isEqualTo(Truth.FALSE);
		// One child false decides the result even when another is missing.
		assertThat(evaluate(condition, facts(80, null)).truth()).isEqualTo(Truth.FALSE);
		// One child true and one missing: cannot tell.
		Evaluation unknown = evaluate(condition, facts(95, null));
		assertThat(unknown.truth()).isEqualTo(Truth.UNKNOWN);
		assertThat(unknown.missingFacts()).containsExactly("vitals.respiratoryRate");
	}

	@Test
	void anyNeedsOneChild() {
		Condition condition = parse("""
				{"any": [{"fact": "vitals.systolicBp", "operator": "gte", "value": 140},
				         {"fact": "vitals.diastolicBp", "operator": "gte", "value": 90}]}
				""");

		Evaluation onlyDiastolic = evaluate(condition, bloodPressure(130, 95));
		assertThat(onlyDiastolic.truth()).isEqualTo(Truth.TRUE);
		assertThat(onlyDiastolic.reasons()).containsExactly(
				"Diastolic blood pressure is 95 mmHg (recorded 2026-10-13 08:00 UTC), which is greater than or equal to 90 mmHg.");
		assertThat(evaluate(condition, bloodPressure(130, 85)).truth()).isEqualTo(Truth.FALSE);
		// One child true is enough even when the other is missing.
		assertThat(evaluate(condition, bloodPressure(150, null)).truth()).isEqualTo(Truth.TRUE);
		// Nothing true and something missing: cannot tell.
		assertThat(evaluate(condition, bloodPressure(130, null)).truth()).isEqualTo(Truth.UNKNOWN);
	}

	@Test
	void notInvertsAndKeepsUnknown() {
		Condition condition = parse("""
				{"not": {"fact": "conditions", "operator": "contains", "value": "E11.9"}}
				""");

		Evaluation noDiabetes = evaluate(condition, new PatientFacts().withCodes("conditions", List.of("I10")));
		assertThat(noDiabetes.truth()).isEqualTo(Truth.TRUE);
		assertThat(noDiabetes.reasons()).containsExactly("Active conditions (ICD-10 codes) do not include E11.9.");
		assertThat(evaluate(condition, new PatientFacts().withCodes("conditions", List.of("e11.9"))).truth())
			.isEqualTo(Truth.FALSE);

		Condition notHeartRate = parse("{\"not\": {\"fact\": \"vitals.heartRate\", \"operator\": \"gt\", \"value\": 100}}");
		assertThat(evaluate(notHeartRate, new PatientFacts()).truth()).isEqualTo(Truth.UNKNOWN);
	}

	@Test
	void labFactsUseTheLabTestUnit() {
		Condition condition = parse("{\"fact\": \"lab.Fasting plasma glucose\", \"operator\": \"gte\", \"value\": 7.0}");
		PatientFacts facts = new PatientFacts().withNumber("lab.Fasting plasma glucose", new BigDecimal("7.00"), TIME);

		Evaluation result = evaluate(condition, facts);
		assertThat(result.matched()).isTrue();
		assertThat(result.reasons()).containsExactly(
				"Fasting plasma glucose is 7 mmol/L (recorded 2026-10-13 08:00 UTC), which is greater than or equal to 7 mmol/L.");
	}

	private Evaluation evaluate(Condition condition, PatientFacts facts) {
		return evaluator.evaluate(condition, facts);
	}

	private static PatientFacts heartRate(int value) {
		return new PatientFacts().withNumber("vitals.heartRate", BigDecimal.valueOf(value), TIME);
	}

	private static PatientFacts temperature(String value) {
		return new PatientFacts().withNumber("vitals.temperature", new BigDecimal(value), TIME);
	}

	private static PatientFacts facts(Integer heartRate, Integer respiratoryRate) {
		return new PatientFacts().withNumber("vitals.heartRate", number(heartRate), TIME)
			.withNumber("vitals.respiratoryRate", number(respiratoryRate), TIME);
	}

	private static PatientFacts bloodPressure(Integer systolic, Integer diastolic) {
		return new PatientFacts().withNumber("vitals.systolicBp", number(systolic), TIME)
			.withNumber("vitals.diastolicBp", number(diastolic), TIME);
	}

	private static BigDecimal number(Integer value) {
		return (value == null) ? null : BigDecimal.valueOf(value);
	}

}
