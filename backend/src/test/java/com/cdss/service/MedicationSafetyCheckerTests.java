package com.cdss.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.cdss.dto.SafetyFinding;
import com.cdss.entity.AllergyCategory;
import com.cdss.entity.AllergyRecordStatus;
import com.cdss.entity.AllergySeverity;
import com.cdss.entity.Drug;
import com.cdss.entity.DrugInteraction;
import com.cdss.entity.InteractionSeverity;
import com.cdss.entity.MedicationStatus;
import com.cdss.entity.PatientAllergy;
import com.cdss.entity.PatientMedication;

class MedicationSafetyCheckerTests {

	private static final Drug AMOXICILLIN_500 = drug(1L, "Amoxicillin", "500 mg", "Antibiotic (penicillin)");

	private static final Drug AMOXICILLIN_250 = drug(6L, "Amoxicillin", "250 mg", "Antibiotic (penicillin)");

	private static final Drug WARFARIN = drug(4L, "Warfarin", "5 mg", "Anticoagulant");

	private static final Drug IBUPROFEN = drug(5L, "Ibuprofen", "400 mg", "NSAID");

	private static final Drug METFORMIN = drug(3L, "Metformin", "500 mg", "Antidiabetic (biguanide)");

	private static final DrugInteraction WARFARIN_IBUPROFEN = interaction(10L, "Ibuprofen", "Warfarin");

	@Test
	void drugAllergyMatchesEveryStrengthOfTheSameDrugName() {
		List<SafetyFinding> findings = MedicationSafetyChecker.evaluate(AMOXICILLIN_250, List.of(),
				List.of(drugAllergy(21L, AMOXICILLIN_500, AllergySeverity.SEVERE)), false, List.of());

		SafetyFinding allergy = only(findings, "ALLERGY");
		assertThat(allergy.code()).matches("ALLERGY-21-[0-9a-f]{8}");
		assertThat(allergy.priority()).isEqualTo("HIGH");
		assertThat(allergy.allergens()).containsExactly("Amoxicillin");
		assertThat(allergy.medications()).containsExactly("Amoxicillin 250 mg");
		assertThat(allergy.recommendedAction()).isNotBlank();
		assertThat(allergy.resultStatus()).isEqualTo("MATCH_FOUND");
		assertThat(allergy.severity()).isEqualTo("SEVERE");
		assertThat(allergy.requiresReview()).isTrue();
		assertThat(allergy.dataClassification()).isEqualTo("PATIENT_RECORD");
		assertThat(MedicationSafetyChecker.overallStatus(findings)).isEqualTo("WARNINGS");
	}

	@Test
	void drugCategoryAllergyMatchesTheExactKnowledgeBaseCategory() {
		PatientAllergy penicillins = allergy(22L, AllergyCategory.DRUG_CATEGORY, "antibiotic (PENICILLIN)", null);

		assertThat(only(MedicationSafetyChecker.evaluate(AMOXICILLIN_500, List.of(), List.of(penicillins), false,
				List.of()), "ALLERGY").resultStatus()).isEqualTo("MATCH_FOUND");
		assertThat(only(MedicationSafetyChecker.evaluate(METFORMIN, List.of(), List.of(penicillins), false,
				List.of()), "ALLERGY").resultStatus()).isEqualTo("NO_MATCH_FOUND");
	}

	@Test
	void similarButDifferentDrugNamesAreNotTreatedAsTheSameDrug() {
		Drug coAmoxiclav = drug(7L, "Amoxicillin with clavulanic acid", "625 mg", "Antibiotic (combination)");

		List<SafetyFinding> findings = MedicationSafetyChecker.evaluate(coAmoxiclav, List.of(),
				List.of(drugAllergy(21L, AMOXICILLIN_500, AllergySeverity.SEVERE)), false, List.of());

		assertThat(only(findings, "ALLERGY").resultStatus()).isEqualTo("NO_MATCH_FOUND");
	}

