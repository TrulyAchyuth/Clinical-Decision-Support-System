package com.cdss.ruleengine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RuleDescriberTests {

	private final RuleParser parser = new RuleParser(new FactCatalog(Map.of("Potassium", "mmol/L")));

	@Test
	void describesComparisonsWithLabelsAndUnits() {
		assertThat(describe(Map.of("fact", "vitals.heartRate", "operator", "gt", "value", 100)))
			.isEqualTo("Heart rate is greater than 100 beats/min");
		assertThat(describe(Map.of("fact", "lab.Potassium", "operator", "lte", "value", 3.50)))
			.isEqualTo("Potassium is less than or equal to 3.5 mmol/L");
		assertThat(describe(Map.of("fact", "conditions", "operator", "contains", "value", "I10")))
			.isEqualTo("Active conditions (ICD-10 codes) includes I10");
	}

	@Test
	void describesGroupsAndNegation() {
		Map<String, Object> condition = Map.of("all", List.of(
				Map.of("fact", "vitals.heartRate", "operator", "gt", "value", 90),
				Map.of("not", Map.of("fact", "conditions", "operator", "contains", "value", "I10"))));

		assertThat(describe(condition)).isEqualTo(
				"ALL of: (Heart rate is greater than 90 beats/min; NOT (Active conditions (ICD-10 codes) includes I10))");
	}

	private String describe(Map<String, Object> json) {
		return RuleDescriber.describe(parser.parse(json));
	}

}
