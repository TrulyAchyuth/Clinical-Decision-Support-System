package com.cdss.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.cdss.dto.SafetyFinding;
import com.cdss.entity.Drug;
import com.cdss.entity.DrugInteraction;
import com.cdss.entity.PatientAllergy;
import com.cdss.entity.PatientMedication;

/**
 * Compares a proposed drug with a patient's active allergies, active
 * medications and the drug-interaction dataset. Plain Java with no Spring or
 * database, so it can be unit-tested directly.
 * <p>
 * Matching is deliberately strict: a drug matches only by exact drug name
 * (ignoring case and surrounding spaces, so every strength of the same drug
 * matches) or by exact knowledge-base category. Similar names are never
 * treated as the same drug. Allergies that cannot be matched automatically,
 * an allergy history that was never assessed, and a missing interaction
 * dataset are reported as UNCERTAIN. Finding nothing is reported as
 * NO_MATCH_FOUND with the limits of the data; it is never reported as "safe".
 * <p>
 * Mechanism, recommended action, version and review date of an interaction
 * are copied from the dataset row and left null when the row has none.
 */
public final class MedicationSafetyChecker {

	public static final String MATCH_FOUND = "MATCH_FOUND";

	public static final String NO_MATCH_FOUND = "NO_MATCH_FOUND";

	public static final String UNCERTAIN = "UNCERTAIN";

	static final String PATIENT_RECORD = "PATIENT_RECORD";

	static final String ILLUSTRATIVE_SAMPLE = "ILLUSTRATIVE_SAMPLE";

	static final String VERIFIED = "VERIFIED";

	private static final String ALLERGY_SOURCE = "Patient allergy record";

	private static final String MEDICATION_SOURCE = "Patient medication list";

	private static final String DATASET_SOURCE = "Drug-interaction dataset";

	private static final List<String> PRIORITY_ORDER = List.of("HIGH", "MEDIUM", "LOW");

	private MedicationSafetyChecker() {
	}

	/** All findings, most important first; each finding that needs review carries a content fingerprint in its code. */
	public static List<SafetyFinding> evaluate(Drug proposed, List<PatientMedication> activeMedications,
			List<PatientAllergy> activeAllergies, boolean noKnownAllergiesConfirmed,
			List<DrugInteraction> interactions) {
		List<SafetyFinding> findings = new ArrayList<>();
		findings.addAll(checkAllergies(proposed, activeAllergies, noKnownAllergiesConfirmed));
		findings.addAll(checkDuplicates(proposed, activeMedications));
		findings.addAll(checkInteractions(proposed, activeMedications, interactions));
		return findings.stream()
			.map(MedicationSafetyChecker::withFingerprint)
			.sorted(Comparator.comparingInt((SafetyFinding finding) -> PRIORITY_ORDER.indexOf(finding.priority())))
			.toList();
	}

	/** WARNINGS if anything matched, UNCERTAIN if something could not be checked, otherwise no issues in the available data. */
	public static String overallStatus(List<SafetyFinding> findings) {
		if (findings.stream().anyMatch((finding) -> MATCH_FOUND.equals(finding.resultStatus()))) {
			return "WARNINGS";
		}
		if (findings.stream().anyMatch((finding) -> UNCERTAIN.equals(finding.resultStatus()))) {
			return UNCERTAIN;
		}
		return "NO_ISSUES_FOUND_IN_AVAILABLE_DATA";
	}

