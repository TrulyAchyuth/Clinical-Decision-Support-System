package com.cdss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.cdss.entity.AllergyRecordStatus;
import com.cdss.entity.PatientAllergy;

public interface PatientAllergyRepository extends JpaRepository<PatientAllergy, Long> {

	@EntityGraph(attributePaths = "drug")
	List<PatientAllergy> findByPatientIdOrderByStatusAscRecordedDateDescIdDesc(Long patientId);

	@EntityGraph(attributePaths = "drug")
	List<PatientAllergy> findByPatientIdAndStatus(Long patientId, AllergyRecordStatus status);

	@EntityGraph(attributePaths = "drug")
	Optional<PatientAllergy> findByIdAndPatientId(Long id, Long patientId);

	boolean existsByPatientIdAndStatus(Long patientId, AllergyRecordStatus status);

	boolean existsByDrugId(Long drugId);

}
