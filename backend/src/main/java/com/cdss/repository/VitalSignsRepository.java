package com.cdss.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cdss.entity.VitalSigns;

public interface VitalSignsRepository extends JpaRepository<VitalSigns, Long> {

	List<VitalSigns> findByPatientIdOrderByRecordedAtDescIdDesc(Long patientId);

	/** Recordings taken at or after {@code since}, newest first. */
	List<VitalSigns> findByPatientIdAndRecordedAtGreaterThanEqualOrderByRecordedAtDescIdDesc(Long patientId,
			Instant since);

	Optional<VitalSigns> findByIdAndPatientId(Long id, Long patientId);

}
