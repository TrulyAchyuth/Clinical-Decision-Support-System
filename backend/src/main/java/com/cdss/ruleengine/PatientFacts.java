package com.cdss.ruleengine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The facts known about one patient at the moment of a decision.
 * A fact that was never added is <em>missing</em>: rules that depend on it
 * are reported as "insufficient data", never treated as normal.
 */
public final class PatientFacts {

	/**
	 * @param number value of a number fact (null for code lists)
	 * @param codes codes of a code-list fact (empty for numbers)
	 * @param observedAt when the value was recorded (null if not applicable)
	 */
	public record Value(BigDecimal number, Set<String> codes, Instant observedAt) {
	}

	private final Map<String, Value> values = new HashMap<>();

	/** Adds a number fact. A null value is ignored, so the fact stays missing. */
	public PatientFacts withNumber(String fact, BigDecimal number, Instant observedAt) {
		if (number != null) {
			values.put(FactCatalog.key(fact), new Value(number, Set.of(), observedAt));
		}
		return this;
	}

	/** Adds a code-list fact. Codes are compared in upper case. */
	public PatientFacts withCodes(String fact, Collection<String> codes) {
		Set<String> upperCase = new TreeSet<>();
		codes.forEach((code) -> upperCase.add(code.strip().toUpperCase(Locale.ROOT)));
		values.put(FactCatalog.key(fact), new Value(null, upperCase, null));
		return this;
	}

	public Optional<Value> get(String fact) {
		return Optional.ofNullable(values.get(FactCatalog.key(fact)));
	}

}
