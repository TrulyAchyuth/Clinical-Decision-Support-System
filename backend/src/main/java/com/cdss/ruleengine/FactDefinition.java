package com.cdss.ruleengine;

/**
 * A fact that rules may refer to.
 *
 * @param name the name used in rule JSON, e.g. {@code vitals.heartRate}
 * @param label human-readable name used in explanations, e.g. "Heart rate"
 * @param unit unit of a number fact (may be null)
 * @param type the kind of value
 */
public record FactDefinition(String name, String label, String unit, FactType type) {
}
