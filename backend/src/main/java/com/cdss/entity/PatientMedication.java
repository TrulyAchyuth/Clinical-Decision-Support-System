package com.cdss.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A medication course for one patient, using a knowledge-base drug.
 * Records are never deleted by the application: they are discontinued or
 * completed, so the history is kept. {@code safetyWarnings} are the
 * safety-check warnings the clinician acknowledged when adding it.
 */
@Entity
@Table(name = "patient_medications")
public class PatientMedication {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "patient_id", nullable = false)
	private Patient patient;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "drug_id", nullable = false)
	private Drug drug;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal dose;

	@Column(name = "dose_unit", nullable = false, length = 20)
	private String doseUnit;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private MedicationRoute route;

	@Column(nullable = false, length = 100)
	private String frequency;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 15)
	private MedicationStatus status;

	@Column(length = 255)
	private String notes;

	@Column(name = "status_reason", length = 255)
	private String statusReason;

	@Column(name = "status_changed_at")
	private Instant statusChangedAt;

	@Column(name = "status_changed_by", length = 50)
	private String statusChangedBy;

	@Convert(converter = StringListJsonConverter.class)
	@Column(name = "safety_warnings", nullable = false, columnDefinition = "json")
	private List<String> safetyWarnings = new ArrayList<>();

	@Column(name = "safety_override_reason", length = 500)
	private String safetyOverrideReason;

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

	public BigDecimal getDose() {
		return dose;
	}

	public void setDose(BigDecimal dose) {
		this.dose = dose;
	}

	public String getDoseUnit() {
		return doseUnit;
	}

	public void setDoseUnit(String doseUnit) {
		this.doseUnit = doseUnit;
	}

	public MedicationRoute getRoute() {
		return route;
	}

	public void setRoute(MedicationRoute route) {
		this.route = route;
	}

	public String getFrequency() {
		return frequency;
	}

	public void setFrequency(String frequency) {
		this.frequency = frequency;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public void setEndDate(LocalDate endDate) {
		this.endDate = endDate;
	}

	public MedicationStatus getStatus() {
		return status;
	}

	public void setStatus(MedicationStatus status) {
		this.status = status;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public String getStatusReason() {
		return statusReason;
	}

	public void setStatusReason(String statusReason) {
		this.statusReason = statusReason;
	}

	public Instant getStatusChangedAt() {
		return statusChangedAt;
	}

	public void setStatusChangedAt(Instant statusChangedAt) {
		this.statusChangedAt = statusChangedAt;
	}

	public String getStatusChangedBy() {
		return statusChangedBy;
	}

	public void setStatusChangedBy(String statusChangedBy) {
		this.statusChangedBy = statusChangedBy;
	}

	public List<String> getSafetyWarnings() {
		return safetyWarnings;
	}

	public void setSafetyWarnings(List<String> safetyWarnings) {
		this.safetyWarnings = safetyWarnings;
	}

	public String getSafetyOverrideReason() {
		return safetyOverrideReason;
	}

	public void setSafetyOverrideReason(String safetyOverrideReason) {
		this.safetyOverrideReason = safetyOverrideReason;
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
