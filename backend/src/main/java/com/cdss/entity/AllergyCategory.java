package com.cdss.entity;

/**
 * What kind of allergen. Only DRUG (linked to a knowledge-base drug) and DRUG_CATEGORY
 * (a knowledge-base drug category) are matched to medications automatically.
 */
public enum AllergyCategory {
	DRUG,
	DRUG_CATEGORY,
	FOOD,
	ENVIRONMENTAL,
	OTHER
}
