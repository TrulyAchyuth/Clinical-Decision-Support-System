package com.cdss.entity;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

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
import jakarta.persistence.Table;

/**
 * An allergy recorded for one patient. A DRUG allergy links a knowledge-base
 * drug; other categories are free text. Records are resolved, never deleted.
 */
@Entity
@Table(name = "patient_allergies")
public class PatientAllergy {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "patient_id", nullable = false)
	private Patient patient;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "drug_id")
	private Drug drug;

	@Column(nullable = false, length = 150)
	private String allergen;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private AllergyCategory category;

	@Column(length = 255)
	private String reaction;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private AllergySeverity severity;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private AllergyRecordStatus status;

	@Column(name = "recorded_date", nullable = false)
	private LocalDate recordedDate;

	@Column(length = 255)
	private String notes;

	@Column(name = "resolution_reason", length = 255)
	private String resolutionReason;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Column(name = "resolved_by", length = 50)
	private String resolvedBy;

	@Column(name = "recorded_by", nullable = false, length = 50)
	private String recordedBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Long getId() {
		return id;
	}

	public Patient getPatient() {
		return patient;
	}

	public void setPatient(Patient patient) {
		this.patient = patient;
	}

	public Drug getDrug() {
		return drug;
	}

	public void setDrug(Drug drug) {
		this.drug = drug;
	}

	public String getAllergen() {
		return allergen;
	}

	public void setAllergen(String allergen) {
		this.allergen = allergen;
	}

	public AllergyCategory getCategory() {
		return category;
	}

	public void setCategory(AllergyCategory category) {
		this.category = category;
	}

	public String getReaction() {
		return reaction;
	}

	public void setReaction(String reaction) {
		this.reaction = reaction;
	}

	public AllergySeverity getSeverity() {
		return severity;
	}

	public void setSeverity(AllergySeverity severity) {
		this.severity = severity;
	}

	public AllergyRecordStatus getStatus() {
		return status;
	}

	public void setStatus(AllergyRecordStatus status) {
		this.status = status;
	}

	public LocalDate getRecordedDate() {
		return recordedDate;
	}

	public void setRecordedDate(LocalDate recordedDate) {
		this.recordedDate = recordedDate;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public String getResolutionReason() {
		return resolutionReason;
	}

	public void setResolutionReason(String resolutionReason) {
		this.resolutionReason = resolutionReason;
	}

	public Instant getResolvedAt() {
		return resolvedAt;
	}

	public void setResolvedAt(Instant resolvedAt) {
		this.resolvedAt = resolvedAt;
	}

	public String getResolvedBy() {
		return resolvedBy;
	}

	public void setResolvedBy(String resolvedBy) {
		this.resolvedBy = resolvedBy;
	}

	public String getRecordedBy() {
		return recordedBy;
	}

	public void setRecordedBy(String recordedBy) {
		this.recordedBy = recordedBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
