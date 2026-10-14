package com.cdss.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cdss.entity.DrugInteraction;

/** The interaction dataset is small, so it is read whole with {@code findAll()}. */
public interface DrugInteractionRepository extends JpaRepository<DrugInteraction, Long> {

}
