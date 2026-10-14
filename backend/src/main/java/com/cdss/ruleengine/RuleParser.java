package com.cdss.ruleengine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Checks a rule's JSON condition and turns it into a {@link Condition} tree.
 * <p>
 * The JSON has already been read into plain Java objects (maps, lists,
 * strings, numbers). Accepted shapes:
 * <pre>
 * {"all": [ condition, ... ]}                         AND
 * {"any": [ condition, ... ]}                         OR
 * {"not": condition}                                  NOT
 * {"fact": "vitals.heartRate", "operator": "gt", "value": 100}
 * {"fact": "conditions", "operator": "contains", "value": "E11.9"}
 * </pre>
 * Every problem is collected, so the caller can show them all at once.
 * Nothing in the JSON is ever executed.
 */
public final class RuleParser {

	public static final int MAX_DEPTH = 6;

	public static final int MAX_CONDITIONS = 50;

	public static final int MAX_CODE_LENGTH = 20;

	private static final Set<String> GROUP_KEYS = Set.of("all", "any", "not");

	private static final Set<String> COMPARISON_KEYS = Set.of("fact", "operator", "value");

	private final FactCatalog catalog;

	public RuleParser(FactCatalog catalog) {
		this.catalog = catalog;
	}

	/**
	 * @param json the condition as read from JSON
	 * @return the parsed condition
	 * @throws RuleValidationException listing every problem found
	 */
	public Condition parse(Object json) {
		Context context = new Context();
		Condition condition = parseNode(json, "condition", 1, context);
		if (!context.errors.isEmpty()) {
			throw new RuleValidationException(context.errors);
		}
		return condition;
	}

	private Condition parseNode(Object node, String path, int depth, Context context) {
		if (!(node instanceof Map<?, ?> map)) {
			return context.error(path, "must be a JSON object");
		}
		if (depth > MAX_DEPTH) {
			return context.error(path, "conditions are nested too deeply (maximum depth is " + MAX_DEPTH + ")");
		}
		if (++context.conditionCount == MAX_CONDITIONS + 1) {
			context.error(path, "a rule may contain at most " + MAX_CONDITIONS + " conditions");
		}
		long groupKeys = map.keySet().stream().filter(GROUP_KEYS::contains).count();
		if (groupKeys > 0) {
			if (map.size() != 1) {
				return context.error(path, "a group must contain exactly one of \"all\", \"any\" or \"not\" and nothing else");
			}
			Map.Entry<?, ?> entry = map.entrySet().iterator().next();
			String key = (String) entry.getKey();
			String childPath = path + "." + key;
			if (key.equals("not")) {
				Condition child = parseNode(entry.getValue(), childPath, depth + 1, context);
				return (child == null) ? null : new Condition.Not(child);
			}
			List<Condition> children = parseList(entry.getValue(), childPath, depth, context);
			if (children == null) {
				return null;
			}
			return key.equals("all") ? new Condition.All(children) : new Condition.Any(children);
		}
		if (map.containsKey("fact")) {
			return parseComparison(map, path, context);
		}
		return context.error(path, "must contain \"all\", \"any\", \"not\" or \"fact\"");
	}

	private List<Condition> parseList(Object value, String path, int depth, Context context) {
		if (!(value instanceof List<?> list) || list.isEmpty()) {
			return context.error(path, "must be a non-empty list of conditions");
		}
		List<Condition> children = new ArrayList<>();
		boolean ok = true;
		for (int i = 0; i < list.size(); i++) {
			Condition child = parseNode(list.get(i), path + "[" + i + "]", depth + 1, context);
			ok &= (child != null);
			children.add(child);
		}
		return ok ? children : null;
	}

	private Condition parseComparison(Map<?, ?> map, String path, Context context) {
		int errorsBefore = context.errors.size();
		for (Object key : map.keySet()) {
			if (!COMPARISON_KEYS.contains(key)) {
				context.error(path + "." + key, "unknown field (allowed: fact, operator, value)");
			}
		}

		FactDefinition fact = null;
		if (map.get("fact") instanceof String factName && !factName.isBlank()) {
			Optional<FactDefinition> found = catalog.find(factName);
			if (found.isPresent()) {
				fact = found.get();
			}
			else {
				context.error(path + ".fact", "unknown fact \"" + factName + "\"");
			}
		}
		else {
			context.error(path + ".fact", "must be a fact name such as \"vitals.heartRate\"");
		}

		Operator operator = null;
		if (map.get("operator") instanceof String operatorName) {
			operator = Operator.fromJsonName(operatorName).orElse(null);
		}
		if (operator == null) {
			context.error(path + ".operator", "must be one of gt, gte, lt, lte, eq, neq, contains");
		}
		else if (fact != null && !operator.supports(fact.type())) {
			context.error(path + ".operator", (fact.type() == FactType.CODE_LIST)
					? "fact \"" + fact.name() + "\" only supports \"contains\""
					: "\"contains\" cannot be used with number fact \"" + fact.name() + "\"");
		}

		BigDecimal number = null;
		String code = null;
		Object value = map.get("value");
		if (!map.containsKey("value") || value == null) {
			context.error(path + ".value", "is required");
		}
		else if (fact != null && fact.type() == FactType.NUMBER) {
			if (value instanceof Number n && !(value instanceof Boolean)) {
				number = new BigDecimal(n.toString());
			}
			else {
				context.error(path + ".value", "must be a number for fact \"" + fact.name() + "\"");
			}
		}
		else if (fact != null && fact.type() == FactType.CODE_LIST) {
			if (value instanceof String text && !text.isBlank() && text.strip().length() <= MAX_CODE_LENGTH) {
				code = text.strip().toUpperCase(Locale.ROOT);
			}
			else {
				context.error(path + ".value", "must be a code such as \"E11.9\"");
			}
		}

		if (context.errors.size() > errorsBefore) {
			return null;
		}
		return new Condition.Comparison(fact, operator, number, code);
	}

	private static final class Context {

		private final List<RuleError> errors = new ArrayList<>();

		private int conditionCount;

		<T> T error(String path, String message) {
			errors.add(new RuleError(path, message));
			return null;
		}

	}

}
