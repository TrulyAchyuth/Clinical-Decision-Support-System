package com.cdss.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.cdss.entity.LabResult;

public interface LabResultRepository extends JpaRepository<LabResult, Long> {

	@EntityGraph(attributePaths = "labTest")
	List<LabResult> findByPatientIdOrderByMeasuredAtDescIdDesc(Long patientId);

	/** Results measured at or after {@code since}, newest first. */
	@EntityGraph(attributePaths = "labTest")
	List<LabResult> findByPatientIdAndMeasuredAtGreaterThanEqualOrderByMeasuredAtDescIdDesc(Long patientId,
			Instant since);

	Optional<LabResult> findByIdAndPatientId(Long id, Long patientId);

	boolean existsByLabTestId(Long labTestId);

}
