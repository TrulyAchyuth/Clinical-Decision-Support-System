package com.cdss.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cdss.entity.Patient;

public interface PatientRepository extends JpaRepository<Patient, Long> {

	List<Patient> findAllByOrderByFullNameAsc();

	List<Patient> findByFullNameContainingIgnoreCaseOrderByFullNameAsc(String text);

}
