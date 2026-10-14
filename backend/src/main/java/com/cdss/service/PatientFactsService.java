package com.cdss.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cdss.config.DecisionProperties;
import com.cdss.entity.ConditionStatus;
import com.cdss.entity.LabResult;
import com.cdss.entity.Patient;
import com.cdss.entity.VitalSigns;
import com.cdss.repository.LabResultRepository;
import com.cdss.repository.PatientConditionRepository;
import com.cdss.repository.VitalSignsRepository;
import com.cdss.ruleengine.FactCatalog;
import com.cdss.ruleengine.FactDefinition;
import com.cdss.ruleengine.PatientFacts;

/**
 * Collects the facts the rules can see for one patient:
 * <ul>
 * <li>age in whole years;</li>
 * <li>the most recent value of each vital sign recorded within
 * {@code cdss.decision.vital-signs-max-age};</li>
 * <li>the most recent result of each lab test within
 * {@code cdss.decision.lab-results-max-age};</li>
 * <li>the ICD-10 codes of the patient's ACTIVE conditions.</li>
 * </ul>
 * Anything not recorded, or too old, is left out and therefore "missing".
 */
@Service
@Transactional(readOnly = true)
public class PatientFactsService {

	private final VitalSignsRepository vitalSignsRepository;

	private final LabResultRepository labResultRepository;

	private final PatientConditionRepository conditionRepository;

	private final DecisionProperties properties;

	public PatientFactsService(VitalSignsRepository vitalSignsRepository, LabResultRepository labResultRepository,
			PatientConditionRepository conditionRepository, DecisionProperties properties) {
		this.vitalSignsRepository = vitalSignsRepository;
		this.labResultRepository = labResultRepository;
		this.conditionRepository = conditionRepository;
		this.properties = properties;
	}

	public PatientFacts collect(Patient patient, Instant now) {
		PatientFacts facts = new PatientFacts();
		LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
		facts.withNumber(FactCatalog.AGE,
				BigDecimal.valueOf(Period.between(patient.getDateOfBirth(), today).getYears()), null);

		List<VitalSigns> vitals = vitalSignsRepository.findByPatientIdAndRecordedAtGreaterThanEqualOrderByRecordedAtDescIdDesc(
				patient.getId(), now.minus(properties.vitalSignsMaxAge()));
		addLatest(facts, vitals, FactCatalog.SYSTOLIC_BP, (v) -> number(v.getSystolicBp()));
		addLatest(facts, vitals, FactCatalog.DIASTOLIC_BP, (v) -> number(v.getDiastolicBp()));
		addLatest(facts, vitals, FactCatalog.TEMPERATURE, VitalSigns::getTemperature);
		addLatest(facts, vitals, FactCatalog.HEART_RATE, (v) -> number(v.getHeartRate()));
		addLatest(facts, vitals, FactCatalog.RESPIRATORY_RATE, (v) -> number(v.getRespiratoryRate()));
		addLatest(facts, vitals, FactCatalog.OXYGEN_SATURATION, (v) -> number(v.getOxygenSaturation()));

		Set<Long> labTestsSeen = new HashSet<>();
		for (LabResult result : labResultRepository.findByPatientIdAndMeasuredAtGreaterThanEqualOrderByMeasuredAtDescIdDesc(
				patient.getId(), now.minus(properties.labResultsMaxAge()))) {
			if (labTestsSeen.add(result.getLabTest().getId())) {
				facts.withNumber(FactCatalog.labFactName(result.getLabTest().getName()), result.getValue(),
						result.getMeasuredAt());
			}
		}

		facts.withCodes(FactCatalog.CONDITIONS,
				conditionRepository.findByPatientIdAndStatus(patient.getId(), ConditionStatus.ACTIVE)
					.stream()
					.map((condition) -> condition.getDisease().getIcd10Code())
					.toList());
		return facts;
	}

	/** Explains why a fact is missing, e.g. "No heart rate recorded in the last 24 hours". */
	public String describeMissing(FactDefinition fact) {
		if (fact.name().startsWith(FactCatalog.LAB_PREFIX)) {
			return "No " + fact.label() + " result in the last " + properties.labResultsMaxAge().toHours() + " hours";
		}
		if (fact.name().startsWith("vitals.")) {
			return "No " + fact.label().toLowerCase(Locale.ROOT) + " recorded in the last "
					+ properties.vitalSignsMaxAge().toHours() + " hours";
		}
		return "No value for " + fact.label();
	}

	/** Uses the newest recording that has a value for this vital sign. */
	private static void addLatest(PatientFacts facts, List<VitalSigns> newestFirst, String fact,
			Function<VitalSigns, BigDecimal> value) {
		for (VitalSigns vitals : newestFirst) {
			BigDecimal number = value.apply(vitals);
			if (number != null) {
				facts.withNumber(fact, number, vitals.getRecordedAt());
				return;
			}
		}
	}

	private static BigDecimal number(Integer value) {
		return (value == null) ? null : BigDecimal.valueOf(value);
	}

}
