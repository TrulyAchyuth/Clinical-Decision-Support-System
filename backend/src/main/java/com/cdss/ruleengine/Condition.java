package com.cdss.ruleengine;

import java.math.BigDecimal;
import java.util.List;

/**
 * A parsed rule condition. It is a small tree:
 * <ul>
 * <li>{@link All}: every child must be true (AND)</li>
 * <li>{@link Any}: at least one child must be true (OR)</li>
 * <li>{@link Not}: the child must be false (NOT)</li>
 * <li>{@link Comparison}: compares one fact with a value</li>
 * </ul>
 * Created by {@link RuleParser}, evaluated by {@link RuleEvaluator}.
 */
public sealed interface Condition {

	record All(List<Condition> conditions) implements Condition {
	}

	record Any(List<Condition> conditions) implements Condition {
	}

	record Not(Condition condition) implements Condition {
	}

	/**
	 * @param fact the fact being tested
	 * @param operator how to compare
	 * @param number the threshold, for number facts (otherwise null)
	 * @param code the code to look for, for code-list facts (otherwise null)
	 */
	record Comparison(FactDefinition fact, Operator operator, BigDecimal number, String code) implements Condition {
	}

}
