package com.cdss.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A clinical rule. The condition is stored as JSON text and is parsed and
 * validated by {@code com.cdss.ruleengine.RuleParser} before use.
 */
@Entity
@Table(name = "clinical_rules")
public class ClinicalRule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** The rule's public identifier, e.g. "BP-HIGH" (shown as "ruleId" in the API). */
	@Column(name = "rule_code", nullable = false, length = 50)
	private String ruleCode;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(length = 1000)
	private String description;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private Severity severity;

	@Column(nullable = false)
	private int priority;

	@Column(nullable = false)
	private boolean enabled;

	@Column(name = "condition_json", nullable = false, columnDefinition = "json")
	private String conditionJson;

	@Convert(converter = StringListJsonConverter.class)
	@Column(name = "recommended_actions", nullable = false, columnDefinition = "json")
	private List<String> recommendedActions = new ArrayList<>();

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Long getId() {
		return id;
	}

	public String getRuleCode() {
		return ruleCode;
	}

	public void setRuleCode(String ruleCode) {
		this.ruleCode = ruleCode;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
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

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getConditionJson() {
		return conditionJson;
	}

	public void setConditionJson(String conditionJson) {
		this.conditionJson = conditionJson;
	}

	public List<String> getRecommendedActions() {
		return recommendedActions;
	}

	public void setRecommendedActions(List<String> recommendedActions) {
		this.recommendedActions = recommendedActions;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
