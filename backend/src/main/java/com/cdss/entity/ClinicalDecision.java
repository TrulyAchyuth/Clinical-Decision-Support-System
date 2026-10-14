package com.cdss.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** A recorded decision request for a patient and the rule results it produced. */
@Entity
@Table(name = "clinical_decisions")
public class ClinicalDecision {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "patient_id", nullable = false)
	private Patient patient;

	@Column(name = "requested_at", nullable = false)
	private Instant requestedAt;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private DecisionOutcome outcome;

	@Column(name = "rules_evaluated", nullable = false)
	private int rulesEvaluated;

	@Column(nullable = false, length = 500)
	private String summary;

	@OneToMany(mappedBy = "decision", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("priority ASC, id ASC")
	private List<DecisionRuleResult> ruleResults = new ArrayList<>();

	public void addRuleResult(DecisionRuleResult result) {
		result.setDecision(this);
		ruleResults.add(result);
	}

	public Long getId() {
		return id;
	}

	public Patient getPatient() {
		return patient;
	}

	public void setPatient(Patient patient) {
		this.patient = patient;
	}

	public Instant getRequestedAt() {
		return requestedAt;
	}

	public void setRequestedAt(Instant requestedAt) {
		this.requestedAt = requestedAt;
	}

	public DecisionOutcome getOutcome() {
		return outcome;
	}

	public void setOutcome(DecisionOutcome outcome) {
		this.outcome = outcome;
	}

	public int getRulesEvaluated() {
		return rulesEvaluated;
	}

	public void setRulesEvaluated(int rulesEvaluated) {
		this.rulesEvaluated = rulesEvaluated;
	}

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public List<DecisionRuleResult> getRuleResults() {
		return ruleResults;
	}

}
