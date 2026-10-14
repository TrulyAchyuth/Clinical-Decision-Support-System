package com.cdss.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cdss.entity.ClinicalRule;

public interface ClinicalRuleRepository extends JpaRepository<ClinicalRule, Long> {

	List<ClinicalRule> findAllByOrderByPriorityAscIdAsc();

	List<ClinicalRule> findByEnabledTrueOrderByPriorityAscIdAsc();

	boolean existsByRuleCodeIgnoreCase(String ruleCode);

	boolean existsByRuleCodeIgnoreCaseAndIdNot(String ruleCode, Long id);

}
