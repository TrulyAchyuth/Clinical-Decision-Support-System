package com.cdss.ruleengine;

/**
 * One problem found in a rule.
 *
 * @param path where the problem is, e.g. {@code condition.any[1].operator}
 * @param message what is wrong
 */
public record RuleError(String path, String message) {
}
