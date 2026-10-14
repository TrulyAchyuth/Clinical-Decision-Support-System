package com.cdss.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cdss.dto.DecisionResponse;
import com.cdss.dto.RuleDryRunRequest;
import com.cdss.dto.RuleDryRunResponse;
import com.cdss.entity.ClinicalDecision;
import com.cdss.entity.ClinicalRule;
import com.cdss.entity.DecisionOutcome;
import com.cdss.entity.DecisionRuleResult;
import com.cdss.entity.Patient;
import com.cdss.entity.RuleResultType;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.repository.ClinicalDecisionRepository;
import com.cdss.repository.ClinicalRuleRepository;
import com.cdss.repository.PatientRepository;
import com.cdss.ruleengine.Condition;
import com.cdss.ruleengine.Evaluation;
import com.cdss.ruleengine.FactCatalog;
import com.cdss.ruleengine.PatientFacts;
import com.cdss.ruleengine.RuleError;
import com.cdss.ruleengine.RuleEvaluator;
import com.cdss.ruleengine.RuleParser;
import com.cdss.ruleengine.RuleValidationException;
import com.cdss.ruleengine.Truth;

/**
 * Runs every enabled rule against a patient's recorded data and stores the
 * result, so earlier decisions can be reviewed later.
 * <p>
 * Each rule ends up as one of: matched, not matched, insufficient data
 * (needed data missing or too old) or invalid (stored JSON no longer passes
 * validation, e.g. it names a lab test that was renamed). Only "not matched"
 * rules are just counted; all others are stored with their reasons.
 */
@Service
@Transactional
public class DecisionService {

	private final PatientRepository patientRepository;

	private final ClinicalRuleRepository ruleRepository;

	private final ClinicalDecisionRepository decisionRepository;

	private final ClinicalRuleService ruleService;

	private final PatientFactsService factsService;

	private final Clock clock;

	private final RuleEvaluator evaluator = new RuleEvaluator();

	public DecisionService(PatientRepository patientRepository, ClinicalRuleRepository ruleRepository,
			ClinicalDecisionRepository decisionRepository, ClinicalRuleService ruleService,
			PatientFactsService factsService, Clock clock) {
		this.patientRepository = patientRepository;
		this.ruleRepository = ruleRepository;
		this.decisionRepository = decisionRepository;
		this.ruleService = ruleService;
		this.factsService = factsService;
		this.clock = clock;
	}

	public DecisionResponse requestDecision(Long patientId) {
		Patient patient = getPatient(patientId);
		Instant now = clock.instant();
		FactCatalog catalog = ruleService.factCatalog();
		PatientFacts facts = factsService.collect(patient, now);
		List<ClinicalRule> rules = ruleRepository.findByEnabledTrueOrderByPriorityAscIdAsc();

		ClinicalDecision decision = new ClinicalDecision();
		decision.setPatient(patient);
		decision.setRequestedAt(now);
		decision.setRulesEvaluated(rules.size());
		for (ClinicalRule rule : rules) {
			DecisionRuleResult result = evaluateRule(rule, catalog, facts);
			if (result != null) {
				decision.addRuleResult(result);
			}
		}
		int matched = count(decision, RuleResultType.MATCHED);
		decision.setOutcome(matched > 0 ? DecisionOutcome.RULES_MATCHED : DecisionOutcome.NO_RULES_MATCHED);
		decision.setSummary(summary(rules.size(), matched, count(decision, RuleResultType.INSUFFICIENT_DATA),
				count(decision, RuleResultType.INVALID_RULE)));
		return DecisionResponse.from(decisionRepository.save(decision));
	}

