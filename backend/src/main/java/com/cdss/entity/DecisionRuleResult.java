package com.cdss.entity;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One rule's result within a decision. The rule's code, name, severity,
 * priority and actions are copied so the history stays readable if the rule
 * changes later; {@code ruleId} becomes null if the rule is deleted.
 */
@Entity
@Table(name = "decision_rule_results")
public class DecisionRuleResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "decision_id", nullable = false)
	private ClinicalDecision decision;

	@Column(name = "rule_id")
	private Long ruleId;

	@Column(name = "rule_code", nullable = false, length = 50)
	private String ruleCode;

	@Column(name = "rule_name", nullable = false, length = 150)
	private String ruleName;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private Severity severity;

	@Column(nullable = false)
	private int priority;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private RuleResultType result;

	@Convert(converter = StringListJsonConverter.class)
	@Column(nullable = false, columnDefinition = "json")
	private List<String> reasons = new ArrayList<>();

	@Convert(converter = StringListJsonConverter.class)
	@Column(name = "recommended_actions", nullable = false, columnDefinition = "json")
	private List<String> recommendedActions = new ArrayList<>();

	public Long getId() {
		return id;
	}

	public ClinicalDecision getDecision() {
		return decision;
	}

	void setDecision(ClinicalDecision decision) {
		this.decision = decision;
	}

	public Long getRuleId() {
		return ruleId;
	}

	public void setRuleId(Long ruleId) {
		this.ruleId = ruleId;
	}

	public String getRuleCode() {
		return ruleCode;
	}

	public void setRuleCode(String ruleCode) {
		this.ruleCode = ruleCode;
	}

	public String getRuleName() {
		return ruleName;
	}

	public void setRuleName(String ruleName) {
		this.ruleName = ruleName;
	}

	public Severity getSeverity() {
		return severity;
	}

	public void setSeverity(Severity severity) {
		this.severity = severity;
	}

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	public RuleResultType getResult() {
		return result;
	}

	public void setResult(RuleResultType result) {
		this.result = result;
	}

	public List<String> getReasons() {
		return reasons;
	}

	public void setReasons(List<String> reasons) {
		this.reasons = reasons;
	}

	public List<String> getRecommendedActions() {
		return recommendedActions;
	}

	public void setRecommendedActions(List<String> recommendedActions) {
		this.recommendedActions = recommendedActions;
	}

}