	@Test
	void freeTextAllergyThatCannotBeMatchedIsReportedAsUncertain() {
		List<SafetyFinding> findings = MedicationSafetyChecker.evaluate(IBUPROFEN, List.of(),
				List.of(allergy(23L, AllergyCategory.OTHER, "Some imported tablet", null)), false, List.of());

		SafetyFinding uncertain = only(findings, "ALLERGY");
		assertThat(uncertain.resultStatus()).isEqualTo("UNCERTAIN");
		assertThat(uncertain.requiresReview()).isTrue();
		assertThat(MedicationSafetyChecker.overallStatus(findings)).isEqualTo("UNCERTAIN");
	}

	@Test
	void allergyHistoryNotAssessedIsUncertainNotSafe() {
		List<SafetyFinding> findings = MedicationSafetyChecker.evaluate(IBUPROFEN, List.of(), List.of(), false,
				List.of(WARFARIN_IBUPROFEN));

		SafetyFinding status = only(findings, "DATA_COMPLETENESS");
		assertThat(status.code()).startsWith("ALLERGY-STATUS-NOT-ASSESSED-");
		assertThat(status.priority()).isEqualTo("MEDIUM");
		assertThat(status.resultStatus()).isEqualTo("UNCERTAIN");
		assertThat(status.requiresReview()).isTrue();
		assertThat(findings).noneMatch((finding) -> finding.checkType().equals("ALLERGY")
				&& finding.resultStatus().equals("NO_MATCH_FOUND"));
	}

	@Test
	void resolvedAllergiesAreNotPassedInAndNoKnownAllergiesGivesALimitedNoMatch() {
		List<SafetyFinding> findings = MedicationSafetyChecker.evaluate(IBUPROFEN, List.of(), List.of(), true,
				List.of(WARFARIN_IBUPROFEN));

		SafetyFinding allergy = only(findings, "ALLERGY");
		assertThat(allergy.resultStatus()).isEqualTo("NO_MATCH_FOUND");
		assertThat(allergy.requiresReview()).isFalse();
		assertThat(allergy.explanation()).contains("only covers allergies that have been recorded");
	}

	@Test
	void interactionInTheDatasetIsFoundInEitherOrderAndLabelledAsSampleData() {
		PatientMedication onWarfarin = medication(30L, WARFARIN);

		SafetyFinding interaction = only(MedicationSafetyChecker.evaluate(IBUPROFEN, List.of(onWarfarin), List.of(),
				true, List.of(WARFARIN_IBUPROFEN)), "INTERACTION");
		assertThat(interaction.code()).matches("INTERACTION-10-30-[0-9a-f]{8}");
		assertThat(interaction.resultStatus()).isEqualTo("MATCH_FOUND");
		assertThat(interaction.severity()).isEqualTo("MAJOR");
		assertThat(interaction.dataClassification()).isEqualTo("ILLUSTRATIVE_SAMPLE");
		assertThat(interaction.explanation()).contains("Illustrative sample data");
		assertThat(interaction.medications()).containsExactly("Ibuprofen 400 mg", "Warfarin 5 mg");
		assertThat(interaction.reviewStatus()).isEqualTo("NOT_CLINICALLY_REVIEWED");
		assertThat(interaction.mechanism()).isNull();
		assertThat(interaction.recommendedAction()).isNull();
		assertThat(interaction.sourceVersion()).isNull();
		assertThat(interaction.sourceReviewedOn()).isNull();

		List<SafetyFinding> reversed = MedicationSafetyChecker.evaluate(WARFARIN, List.of(medication(31L, IBUPROFEN)),
				List.of(), true, List.of(WARFARIN_IBUPROFEN));
		assertThat(only(reversed, "INTERACTION").resultStatus()).isEqualTo("MATCH_FOUND");
	}