	@Transactional(readOnly = true)
	public List<DecisionResponse> findDecisions(Long patientId) {
		getPatient(patientId);
		return decisionRepository.findByPatientIdOrderByRequestedAtDescIdDesc(patientId)
			.stream()
			.map(DecisionResponse::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public DecisionResponse findDecision(Long patientId, Long decisionId) {
		getPatient(patientId);
		return decisionRepository.findByIdAndPatientId(decisionId, patientId)
			.map(DecisionResponse::from)
			.orElseThrow(() -> new ResourceNotFoundException("Decision", decisionId));
	}

	/** Evaluates a condition that is not saved against a patient's data now. Nothing is stored. */
	@Transactional(readOnly = true)
	public RuleDryRunResponse dryRun(RuleDryRunRequest request) {
		Patient patient = getPatient(request.patientId());
		Instant now = clock.instant();
		FactCatalog catalog = ruleService.factCatalog();
		Condition condition;
		try {
			condition = new RuleParser(catalog).parse(request.condition());
		}
		catch (RuleValidationException ex) {
			return new RuleDryRunResponse(patient.getId(), false, ex.getErrors(), null, List.of(), now,
					RuleDryRunResponse.NOTE);
		}
		Evaluation evaluation = evaluator.evaluate(condition, factsService.collect(patient, now));
		if (evaluation.truth() == Truth.UNKNOWN) {
			List<String> missing = evaluation.missingFacts()
				.stream()
				.sorted()
				.map((name) -> catalog.find(name).map(factsService::describeMissing).orElse("No value for " + name))
				.toList();
			return new RuleDryRunResponse(patient.getId(), true, List.of(), "INSUFFICIENT_DATA", missing, now,
					RuleDryRunResponse.NOTE);
		}
		return new RuleDryRunResponse(patient.getId(), true, List.of(),
				evaluation.truth() == Truth.TRUE ? "MATCHED" : "NOT_MATCHED", evaluation.reasons(), now,
				RuleDryRunResponse.NOTE);
	}

	/** Returns the stored result for this rule, or null if it was evaluated and did not match. */
	private DecisionRuleResult evaluateRule(ClinicalRule rule, FactCatalog catalog, PatientFacts facts) {
		Condition condition;
		try {
			condition = new RuleParser(catalog).parse(ClinicalRuleService.readCondition(rule.getConditionJson()));
		}
		catch (RuleValidationException ex) {
			return result(rule, RuleResultType.INVALID_RULE,
					ex.getErrors().stream().map((RuleError e) -> e.path() + ": " + e.message()).toList(), List.of());
		}

		Evaluation evaluation = evaluator.evaluate(condition, facts);
		if (evaluation.truth() == Truth.TRUE) {
			return result(rule, RuleResultType.MATCHED, evaluation.reasons(), rule.getRecommendedActions());
		}
		if (evaluation.truth() == Truth.UNKNOWN) {
			List<String> missing = evaluation.missingFacts()
				.stream()
				.sorted()
				.map((name) -> catalog.find(name).map(factsService::describeMissing).orElse("No value for " + name))
				.toList();
			return result(rule, RuleResultType.INSUFFICIENT_DATA, missing, List.of());
		}
		return null;
	}

	private static DecisionRuleResult result(ClinicalRule rule, RuleResultType type, List<String> reasons,
			List<String> actions) {
		DecisionRuleResult result = new DecisionRuleResult();
		result.setRuleId(rule.getId());
		result.setRuleCode(rule.getRuleCode());
		result.setRuleName(rule.getName());
		result.setSeverity(rule.getSeverity());
		result.setPriority(rule.getPriority());
		result.setResult(type);
		result.setReasons(List.copyOf(reasons));
		result.setRecommendedActions(List.copyOf(actions));
		return result;
	}

	static String summary(int evaluated, int matched, int insufficient, int invalid) {
		StringBuilder summary = new StringBuilder();
		if (evaluated == 0) {
			summary.append("No active rules are configured, so nothing was evaluated.");
		}
		else if (matched > 0) {
			summary.append(matched).append(" of ").append(evaluated)
				.append(" active rules matched. Review the reasons and suggested actions; a match is not a diagnosis.");
		}
		else {
			summary.append("No active rule matched the recorded data. This does not rule out any condition.");
		}
		if (insufficient > 0) {
			summary.append(' ').append(insufficient)
				.append(insufficient == 1 ? " rule" : " rules")
				.append(" could not be evaluated because data was missing or too old.");
		}
		if (invalid > 0) {
			summary.append(' ').append(invalid)
				.append(invalid == 1 ? " rule was" : " rules were")
				.append(" skipped because the rule definition is invalid.");
		}
		return summary.toString();
	}

	private static int count(ClinicalDecision decision, RuleResultType type) {
		return (int) decision.getRuleResults().stream().filter((result) -> result.getResult() == type).count();
	}

	private Patient getPatient(Long patientId) {
		return patientRepository.findById(patientId)
			.orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
	}

}
