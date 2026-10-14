package com.cdss.ruleengine;

import java.util.Arrays;
import java.util.Optional;

/** Comparison operators allowed in rule conditions. */
public enum Operator {

	GT("gt", "greater than"),
	GTE("gte", "greater than or equal to"),
	LT("lt", "less than"),
	LTE("lte", "less than or equal to"),
	EQ("eq", "equal to"),
	NEQ("neq", "not equal to"),
	/** Only for {@link FactType#CODE_LIST} facts: the list includes the code. */
	CONTAINS("contains", "includes");

	private final String jsonName;

	private final String words;

	Operator(String jsonName, String words) {
		this.jsonName = jsonName;
		this.words = words;
	}

	/** The name used in rule JSON, e.g. "gte". */
	public String jsonName() {
		return jsonName;
	}

	/** Plain-English wording used in explanations. */
	public String words() {
		return words;
	}

	public boolean supports(FactType type) {
		return (this == CONTAINS) == (type == FactType.CODE_LIST);
	}

	public static Optional<Operator> fromJsonName(String name) {
		return Arrays.stream(values()).filter((operator) -> operator.jsonName.equals(name)).findFirst();
	}

}
