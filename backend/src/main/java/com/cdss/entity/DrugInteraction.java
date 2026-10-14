package com.cdss.entity;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An interaction between two drugs, identified by drug name so it applies to
 * every strength. In this prototype all rows are illustrative sample data
 * (see V9__drug_interactions.sql); read-only in the application. The source
 * details added in V12 are optional and null for the sample rows.
 */
@Entity
@Table(name = "drug_interactions")
public class DrugInteraction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "drug_name_a", nullable = false, length = 150)
	private String drugNameA;

	@Column(name = "drug_name_b", nullable = false, length = 150)
	private String drugNameB;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private InteractionSeverity severity;

	@Column(nullable = false, length = 1000)
	private String description;

	@Column(nullable = false, length = 255)
	private String source;

	@Column(name = "data_classification", nullable = false, length = 30)
	private String dataClassification;

	@Column(name = "review_status", nullable = false, length = 30)
	private String reviewStatus;

	@Column(length = 1000)
	private String mechanism;

	@Column(name = "recommended_action", length = 1000)
	private String recommendedAction;

	@Column(name = "source_version", length = 100)
	private String sourceVersion;

	@Column(name = "last_reviewed_on")
	private LocalDate lastReviewedOn;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public Long getId() {
		return id;
	}

	public String getDrugNameA() {
		return drugNameA;
	}

	public void setDrugNameA(String drugNameA) {
		this.drugNameA = drugNameA;
	}

	public String getDrugNameB() {
		return drugNameB;
	}

	public void setDrugNameB(String drugNameB) {
		this.drugNameB = drugNameB;
	}

	public InteractionSeverity getSeverity() {
		return severity;
	}

	public void setSeverity(InteractionSeverity severity) {
		this.severity = severity;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getDataClassification() {
		return dataClassification;
	}

	public void setDataClassification(String dataClassification) {
		this.dataClassification = dataClassification;
	}

	public String getReviewStatus() {
		return reviewStatus;
	}

	public void setReviewStatus(String reviewStatus) {
		this.reviewStatus = reviewStatus;
	}

	public String getMechanism() {
		return mechanism;
	}

	public void setMechanism(String mechanism) {
		this.mechanism = mechanism;
	}

	public String getRecommendedAction() {
		return recommendedAction;
	}

	public void setRecommendedAction(String recommendedAction) {
		this.recommendedAction = recommendedAction;
	}

	public String getSourceVersion() {
		return sourceVersion;
	}

	public void setSourceVersion(String sourceVersion) {
		this.sourceVersion = sourceVersion;
	}

	public LocalDate getLastReviewedOn() {
		return lastReviewedOn;
	}

	public void setLastReviewedOn(LocalDate lastReviewedOn) {
		this.lastReviewedOn = lastReviewedOn;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}