	private static List<SafetyFinding> checkAllergies(Drug proposed, List<PatientAllergy> activeAllergies,
			boolean noKnownAllergiesConfirmed) {
		List<SafetyFinding> findings = new ArrayList<>();
		int notComparable = 0;
		for (PatientAllergy allergy : activeAllergies) {
			switch (allergy.getCategory()) {
				case DRUG -> {
					if (sameName(allergy.getDrug().getName(), proposed.getName())) {
						findings.add(allergyMatch(allergy, proposed, "Recorded drug allergy matches " + label(proposed),
								"The patient has an active recorded allergy to " + allergy.getDrug().getName()
										+ reaction(allergy) + ". The proposed drug " + label(proposed)
										+ " has the same drug name."));
					}
				}
				case DRUG_CATEGORY -> {
					if (sameName(allergy.getAllergen(), proposed.getCategory())) {
						findings.add(allergyMatch(allergy, proposed,
								"Recorded drug-category allergy matches " + label(proposed),
								"The patient has an active recorded allergy to the drug category '"
										+ allergy.getAllergen() + "'" + reaction(allergy) + ". The proposed drug "
										+ label(proposed) + " is in that knowledge-base category."));
					}
				}
				case OTHER -> findings.add(recordFinding("ALLERGY-UNMATCHED-" + allergy.getId(), "ALLERGY", UNCERTAIN,
						"Free-text allergy could not be compared automatically", allergy.getSeverity().name(),
						List.of(label(proposed)), List.of(allergy.getAllergen()),
						"The allergy '" + allergy.getAllergen() + "'" + reaction(allergy)
								+ " is recorded as free text and is not linked to the drug catalogue, so it cannot be "
								+ "compared with " + label(proposed) + " automatically.",
						"Compare this allergy with the proposed drug manually before continuing.", ALLERGY_SOURCE,
						true));
				case FOOD, ENVIRONMENTAL -> notComparable++;
			}
		}
		if (activeAllergies.isEmpty() && !noKnownAllergiesConfirmed) {
			findings.add(recordFinding("ALLERGY-STATUS-NOT-ASSESSED", "DATA_COMPLETENESS", UNCERTAIN,
					"Allergy history not assessed", null, List.of(label(proposed)), List.of(),
					"No allergy is recorded and 'no known allergies' has not been confirmed. An allergy to "
							+ label(proposed) + " cannot be ruled out.",
					"Record the patient's allergies, or confirm 'no known allergies', before relying on this check.",
					ALLERGY_SOURCE, true));
		}
		if (findings.isEmpty()) {
			String explanation = noKnownAllergiesConfirmed && activeAllergies.isEmpty()
					? "'No known allergies' has been confirmed for this patient, so no allergy record was compared."
					: "None of the patient's " + activeAllergies.size() + " active allergy record(s) matches "
							+ label(proposed) + "."
							+ (notComparable > 0 ? " " + notComparable
									+ " food or environmental allergy record(s) are not compared with drugs." : "");
			findings.add(recordFinding("ALLERGY-NONE-FOUND", "ALLERGY", NO_MATCH_FOUND, "No recorded allergy matches",
					null, List.of(label(proposed)), List.of(),
					explanation + " This only covers allergies that have been recorded.", null, ALLERGY_SOURCE, false));
		}
		return findings;
	}

	private static List<SafetyFinding> checkDuplicates(Drug proposed, List<PatientMedication> activeMedications) {
		List<SafetyFinding> findings = new ArrayList<>();
		for (PatientMedication medication : activeMedications) {
			if (sameName(medication.getDrug().getName(), proposed.getName())) {
				findings.add(recordFinding("DUPLICATE-" + medication.getId(), "DUPLICATE_THERAPY", MATCH_FOUND,
						"Same drug already active: " + label(medication.getDrug()), null,
						List.of(label(proposed), label(medication.getDrug())), List.of(),
						"The patient already has an active medication with the same drug name: "
								+ label(medication.getDrug()) + ".",
						"Check whether both medications are intended.", MEDICATION_SOURCE, true));
			}
		}
		return findings;
	}

	private static List<SafetyFinding> checkInteractions(Drug proposed, List<PatientMedication> activeMedications,
			List<DrugInteraction> interactions) {
		List<PatientMedication> others = activeMedications.stream()
			.filter((medication) -> !sameName(medication.getDrug().getName(), proposed.getName()))
			.toList();
		List<SafetyFinding> findings = new ArrayList<>();
		for (PatientMedication medication : others) {
			for (DrugInteraction interaction : interactions) {
				if (isPair(interaction, proposed.getName(), medication.getDrug().getName())) {
					findings.add(interactionMatch(interaction, proposed, medication));
				}
			}
		}
		if (!findings.isEmpty()) {
			return findings;
		}
		if (others.isEmpty()) {
			findings.add(new SafetyFinding("INTERACTION-NONE-FOUND", "INTERACTION", NO_MATCH_FOUND, "LOW",
					"No other active medications to compare", null, List.of(label(proposed)), List.of(),
					"No other active medications are recorded for this patient, so no interaction could be checked."
							+ " The medication list may be incomplete.",
					null, null, MEDICATION_SOURCE, PATIENT_RECORD, null, null, null, false));
		}
		else if (interactions.isEmpty()) {
			findings.add(new SafetyFinding("INTERACTION-CHECK-UNAVAILABLE", "INTERACTION", UNCERTAIN, "MEDIUM",
					"Interaction check unavailable", null, List.of(label(proposed)), List.of(),
					"No drug-interaction data is loaded, so " + label(proposed) + " could not be checked against the "
							+ "patient's " + others.size() + " other active medication(s).",
					null, "Check interactions in a verified drug reference before continuing.", DATASET_SOURCE, null,
					null, null, null, true));
		}
		else {
			String classification = datasetClassification(interactions);
			boolean covered = interactions.stream()
				.anyMatch((row) -> sameName(row.getDrugNameA(), proposed.getName())
						|| sameName(row.getDrugNameB(), proposed.getName()));
			findings.add(new SafetyFinding("INTERACTION-NONE-FOUND", "INTERACTION", NO_MATCH_FOUND, "LOW",
					"No interaction found in the dataset", null,
					others.stream().map((medication) -> label(medication.getDrug())).toList(), List.of(),
					"No interaction between " + label(proposed) + " and the patient's " + others.size()
							+ " other active medication(s) was found in the configured dataset of "
							+ interactions.size() + " interaction pair(s)."
							+ (covered ? "" : " " + proposed.getName() + " does not appear in the dataset at all,"
									+ " so its interactions are not covered.")
							+ (ILLUSTRATIVE_SAMPLE.equals(classification)
									? " The dataset is a small illustrative sample, so"
									: " The dataset is not exhaustive, so")
							+ " this does not mean the combination is safe.",
					null, null, DATASET_SOURCE, classification, null, null, null, false));
		}
		return findings;
	}

