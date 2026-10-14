package com.cdss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.cdss.entity.FollowUp;

public interface FollowUpRepository extends JpaRepository<FollowUp, Long> {

	@EntityGraph(attributePaths = "patient")
	List<FollowUp> findAllByOrderByScheduledAtAscIdAsc();

	@EntityGraph(attributePaths = "patient")
	List<FollowUp> findByPatientIdOrderByScheduledAtDescIdDesc(Long patientId);

	@EntityGraph(attributePaths = "patient")
	Optional<FollowUp> findByIdAndPatientId(Long id, Long patientId);

}
