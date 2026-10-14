package com.cdss.ruleengine;

import static com.cdss.ruleengine.RuleTestSupport.catalog;
import static com.cdss.ruleengine.RuleTestSupport.json;
import static com.cdss.ruleengine.RuleTestSupport.parse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class RuleParserTests {

	@Test
	void parsesSimpleComparison() {
		Condition condition = parse("""
				{"fact": "vitals.heartRate", "operator": "gt", "value": 100}
				""");

		assertThat(condition).isInstanceOfSatisfying(Condition.Comparison.class, (comparison) -> {
			assertThat(comparison.fact().name()).isEqualTo("vitals.heartRate");
			assertThat(comparison.operator()).isEqualTo(Operator.GT);
			assertThat(comparison.number()).isEqualByComparingTo("100");
		});
	}

	@Test
	void parsesNestedAllAnyNot() {
		Condition condition = parse("""
				{"all": [
				  {"any": [{"fact": "vitals.temperature", "operator": "gt", "value": 38.0},
				           {"fact": "vitals.temperature", "operator": "lt", "value": 36.0}]},
				  {"not": {"fact": "conditions", "operator": "contains", "value": "j45.9"}}
				]}
				""");

		Condition.All all = (Condition.All) condition;
		assertThat(all.conditions()).hasSize(2);
		assertThat(all.conditions().get(0)).isInstanceOf(Condition.Any.class);
		Condition.Not not = (Condition.Not) all.conditions().get(1);
		assertThat(((Condition.Comparison) not.condition()).code()).isEqualTo("J45.9");
	}

	@Test
	void factNamesAreCaseInsensitiveAndLabFactsComeFromTheKnowledgeBase() {
		Condition condition = parse("""
				{"fact": "LAB.fasting PLASMA glucose", "operator": "gte", "value": 7}
				""");

		Condition.Comparison comparison = (Condition.Comparison) condition;
		assertThat(comparison.fact().name()).isEqualTo("lab.Fasting plasma glucose");
		assertThat(comparison.fact().unit()).isEqualTo("mmol/L");
	}

	@Test
	void decimalValuesAreKeptExactly() {
		Condition.Comparison comparison = (Condition.Comparison) parse("""
				{"fact": "vitals.temperature", "operator": "gt", "value": 38.3}
				""");

		assertThat(comparison.number()).isEqualTo(new BigDecimal("38.3"));
	}

	@Test
	void rejectsUnknownFactOperatorAndWrongValueType() {
		assertThat(errorsFor("""
				{"fact": "vitals.bloodSugar", "operator": "greater", "value": 5}
				""")).containsExactlyInAnyOrder(
						new RuleError("condition.fact", "unknown fact \"vitals.bloodSugar\""),
						new RuleError("condition.operator", "must be one of gt, gte, lt, lte, eq, neq, contains"));

		assertThat(errorsFor("""
				{"fact": "vitals.heartRate", "operator": "gt", "value": "100"}
				""")).containsExactly(
						new RuleError("condition.value", "must be a number for fact \"vitals.heartRate\""));
	}

	@Test
	void rejectsLabTestNotInKnowledgeBase() {
		assertThat(errorsFor("""
				{"fact": "lab.Sodium", "operator": "lt", "value": 130}
				""")).extracting(RuleError::message).containsExactly("unknown fact \"lab.Sodium\"");
	}

	@Test
	void rejectsOperatorThatDoesNotSuitTheFact() {
		assertThat(errorsFor("""
				{"fact": "vitals.heartRate", "operator": "contains", "value": 100}
				""")).extracting(RuleError::path).contains("condition.operator");
		assertThat(errorsFor("""
				{"fact": "conditions", "operator": "gt", "value": "I10"}
				""")).extracting(RuleError::message).contains("fact \"conditions\" only supports \"contains\"");
	}

	@Test
	void rejectsMissingValueBooleansAndUnknownFields() {
		assertThat(errorsFor("""
				{"fact": "vitals.heartRate", "operator": "gt"}
				""")).containsExactly(new RuleError("condition.value", "is required"));
		assertThat(errorsFor("""
				{"fact": "vitals.heartRate", "operator": "gt", "value": true}
				""")).extracting(RuleError::path).containsExactly("condition.value");
		assertThat(errorsFor("""
				{"fact": "vitals.heartRate", "operator": "gt", "value": 100, "units": "bpm"}
				""")).containsExactly(new RuleError("condition.units", "unknown field (allowed: fact, operator, value)"));
	}

	@Test
	void rejectsBadGroups() {
		assertThat(errorsFor("{\"all\": []}"))
			.containsExactly(new RuleError("condition.all", "must be a non-empty list of conditions"));
		assertThat(errorsFor("{\"not\": [{\"fact\": \"vitals.heartRate\", \"operator\": \"gt\", \"value\": 1}]}"))
			.containsExactly(new RuleError("condition.not", "must be a JSON object"));
		assertThat(errorsFor("{\"all\": [], \"any\": []}")).extracting(RuleError::message)
			.containsExactly("a group must contain exactly one of \"all\", \"any\" or \"not\" and nothing else");
		assertThat(errorsFor("{\"something\": 1}")).extracting(RuleError::message)
			.containsExactly("must contain \"all\", \"any\", \"not\" or \"fact\"");
		assertThat(errorsFor("[1, 2]")).extracting(RuleError::message).containsExactly("must be a JSON object");
	}

	@Test
	void reportsEveryErrorWithItsPath() {
		List<RuleError> errors = errorsFor("""
				{"any": [
				  {"fact": "vitals.heartRate", "operator": "gt", "value": 100},
				  {"fact": "vitals.pulse", "operator": "gt", "value": 100},
				  {"all": [{"fact": "vitals.temperature", "operator": "between", "value": 38}]}
				]}
				""");

		assertThat(errors).extracting(RuleError::path)
			.containsExactlyInAnyOrder("condition.any[1].fact", "condition.any[2].all[0].operator");
	}

	@Test
	void rejectsConditionsNestedTooDeeply() {
		String leaf = "{\"fact\": \"vitals.heartRate\", \"operator\": \"gt\", \"value\": 100}";
		String nested = leaf;
		for (int i = 0; i < RuleParser.MAX_DEPTH; i++) {
			nested = "{\"not\": " + nested + "}";
		}
		String finalNested = nested;
		assertThatThrownBy(() -> parse(finalNested)).isInstanceOf(RuleValidationException.class)
			.hasMessageContaining("nested too deeply");

		String deepestAllowed = leaf;
		for (int i = 1; i < RuleParser.MAX_DEPTH; i++) {
			deepestAllowed = "{\"not\": " + deepestAllowed + "}";
		}
		assertThat(parse(deepestAllowed)).isInstanceOf(Condition.Not.class);
	}

	@Test
	void rejectsTooManyConditions() {
		StringBuilder items = new StringBuilder();
		for (int i = 0; i < RuleParser.MAX_CONDITIONS; i++) {
			items.append(i == 0 ? "" : ",").append("{\"fact\": \"vitals.heartRate\", \"operator\": \"gt\", \"value\": ")
				.append(i).append('}');
		}
		assertThat(errorsFor("{\"any\": [" + items + "]}")).extracting(RuleError::message)
			.containsExactly("a rule may contain at most " + RuleParser.MAX_CONDITIONS + " conditions");
	}

	private static List<RuleError> errorsFor(String conditionJson) {
		try {
			new RuleParser(catalog()).parse(json(conditionJson));
		}
		catch (RuleValidationException ex) {
			return ex.getErrors();
		}
		throw new AssertionError("expected the rule to be rejected: " + conditionJson);
	}

}
