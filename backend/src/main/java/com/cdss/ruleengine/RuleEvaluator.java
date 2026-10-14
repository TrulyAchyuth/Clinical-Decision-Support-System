package com.cdss.ruleengine;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Evaluates a parsed {@link Condition} against a patient's facts.
 * <p>
 * Missing data gives {@link Truth#UNKNOWN}, combined like this:
 * <ul>
 * <li>ALL: FALSE if any child is FALSE; otherwise UNKNOWN if any is UNKNOWN; otherwise TRUE</li>
 * <li>ANY: TRUE if any child is TRUE; otherwise UNKNOWN if any is UNKNOWN; otherwise FALSE</li>
 * <li>NOT: swaps TRUE and FALSE; UNKNOWN stays UNKNOWN</li>
 * </ul>
 * So a rule can still match when only some data is missing (for example one
 * branch of an ANY is true), but missing data is never treated as normal.
 * The reasons returned are the statements that decided the result.
 */
public final class RuleEvaluator {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
		.withZone(ZoneOffset.UTC);

	public Evaluation evaluate(Condition condition, PatientFacts facts) {
		return switch (condition) {
			case Condition.All all -> evaluateAll(all.conditions(), facts);
			case Condition.Any any -> evaluateAny(any.conditions(), facts);
			case Condition.Not not -> negate(evaluate(not.condition(), facts));
			case Condition.Comparison comparison -> evaluateComparison(comparison, facts);
		};
	}

	private Evaluation evaluateAll(List<Condition> conditions, PatientFacts facts) {
		List<Evaluation> results = conditions.stream().map((c) -> evaluate(c, facts)).toList();
		if (anyHas(results, Truth.FALSE)) {
			return combine(Truth.FALSE, results, Truth.FALSE);
		}
		if (anyHas(results, Truth.UNKNOWN)) {
			return combine(Truth.UNKNOWN, results, Truth.UNKNOWN);
		}
		return combine(Truth.TRUE, results, Truth.TRUE);
	}

	private Evaluation evaluateAny(List<Condition> conditions, PatientFacts facts) {
		List<Evaluation> results = conditions.stream().map((c) -> evaluate(c, facts)).toList();
		if (anyHas(results, Truth.TRUE)) {
			return combine(Truth.TRUE, results, Truth.TRUE);
		}
		if (anyHas(results, Truth.UNKNOWN)) {
			return combine(Truth.UNKNOWN, results, Truth.UNKNOWN);
		}
		return combine(Truth.FALSE, results, Truth.FALSE);
	}

	private static Evaluation negate(Evaluation inner) {
		Truth truth = switch (inner.truth()) {
			case TRUE -> Truth.FALSE;
			case FALSE -> Truth.TRUE;
			case UNKNOWN -> Truth.UNKNOWN;
		};
		return new Evaluation(truth, inner.reasons(), inner.missingFacts());
	}

	private static Evaluation evaluateComparison(Condition.Comparison comparison, PatientFacts facts) {
		FactDefinition fact = comparison.fact();
		Optional<PatientFacts.Value> value = facts.get(fact.name());
		if (value.isEmpty()) {
			return new Evaluation(Truth.UNKNOWN, List.of(), Set.of(fact.name()));
		}
		if (fact.type() == FactType.CODE_LIST) {
			boolean included = value.get().codes().contains(comparison.code());
			String reason = fact.label() + (included ? " include " : " do not include ") + comparison.code() + ".";
			return new Evaluation(included ? Truth.TRUE : Truth.FALSE, List.of(reason), Set.of());
		}
		BigDecimal actual = value.get().number();
		BigDecimal threshold = comparison.number();
		boolean holds = compare(actual, comparison.operator(), threshold);
		String reason = fact.label() + " is " + withUnit(actual, fact) + recordedAt(value.get().observedAt())
				+ ", which is " + (holds ? "" : "not ") + comparison.operator().words() + " "
				+ withUnit(threshold, fact) + ".";
		return new Evaluation(holds ? Truth.TRUE : Truth.FALSE, List.of(reason), Set.of());
	}

	static boolean compare(BigDecimal actual, Operator operator, BigDecimal threshold) {
		int order = actual.compareTo(threshold);
		return switch (operator) {
			case GT -> order > 0;
			case GTE -> order >= 0;
			case LT -> order < 0;
			case LTE -> order <= 0;
			case EQ -> order == 0;
			case NEQ -> order != 0;
			case CONTAINS -> throw new IllegalArgumentException("contains is not a number comparison");
		};
	}

	private static boolean anyHas(List<Evaluation> results, Truth truth) {
		return results.stream().anyMatch((result) -> result.truth() == truth);
	}

	/**
	 * Keeps the reasons of the children whose result decided the outcome (each
	 * reason once, in order), and all missing facts.
	 */
	private static Evaluation combine(Truth truth, List<Evaluation> results, Truth deciding) {
		Set<String> reasons = new LinkedHashSet<>();
		Set<String> missing = new LinkedHashSet<>();
		for (Evaluation result : results) {
			if (result.truth() == deciding) {
				reasons.addAll(result.reasons());
			}
			missing.addAll(result.missingFacts());
		}
		return new Evaluation(truth, new ArrayList<>(reasons), Set.copyOf(missing));
	}

	private static String withUnit(BigDecimal number, FactDefinition fact) {
		String text = number.stripTrailingZeros().toPlainString();
		if (fact.unit() == null) {
			return text;
		}
		return fact.unit().equals("%") ? text + "%" : text + " " + fact.unit();
	}

	private static String recordedAt(Instant observedAt) {
		return (observedAt == null) ? "" : " (recorded " + TIME.format(observedAt) + ")";
	}

}
