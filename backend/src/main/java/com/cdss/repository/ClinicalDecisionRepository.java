package com.cdss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.cdss.entity.ClinicalDecision;

public interface ClinicalDecisionRepository extends JpaRepository<ClinicalDecision, Long> {

	@EntityGraph(attributePaths = "ruleResults")
	List<ClinicalDecision> findByPatientIdOrderByRequestedAtDescIdDesc(Long patientId);

	@EntityGraph(attributePaths = "ruleResults")
	Optional<ClinicalDecision> findByIdAndPatientId(Long id, Long patientId);

}
