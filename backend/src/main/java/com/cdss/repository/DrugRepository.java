package com.cdss.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cdss.entity.Drug;

public interface DrugRepository extends JpaRepository<Drug, Long> {

	List<Drug> findAllByOrderByNameAscDosageAsc();

	boolean existsByNameIgnoreCaseAndDosageIgnoreCase(String name, String dosage);

	boolean existsByNameIgnoreCaseAndDosageIgnoreCaseAndIdNot(String name, String dosage, Long id);

	boolean existsByCategoryIgnoreCase(String category);

}