	@Test
	void noInteractionFoundIsLimitedDataNeverSafe() {
		List<SafetyFinding> findings = MedicationSafetyChecker.evaluate(METFORMIN,
				List.of(medication(30L, WARFARIN)), List.of(), true, List.of(WARFARIN_IBUPROFEN));

		SafetyFinding interaction = only(findings, "INTERACTION");
		assertThat(interaction.resultStatus()).isEqualTo("NO_MATCH_FOUND");
		assertThat(interaction.requiresReview()).isFalse();
		assertThat(interaction.explanation()).contains("does not mean the combination is safe");
		assertThat(MedicationSafetyChecker.overallStatus(findings)).isEqualTo("NO_ISSUES_FOUND_IN_AVAILABLE_DATA");
		assertThat(findings).extracting(SafetyFinding::resultStatus).doesNotContain("SAFE");
	}

	@Test
	void noOtherActiveMedicationsSaysTheListMayBeIncomplete() {
		SafetyFinding interaction = only(MedicationSafetyChecker.evaluate(METFORMIN, List.of(), List.of(), true,
				List.of(WARFARIN_IBUPROFEN)), "INTERACTION");

		assertThat(interaction.explanation()).contains("may be incomplete");
	}

	@Test
	void sameDrugNameAlreadyActiveIsReportedAsDuplicateTherapy() {
		SafetyFinding duplicate = only(MedicationSafetyChecker.evaluate(AMOXICILLIN_250,
				List.of(medication(32L, AMOXICILLIN_500)), List.of(), true, List.of()), "DUPLICATE_THERAPY");

		assertThat(duplicate.resultStatus()).isEqualTo("MATCH_FOUND");
		assertThat(duplicate.requiresReview()).isTrue();
	}

	@Test
	void sourceDetailsAreCopiedFromTheDatasetRowAndNeverInvented() {
		DrugInteraction verified = interaction(11L, "Ibuprofen", "Warfarin");
		verified.setDataClassification("VERIFIED");
		verified.setReviewStatus("CLINICALLY_REVIEWED");
		verified.setMechanism("Test mechanism text");
		verified.setRecommendedAction("Test recommended action");
		verified.setSourceVersion("test-v2");
		verified.setLastReviewedOn(LocalDate.of(2026, 1, 15));

		SafetyFinding interaction = only(MedicationSafetyChecker.evaluate(IBUPROFEN,
				List.of(medication(30L, WARFARIN)), List.of(), true, List.of(verified)), "INTERACTION");

		assertThat(interaction.dataClassification()).isEqualTo("VERIFIED");
		assertThat(interaction.mechanism()).isEqualTo("Test mechanism text");
		assertThat(interaction.recommendedAction()).isEqualTo("Test recommended action");
		assertThat(interaction.sourceVersion()).isEqualTo("test-v2");
		assertThat(interaction.sourceReviewedOn()).isEqualTo(LocalDate.of(2026, 1, 15));
		assertThat(interaction.explanation()).doesNotContain("Illustrative sample data");
	}

	@Test
	void unchangedFindingKeepsItsCodeButAMaterialChangeGetsANewOne() {
		PatientAllergy allergy = drugAllergy(21L, AMOXICILLIN_500, AllergySeverity.MODERATE);
		String first = only(MedicationSafetyChecker.evaluate(AMOXICILLIN_500, List.of(), List.of(allergy), false,
				List.of()), "ALLERGY").code();
		String again = only(MedicationSafetyChecker.evaluate(AMOXICILLIN_500, List.of(), List.of(allergy), false,
				List.of()), "ALLERGY").code();
		allergy.setSeverity(AllergySeverity.SEVERE);
		String changed = only(MedicationSafetyChecker.evaluate(AMOXICILLIN_500, List.of(), List.of(allergy), false,
				List.of()), "ALLERGY").code();

		assertThat(again).isEqualTo(first);
		assertThat(changed).isNotEqualTo(first).startsWith("ALLERGY-21-");
	}

