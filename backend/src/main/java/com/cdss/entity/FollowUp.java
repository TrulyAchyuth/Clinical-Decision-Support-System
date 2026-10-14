package com.cdss.entity;

import java.time.Instant;

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
 * A scheduled follow-up for one patient. Whether it is upcoming or overdue is
 * calculated when it is read (see FollowUpService), never stored.
 */
@Entity
@Table(name = "follow_ups")
public class FollowUp {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "patient_id", nullable = false)
	private Patient patient;

	@Column(name = "scheduled_at", nullable = false)
	private Instant scheduledAt;

	@Column(nullable = false, length = 200)
	private String reason;

	@Column(length = 500)
	private String notes;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private FollowUpStatus status;

	@Column(name = "reschedule_count", nullable = false)
	private int rescheduleCount;

	@Column(name = "outcome_notes", length = 500)
	private String outcomeNotes;

	@Column(name = "status_changed_at")
	private Instant statusChangedAt;

	@Column(name = "status_changed_by", length = 50)
	private String statusChangedBy;

	@Column(name = "created_by", nullable = false, length = 50)
	private String createdBy;

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

	public Instant getScheduledAt() {
		return scheduledAt;
	}

	public void setScheduledAt(Instant scheduledAt) {
		this.scheduledAt = scheduledAt;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public FollowUpStatus getStatus() {
		return status;
	}

	public void setStatus(FollowUpStatus status) {
		this.status = status;
	}

	public int getRescheduleCount() {
		return rescheduleCount;
	}

	public void setRescheduleCount(int rescheduleCount) {
		this.rescheduleCount = rescheduleCount;
	}

	public String getOutcomeNotes() {
		return outcomeNotes;
	}

	public void setOutcomeNotes(String outcomeNotes) {
		this.outcomeNotes = outcomeNotes;
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

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
