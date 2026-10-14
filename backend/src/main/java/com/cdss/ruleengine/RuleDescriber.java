package com.cdss.ruleengine;

import java.util.stream.Collectors;

/**
 * Turns a parsed condition into plain English, e.g.
 * "ANY of: (Systolic blood pressure is greater than or equal to 140 mmHg; ...)".
 * Used to show clinicians what a rule checks without its JSON definition.
 */
public final class RuleDescriber {

	private RuleDescriber() {
	}

	public static String describe(Condition condition) {
		return switch (condition) {
			case Condition.All all -> "ALL of: (" + all.conditions().stream().map(RuleDescriber::describe)
				.collect(Collectors.joining("; ")) + ")";
			case Condition.Any any -> "ANY of: (" + any.conditions().stream().map(RuleDescriber::describe)
				.collect(Collectors.joining("; ")) + ")";
			case Condition.Not not -> "NOT (" + describe(not.condition()) + ")";
			case Condition.Comparison comparison -> describe(comparison);
		};
	}

	private static String describe(Condition.Comparison comparison) {
		FactDefinition fact = comparison.fact();
		if (comparison.operator() == Operator.CONTAINS) {
			return fact.label() + " includes " + comparison.code();
		}
		String unit = fact.unit() == null ? "" : " " + fact.unit();
		return fact.label() + " is " + comparison.operator().words() + " "
				+ comparison.number().stripTrailingZeros().toPlainString() + unit;
	}

}
