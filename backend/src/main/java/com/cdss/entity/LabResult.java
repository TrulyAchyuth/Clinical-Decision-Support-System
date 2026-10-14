package com.cdss.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A lab result for a patient, for one of the knowledge-base lab tests. */
@Entity
@Table(name = "lab_results")
public class LabResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "patient_id", nullable = false)
	private Patient patient;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lab_test_id", nullable = false)
	private LabTest labTest;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal value;

	@Column(nullable = false, length = 30)
	private String unit;

	@Column(name = "measured_at", nullable = false)
	private Instant measuredAt;

	@Column(length = 255)
	private String notes;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public Long getId() {
		return id;
	}

	public Patient getPatient() {
		return patient;
	}

	public void setPatient(Patient patient) {
		this.patient = patient;
	}

	public LabTest getLabTest() {
		return labTest;
	}

	public void setLabTest(LabTest labTest) {
		this.labTest = labTest;
	}

	public BigDecimal getValue() {
		return value;
	}

	public void setValue(BigDecimal value) {
		this.value = value;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public Instant getMeasuredAt() {
		return measuredAt;
	}

	public void setMeasuredAt(Instant measuredAt) {
		this.measuredAt = measuredAt;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
