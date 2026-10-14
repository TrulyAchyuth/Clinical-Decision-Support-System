package com.cdss.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "lab_tests")
public class LabTest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(name = "reference_range_low", nullable = false, precision = 10, scale = 2)
	private BigDecimal referenceRangeLow;

	@Column(name = "reference_range_high", nullable = false, precision = 10, scale = 2)
	private BigDecimal referenceRangeHigh;

	@Column(nullable = false, length = 30)
	private String unit;

	@Column(length = 255)
	private String notes;

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public BigDecimal getReferenceRangeLow() {
		return referenceRangeLow;
	}

	public void setReferenceRangeLow(BigDecimal referenceRangeLow) {
		this.referenceRangeLow = referenceRangeLow;
	}

	public BigDecimal getReferenceRangeHigh() {
		return referenceRangeHigh;
	}

	public void setReferenceRangeHigh(BigDecimal referenceRangeHigh) {
		this.referenceRangeHigh = referenceRangeHigh;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

}
