package com.cdss.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cdss.entity.LabTest;

public interface LabTestRepository extends JpaRepository<LabTest, Long> {

	List<LabTest> findAllByOrderByNameAsc();

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

}
