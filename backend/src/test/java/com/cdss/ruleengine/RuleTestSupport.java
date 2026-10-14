package com.cdss.ruleengine;

import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

/** Shared helpers: a catalog like the sample knowledge base, and JSON parsing. */
final class RuleTestSupport {

	private RuleTestSupport() {
	}

	static FactCatalog catalog() {
		Map<String, String> labs = new LinkedHashMap<>();
		labs.put("Fasting plasma glucose", "mmol/L");
		labs.put("Potassium", "mmol/L");
		labs.put("HbA1c", "%");
		return new FactCatalog(labs);
	}

	static Object json(String text) {
		return JsonMapper.shared().readValue(text, Object.class);
	}

	static Condition parse(String conditionJson) {
		return new RuleParser(catalog()).parse(json(conditionJson));
	}

}