	@Test
	void missingInteractionDatasetIsReportedAsUnavailableNotAsNoMatch() {
		List<SafetyFinding> findings = MedicationSafetyChecker.evaluate(IBUPROFEN,
				List.of(medication(30L, WARFARIN)), List.of(), true, List.of());

		SafetyFinding interaction = only(findings, "INTERACTION");
		assertThat(interaction.resultStatus()).isEqualTo("UNCERTAIN");
		assertThat(interaction.title()).isEqualTo("Interaction check unavailable");
		assertThat(interaction.requiresReview()).isTrue();
		assertThat(MedicationSafetyChecker.overallStatus(findings)).isEqualTo("UNCERTAIN");
	}

	@Test
	void drugMissingFromTheDatasetIsCalledOutAsNotCovered() {
		SafetyFinding interaction = only(MedicationSafetyChecker.evaluate(METFORMIN,
				List.of(medication(30L, WARFARIN)), List.of(), true, List.of(WARFARIN_IBUPROFEN)), "INTERACTION");

		assertThat(interaction.explanation()).contains("Metformin does not appear in the dataset at all");
	}

	@Test
	void matchesAreListedBeforeUncertainAndNoMatchFindings() {
		List<SafetyFinding> findings = MedicationSafetyChecker.evaluate(IBUPROFEN, List.of(medication(30L, WARFARIN)),
				List.of(allergy(23L, AllergyCategory.OTHER, "Some imported tablet", null)), false,
				List.of(WARFARIN_IBUPROFEN));

		assertThat(findings).extracting(SafetyFinding::priority).containsExactly("HIGH", "MEDIUM");
	}

	private static SafetyFinding only(List<SafetyFinding> findings, String checkType) {
		List<SafetyFinding> matching = findings.stream().filter((finding) -> finding.checkType().equals(checkType)).toList();
		assertThat(matching).hasSize(1);
		return matching.get(0);
	}

	static Drug drug(Long id, String name, String dosage, String category) {
		Drug drug = new Drug();
		ReflectionTestUtils.setField(drug, "id", id);
		drug.setName(name);
		drug.setDosage(dosage);
		drug.setCategory(category);
		return drug;
	}

	static PatientAllergy drugAllergy(Long id, Drug drug, AllergySeverity severity) {
		PatientAllergy allergy = allergy(id, AllergyCategory.DRUG, drug.getName(), drug);
		allergy.setSeverity(severity);
		return allergy;
	}

	static PatientAllergy allergy(Long id, AllergyCategory category, String allergen, Drug drug) {
		PatientAllergy allergy = new PatientAllergy();
		ReflectionTestUtils.setField(allergy, "id", id);
		allergy.setCategory(category);
		allergy.setAllergen(allergen);
		allergy.setDrug(drug);
		allergy.setSeverity(AllergySeverity.UNKNOWN);
		allergy.setStatus(AllergyRecordStatus.ACTIVE);
		return allergy;
	}

	static PatientMedication medication(Long id, Drug drug) {
		PatientMedication medication = new PatientMedication();
		ReflectionTestUtils.setField(medication, "id", id);
		medication.setDrug(drug);
		medication.setStatus(MedicationStatus.ACTIVE);
		return medication;
	}

	static DrugInteraction interaction(Long id, String nameA, String nameB) {
		DrugInteraction interaction = new DrugInteraction();
		ReflectionTestUtils.setField(interaction, "id", id);
		interaction.setDrugNameA(nameA);
		interaction.setDrugNameB(nameB);
		interaction.setSeverity(InteractionSeverity.MAJOR);
		interaction.setDescription("ILLUSTRATIVE SAMPLE: demonstration interaction.");
		interaction.setSource("Illustrative teaching sample");
		interaction.setDataClassification("ILLUSTRATIVE_SAMPLE");
		interaction.setReviewStatus("NOT_CLINICALLY_REVIEWED");
		return interaction;
	}

}