	private static SafetyFinding allergyMatch(PatientAllergy allergy, Drug proposed, String title, String explanation) {
		return recordFinding("ALLERGY-" + allergy.getId(), "ALLERGY", MATCH_FOUND, title, allergy.getSeverity().name(),
				List.of(label(proposed)), List.of(allergy.getAllergen()), explanation,
				"Confirm the allergy history and recorded reaction with the patient before deciding whether to "
						+ "proceed.",
				ALLERGY_SOURCE, true);
	}

	private static SafetyFinding interactionMatch(DrugInteraction interaction, Drug proposed,
			PatientMedication medication) {
		boolean sample = ILLUSTRATIVE_SAMPLE.equals(interaction.getDataClassification());
		return new SafetyFinding("INTERACTION-" + interaction.getId() + "-" + medication.getId(), "INTERACTION",
				MATCH_FOUND, "HIGH",
				"Interaction listed: " + label(proposed) + " with " + label(medication.getDrug()),
				interaction.getSeverity().name(), List.of(label(proposed), label(medication.getDrug())), List.of(),
				interaction.getDescription()
						+ (sample ? " [Illustrative sample data: not authoritative clinical guidance.]" : ""),
				interaction.getMechanism(), interaction.getRecommendedAction(), interaction.getSource(),
				interaction.getDataClassification(), interaction.getSourceVersion(), interaction.getLastReviewedOn(),
				interaction.getReviewStatus(), true);
	}

	/** A finding taken from the patient's own record; priority follows from the result. */
	private static SafetyFinding recordFinding(String code, String checkType, String resultStatus, String title,
			String severity, List<String> medications, List<String> allergens, String explanation,
			String recommendedAction, String source, boolean requiresReview) {
		String priority = switch (resultStatus) {
			case MATCH_FOUND -> "HIGH";
			case UNCERTAIN -> "MEDIUM";
			default -> "LOW";
		};
		return new SafetyFinding(code, checkType, resultStatus, priority, title, severity, medications, allergens,
				explanation, null, recommendedAction, source, PATIENT_RECORD, null, null, null, requiresReview);
	}

	/**
	 * Appends a short hash of everything the clinician reviews, so an
	 * acknowledgement stops applying as soon as the finding changes, while an
	 * unchanged finding keeps its code and is not raised as new again.
	 */
	private static SafetyFinding withFingerprint(SafetyFinding finding) {
		if (!finding.requiresReview()) {
			return finding;
		}
		String content = String.join("\u0000", finding.checkType(), finding.resultStatus(),
				Objects.toString(finding.severity()), finding.title(), String.join(",", finding.medications()),
				String.join(",", finding.allergens()), finding.explanation(), Objects.toString(finding.mechanism()),
				Objects.toString(finding.recommendedAction()), finding.source(),
				Objects.toString(finding.dataClassification()), Objects.toString(finding.sourceVersion()),
				Objects.toString(finding.sourceReviewedOn()), Objects.toString(finding.reviewStatus()));
		return new SafetyFinding(finding.code() + "-" + shortHash(content), finding.checkType(),
				finding.resultStatus(), finding.priority(), finding.title(), finding.severity(), finding.medications(),
				finding.allergens(), finding.explanation(), finding.mechanism(), finding.recommendedAction(),
				finding.source(), finding.dataClassification(), finding.sourceVersion(), finding.sourceReviewedOn(),
				finding.reviewStatus(), true);
	}

	private static String shortHash(String content) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(Arrays.copyOf(digest, 4));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is always available in Java", ex);
		}
	}

	private static String datasetClassification(List<DrugInteraction> interactions) {
		boolean allVerified = interactions.stream()
			.allMatch((interaction) -> VERIFIED.equals(interaction.getDataClassification()));
		return allVerified ? VERIFIED : ILLUSTRATIVE_SAMPLE;
	}

	private static boolean isPair(DrugInteraction interaction, String first, String second) {
		return (sameName(interaction.getDrugNameA(), first) && sameName(interaction.getDrugNameB(), second))
				|| (sameName(interaction.getDrugNameA(), second) && sameName(interaction.getDrugNameB(), first));
	}

	static boolean sameName(String first, String second) {
		return first != null && second != null && key(first).equals(key(second));
	}

	private static String key(String name) {
		return name.strip().toLowerCase(Locale.ROOT);
	}

	private static String reaction(PatientAllergy allergy) {
		return allergy.getReaction() == null ? "" : " (recorded reaction: " + allergy.getReaction() + ")";
	}

	private static String label(Drug drug) {
		return drug.getName() + " " + drug.getDosage();
	}

}
