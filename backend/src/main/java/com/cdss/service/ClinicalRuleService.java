package com.cdss.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cdss.dto.FactResponse;
import com.cdss.dto.RuleRequest;
import com.cdss.dto.RuleResponse;
import com.cdss.dto.RuleSummaryResponse;
import com.cdss.dto.RuleValidationResponse;
import com.cdss.entity.ClinicalRule;
import com.cdss.entity.LabTest;
import com.cdss.exception.DuplicateResourceException;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.repository.ClinicalRuleRepository;
import com.cdss.repository.LabTestRepository;
import com.cdss.ruleengine.FactCatalog;
import com.cdss.ruleengine.RuleDescriber;
import com.cdss.ruleengine.RuleError;
import com.cdss.ruleengine.RuleParser;
import com.cdss.ruleengine.RuleValidationException;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Stores and validates clinical rules. Every rule is checked by {@link RuleParser} before it is saved. */
@Service
@Transactional
public class ClinicalRuleService {

	private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
	};

	private final ClinicalRuleRepository ruleRepository;

	private final LabTestRepository labTestRepository;

	public ClinicalRuleService(ClinicalRuleRepository ruleRepository, LabTestRepository labTestRepository) {
		this.ruleRepository = ruleRepository;
		this.labTestRepository = labTestRepository;
	}

	/** The facts rules can use right now (includes one fact per knowledge-base lab test). */
	@Transactional(readOnly = true)
	public FactCatalog factCatalog() {
		Map<String, String> labUnits = new LinkedHashMap<>();
		for (LabTest labTest : labTestRepository.findAllByOrderByNameAsc()) {
			labUnits.put(labTest.getName(), labTest.getUnit());
		}
		return new FactCatalog(labUnits);
	}

	@Transactional(readOnly = true)
	public List<FactResponse> facts() {
		return factCatalog().all().stream().map(FactResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public List<RuleResponse> findAll() {
		FactCatalog catalog = factCatalog();
		return ruleRepository.findAllByOrderByPriorityAscIdAsc().stream().map((rule) -> toResponse(rule, catalog)).toList();
	}

	/** Every rule in plain English, without its JSON definition (for clinicians). */
	@Transactional(readOnly = true)
	public List<RuleSummaryResponse> summaries() {
		FactCatalog catalog = factCatalog();
		return ruleRepository.findAllByOrderByPriorityAscIdAsc().stream().map((rule) -> {
			String summary;
			boolean valid = true;
			try {
				summary = RuleDescriber.describe(new RuleParser(catalog).parse(readCondition(rule.getConditionJson())));
			}
			catch (RuleValidationException ex) {
				valid = false;
				summary = "This rule's condition is no longer valid and is skipped in decisions.";
			}
			return new RuleSummaryResponse(rule.getId(), rule.getRuleCode(), rule.getName(), rule.getDescription(),
					rule.getSeverity(), rule.getPriority(), rule.isEnabled(), valid, summary,
					List.copyOf(rule.getRecommendedActions()));
		}).toList();
	}

	@Transactional(readOnly = true)
	public RuleResponse findById(Long id) {
		return toResponse(getRule(id), factCatalog());
	}

	/** Checks a rule without saving it. */
	@Transactional(readOnly = true)
	public RuleValidationResponse validate(RuleRequest request) {
		try {
			new RuleParser(factCatalog()).parse(request.condition());
			return new RuleValidationResponse(true, List.of());
		}
		catch (RuleValidationException ex) {
			return new RuleValidationResponse(false, ex.getErrors());
		}
	}

	public RuleResponse create(RuleRequest request) {
		String ruleCode = normalizeCode(request.ruleId());
		if (ruleRepository.existsByRuleCodeIgnoreCase(ruleCode)) {
			throw duplicate(ruleCode);
		}
		new RuleParser(factCatalog()).parse(request.condition());
		ClinicalRule rule = new ClinicalRule();
		copy(request, rule);
		return toResponse(ruleRepository.save(rule), factCatalog());
	}

	public RuleResponse update(Long id, RuleRequest request) {
		ClinicalRule rule = getRule(id);
		String ruleCode = normalizeCode(request.ruleId());
		if (ruleRepository.existsByRuleCodeIgnoreCaseAndIdNot(ruleCode, id)) {
			throw duplicate(ruleCode);
		}
		new RuleParser(factCatalog()).parse(request.condition());
		copy(request, rule);
		return toResponse(ruleRepository.saveAndFlush(rule), factCatalog());
	}

	/**
	 * Switches a rule on. The stored rule is checked again first, because the
	 * facts may have changed since it was saved (e.g. a lab test renamed); an
	 * invalid rule is refused with its problems and stays off.
	 */
	public RuleResponse activate(Long id) {
		ClinicalRule rule = getRule(id);
		new RuleParser(factCatalog()).parse(readCondition(rule.getConditionJson()));
		rule.setEnabled(true);
		return toResponse(ruleRepository.saveAndFlush(rule), factCatalog());
	}

	/** Switches a rule off. Not validated, so even a rule that has become invalid can be switched off. */
	public RuleResponse deactivate(Long id) {
		ClinicalRule rule = getRule(id);
		rule.setEnabled(false);
		return toResponse(ruleRepository.saveAndFlush(rule), factCatalog());
	}

	/** Deletes a rule. Past decisions keep their copy of the rule's name and results. */
	public void delete(Long id) {
		ruleRepository.delete(getRule(id));
	}

	static Map<String, Object> readCondition(String conditionJson) {
		return JsonMapper.shared().readValue(conditionJson, JSON_OBJECT);
	}

	private ClinicalRule getRule(Long id) {
		return ruleRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Rule", id));
	}

	private static String normalizeCode(String ruleId) {
		return ruleId.strip().toUpperCase(Locale.ROOT);
	}

	private static DuplicateResourceException duplicate(String ruleCode) {
		return new DuplicateResourceException("A rule with ruleId '" + ruleCode + "' already exists");
	}

	private static void copy(RuleRequest request, ClinicalRule rule) {
		rule.setRuleCode(normalizeCode(request.ruleId()));
		rule.setName(Text.trimToNull(request.name()));
		rule.setDescription(Text.trimToNull(request.description()));
		rule.setSeverity(request.severity());
		rule.setPriority(request.priority());
		rule.setEnabled(request.enabled() == null || request.enabled());
		rule.setConditionJson(JsonMapper.shared().writeValueAsString(request.condition()));
		rule.setRecommendedActions(request.recommendedActions().stream().map(String::strip).toList());
	}

	private static RuleResponse toResponse(ClinicalRule rule, FactCatalog catalog) {
		Map<String, Object> condition = readCondition(rule.getConditionJson());
		List<RuleError> errors = List.of();
		try {
			new RuleParser(catalog).parse(condition);
		}
		catch (RuleValidationException ex) {
			errors = ex.getErrors();
		}
		return new RuleResponse(rule.getId(), rule.getRuleCode(), rule.getName(), rule.getDescription(),
				rule.getSeverity(), rule.getPriority(), rule.isEnabled(), condition, rule.getRecommendedActions(),
				rule.getCreatedAt(), rule.getUpdatedAt(), errors.isEmpty(), errors);
	}

}
