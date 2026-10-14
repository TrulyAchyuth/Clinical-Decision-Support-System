package com.cdss.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cdss.entity.Disease;

public interface DiseaseRepository extends JpaRepository<Disease, Long> {

	List<Disease> findAllByOrderByNameAsc();

	boolean existsByIcd10CodeIgnoreCase(String icd10Code);

	boolean existsByIcd10CodeIgnoreCaseAndIdNot(String icd10Code, Long id);

}
