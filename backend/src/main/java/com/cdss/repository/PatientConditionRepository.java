package com.cdss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.cdss.entity.ConditionStatus;
import com.cdss.entity.PatientCondition;

public interface PatientConditionRepository extends JpaRepository<PatientCondition, Long> {

	@EntityGraph(attributePaths = "disease")
	List<PatientCondition> findByPatientIdOrderByIdAsc(Long patientId);

	@EntityGraph(attributePaths = "disease")
	List<PatientCondition> findByPatientIdAndStatus(Long patientId, ConditionStatus status);

	Optional<PatientCondition> findByIdAndPatientId(Long id, Long patientId);

	boolean existsByPatientIdAndDiseaseId(Long patientId, Long diseaseId);

	boolean existsByDiseaseId(Long diseaseId);

}
