package com.cdss.ruleengine;

import java.util.List;
import java.util.Set;

/**
 * Result of evaluating a condition.
 *
 * @param truth TRUE (matched), FALSE (not matched) or UNKNOWN (data missing)
 * @param reasons plain-English statements explaining the result, e.g.
 * "Heart rate is 118 beats/min (recorded 2026-10-13 08:00 UTC), which is greater than 100 beats/min."
 * @param missingFacts facts that had no value, by name
 */
public record Evaluation(Truth truth, List<String> reasons, Set<String> missingFacts) {

	public boolean matched() {
		return truth == Truth.TRUE;
	}

}
