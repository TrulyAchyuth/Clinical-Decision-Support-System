package com.cdss.dto;

import java.time.LocalDate;

import com.cdss.entity.DrugInteraction;
import com.cdss.entity.InteractionSeverity;

/** One row of the drug-interaction dataset; the source details are null when the row has none. */
public record DrugInteractionResponse(Long id, String drugNameA, String drugNameB, InteractionSeverity severity,
		String description, String mechanism, String recommendedAction, String source, String sourceVersion,
		LocalDate lastReviewedOn, String dataClassification, String reviewStatus) {

	public static DrugInteractionResponse from(DrugInteraction interaction) {
		return new DrugInteractionResponse(interaction.getId(), interaction.getDrugNameA(),
				interaction.getDrugNameB(), interaction.getSeverity(), interaction.getDescription(),
				interaction.getMechanism(), interaction.getRecommendedAction(), interaction.getSource(),
				interaction.getSourceVersion(), interaction.getLastReviewedOn(), interaction.getDataClassification(),
				interaction.getReviewStatus());
	}

}
