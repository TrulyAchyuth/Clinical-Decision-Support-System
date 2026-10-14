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
import com.cdss.dto.RuleValidationResponse;
import com.cdss.entity.ClinicalRule;
import com.cdss.entity.LabTest;
import com.cdss.exception.DuplicateResourceException;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.repository.ClinicalRuleRepository;
import com.cdss.repository.LabTestRepository;
import com.cdss.ruleengine.FactCatalog;
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
		return ruleRepository.findAllByOrderByPriorityAscIdAsc().stream().map(ClinicalRuleService::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public RuleResponse findById(Long id) {
		return toResponse(getRule(id));
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
		return toResponse(ruleRepository.save(rule));
	}

	public RuleResponse update(Long id, RuleRequest request) {
		ClinicalRule rule = getRule(id);
		String ruleCode = normalizeCode(request.ruleId());
		if (ruleRepository.existsByRuleCodeIgnoreCaseAndIdNot(ruleCode, id)) {
			throw duplicate(ruleCode);
		}
		new RuleParser(factCatalog()).parse(request.condition());
		copy(request, rule);
		return toResponse(ruleRepository.saveAndFlush(rule));
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

	private static RuleResponse toResponse(ClinicalRule rule) {
		return new RuleResponse(rule.getId(), rule.getRuleCode(), rule.getName(), rule.getDescription(),
				rule.getSeverity(), rule.getPriority(), rule.isEnabled(), readCondition(rule.getConditionJson()),
				rule.getRecommendedActions(), rule.getCreatedAt(), rule.getUpdatedAt());
	}

}
