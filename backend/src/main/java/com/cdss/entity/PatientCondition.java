package com.cdss.entity;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
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

/** Patient history: a knowledge-base disease recorded for a patient. */
@Entity
@Table(name = "patient_conditions")
public class PatientCondition {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "patient_id", nullable = false)
	private Patient patient;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "disease_id", nullable = false)
	private Disease disease;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private ConditionStatus status;

	@Column(name = "onset_date")
	private LocalDate onsetDate;

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

	public Disease getDisease() {
		return disease;
	}

	public void setDisease(Disease disease) {
		this.disease = disease;
	}

	public ConditionStatus getStatus() {
		return status;
	}

	public void setStatus(ConditionStatus status) {
		this.status = status;
	}

	public LocalDate getOnsetDate() {
		return onsetDate;
	}

	public void setOnsetDate(LocalDate onsetDate) {
		this.onsetDate = onsetDate;
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
