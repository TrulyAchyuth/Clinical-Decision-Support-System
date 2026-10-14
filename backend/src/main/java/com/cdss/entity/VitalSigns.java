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

/**
 * One set of vital signs taken at the same time. Any value may be missing.
 * Units: blood pressure mmHg, temperature degrees Celsius, heart rate
 * beats/min, respiratory rate breaths/min, oxygen saturation %.
 */
@Entity
@Table(name = "vital_signs")
public class VitalSigns {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "patient_id", nullable = false)
	private Patient patient;

	@Column(name = "recorded_at", nullable = false)
	private Instant recordedAt;

	@Column(name = "systolic_bp")
	private Integer systolicBp;

	@Column(name = "diastolic_bp")
	private Integer diastolicBp;

	@Column(precision = 4, scale = 1)
	private BigDecimal temperature;

	@Column(name = "heart_rate")
	private Integer heartRate;

	@Column(name = "respiratory_rate")
	private Integer respiratoryRate;

	@Column(name = "oxygen_saturation")
	private Integer oxygenSaturation;

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

	public Instant getRecordedAt() {
		return recordedAt;
	}

	public void setRecordedAt(Instant recordedAt) {
		this.recordedAt = recordedAt;
	}

	public Integer getSystolicBp() {
		return systolicBp;
	}

	public void setSystolicBp(Integer systolicBp) {
		this.systolicBp = systolicBp;
	}

	public Integer getDiastolicBp() {
		return diastolicBp;
	}

	public void setDiastolicBp(Integer diastolicBp) {
		this.diastolicBp = diastolicBp;
	}

	public BigDecimal getTemperature() {
		return temperature;
	}

	public void setTemperature(BigDecimal temperature) {
		this.temperature = temperature;
	}

	public Integer getHeartRate() {
		return heartRate;
	}

	public void setHeartRate(Integer heartRate) {
		this.heartRate = heartRate;
	}

	public Integer getRespiratoryRate() {
		return respiratoryRate;
	}

	public void setRespiratoryRate(Integer respiratoryRate) {
		this.respiratoryRate = respiratoryRate;
	}

	public Integer getOxygenSaturation() {
		return oxygenSaturation;
	}

	public void setOxygenSaturation(Integer oxygenSaturation) {
		this.oxygenSaturation = oxygenSaturation;
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
