package com.cdss.ruleengine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The fixed list of facts that rules may use. Only these facts can appear in
 * a rule, so rules cannot reach any other data.
 * <ul>
 * <li>{@code patient.ageYears}</li>
 * <li>{@code vitals.systolicBp}, {@code vitals.diastolicBp}, {@code vitals.temperature},
 * {@code vitals.heartRate}, {@code vitals.respiratoryRate}, {@code vitals.oxygenSaturation}</li>
 * <li>{@code lab.<lab test name>} for every lab test in the knowledge base</li>
 * <li>{@code conditions}: the patient's active ICD-10 codes</li>
 * </ul>
 * Names are matched without regard to upper/lower case.
 */
public final class FactCatalog {

	public static final String AGE = "patient.ageYears";

	public static final String SYSTOLIC_BP = "vitals.systolicBp";

	public static final String DIASTOLIC_BP = "vitals.diastolicBp";

	public static final String TEMPERATURE = "vitals.temperature";

	public static final String HEART_RATE = "vitals.heartRate";

	public static final String RESPIRATORY_RATE = "vitals.respiratoryRate";

	public static final String OXYGEN_SATURATION = "vitals.oxygenSaturation";

	public static final String CONDITIONS = "conditions";

	public static final String LAB_PREFIX = "lab.";

	private static final List<FactDefinition> FIXED_FACTS = List.of(
			new FactDefinition(AGE, "Age", "years", FactType.NUMBER),
			new FactDefinition(SYSTOLIC_BP, "Systolic blood pressure", "mmHg", FactType.NUMBER),
			new FactDefinition(DIASTOLIC_BP, "Diastolic blood pressure", "mmHg", FactType.NUMBER),
			new FactDefinition(TEMPERATURE, "Temperature", "°C", FactType.NUMBER),
			new FactDefinition(HEART_RATE, "Heart rate", "beats/min", FactType.NUMBER),
			new FactDefinition(RESPIRATORY_RATE, "Respiratory rate", "breaths/min", FactType.NUMBER),
			new FactDefinition(OXYGEN_SATURATION, "Oxygen saturation", "%", FactType.NUMBER),
			new FactDefinition(CONDITIONS, "Active conditions (ICD-10 codes)", null, FactType.CODE_LIST));

	private final Map<String, FactDefinition> factsByLowerCaseName = new LinkedHashMap<>();

	/**
	 * @param labTestUnits lab test name to unit, from the knowledge base
	 */
	public FactCatalog(Map<String, String> labTestUnits) {
		FIXED_FACTS.forEach(this::add);
		labTestUnits.forEach((name, unit) -> add(new FactDefinition(LAB_PREFIX + name, name, unit, FactType.NUMBER)));
	}

	public static String labFactName(String labTestName) {
		return LAB_PREFIX + labTestName;
	}

	public Optional<FactDefinition> find(String name) {
		return Optional.ofNullable(factsByLowerCaseName.get(key(name)));
	}

	public List<FactDefinition> all() {
		return Collections.unmodifiableList(new ArrayList<>(factsByLowerCaseName.values()));
	}

	private void add(FactDefinition fact) {
		factsByLowerCaseName.put(key(fact.name()), fact);
	}

	static String key(String name) {
		return name.strip().toLowerCase(Locale.ROOT);
	}

}
