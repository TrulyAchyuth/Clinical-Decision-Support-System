package com.cdss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.cdss.entity.MedicationStatus;
import com.cdss.entity.PatientMedication;

public interface PatientMedicationRepository extends JpaRepository<PatientMedication, Long> {

	@EntityGraph(attributePaths = "drug")
	List<PatientMedication> findByPatientIdOrderByStartDateDescIdDesc(Long patientId);

	@EntityGraph(attributePaths = "drug")
	List<PatientMedication> findByPatientIdAndStatus(Long patientId, MedicationStatus status);

	@EntityGraph(attributePaths = "drug")
	Optional<PatientMedication> findByIdAndPatientId(Long id, Long patientId);

	boolean existsByPatientIdAndDrugIdAndStatus(Long patientId, Long drugId, MedicationStatus status);

	boolean existsByDrugId(Long drugId);

}
