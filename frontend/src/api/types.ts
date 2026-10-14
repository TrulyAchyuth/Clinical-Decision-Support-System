// TypeScript versions of the backend's request/response records (com.cdss.dto).
// Dates are ISO strings: LocalDate "2026-10-13", Instant "2026-10-13T09:30:00Z".

export type Gender = 'MALE' | 'FEMALE' | 'OTHER' | 'UNKNOWN'

export interface Patient {
  id: number
  fullName: string
  dateOfBirth: string
  gender: Gender
  phone: string | null
  email: string | null
  address: string | null
  createdAt: string
  updatedAt: string
}

// In the *Input types, `null` means "left empty": the backend then answers with a
// clear field error ("must not be null") instead of failing to read the request.

export interface PatientInput {
  fullName: string
  dateOfBirth: string | null
  gender: Gender | null
  phone?: string
  email?: string
  address?: string
}

export interface Drug {
  id: number
  name: string
  dosage: string
  category: string
  notes: string | null
}

export type DrugInput = Omit<Drug, 'id'>

export interface Disease {
  id: number
  name: string
  icd10Code: string
}

export type DiseaseInput = Omit<Disease, 'id'>

export interface LabTest {
  id: number
  name: string
  referenceRangeLow: number
  referenceRangeHigh: number
  unit: string
  notes: string | null
}

/** Empty range fields are sent as null so the backend can report them as required. */
export interface LabTestInput {
  name: string
  referenceRangeLow: number | null
  referenceRangeHigh: number | null
  unit: string
  notes: string | null
}

export interface VitalSigns {
  id: number
  patientId: number
  recordedAt: string
  systolicBp: number | null
  diastolicBp: number | null
  temperature: number | null
  heartRate: number | null
  respiratoryRate: number | null
  oxygenSaturation: number | null
  notes: string | null
  createdAt: string
}

export interface VitalSignsInput {
  recordedAt: string | null
  systolicBp?: number
  diastolicBp?: number
  temperature?: number
  heartRate?: number
  respiratoryRate?: number
  oxygenSaturation?: number
  notes?: string
}

export type LabFlag = 'LOW' | 'NORMAL' | 'HIGH'

export interface LabResult {
  id: number
  patientId: number
  labTestId: number
  labTestName: string
  value: number
  unit: string
  measuredAt: string
  referenceRangeLow: number
  referenceRangeHigh: number
  flag: LabFlag
  notes: string | null
}

export interface LabResultInput {
  labTestId: number
  value: number
  measuredAt: string | null
  notes?: string
}

export type ConditionStatus = 'ACTIVE' | 'RESOLVED'

export interface PatientCondition {
  id: number
  patientId: number
  diseaseId: number
  diseaseName: string
  icd10Code: string
  status: ConditionStatus
  onsetDate: string | null
  notes: string | null
}

export interface PatientConditionInput {
  diseaseId: number
  status: ConditionStatus
  onsetDate?: string
  notes?: string
}

export type Severity = 'LOW' | 'MEDIUM' | 'HIGH'

export interface Rule {
  id: number
  ruleId: string
  name: string
  description: string | null
  severity: Severity
  priority: number
  enabled: boolean
  condition: Record<string, unknown>
  recommendedActions: string[]
  createdAt: string
  updatedAt: string
  /** False when the stored condition no longer validates (e.g. a lab test was renamed). */
  valid: boolean
  validationErrors: RuleError[]
}

/** A rule as clinicians see it: plain English, no JSON definition. */
export interface RuleSummary {
  id: number
  ruleId: string
  name: string
  description: string | null
  severity: Severity
  priority: number
  enabled: boolean
  valid: boolean
  conditionSummary: string
  recommendedActions: string[]
}

export interface RuleInput {
  ruleId: string
  name: string
  description?: string
  severity: Severity
  /** null when left empty, so the backend reports it as required. */
  priority: number | null
  enabled: boolean
  condition: unknown
  recommendedActions: string[]
}

export interface RuleError {
  path: string
  message: string
}

export interface RuleValidation {
  valid: boolean
  errors: RuleError[]
}

export interface Fact {
  name: string
  label: string
  unit: string | null
  type: 'NUMBER' | 'CODE_LIST'
  operators: string[]
}

export interface RuleResult {
  ruleId: string
  name: string
  severity: Severity
  priority: number
  reasons: string[]
  recommendedActions: string[]
}

export type DecisionOutcome = 'RULES_MATCHED' | 'NO_RULES_MATCHED'

export interface Decision {
  id: number
  patientId: number
  requestedAt: string
  outcome: DecisionOutcome
  summary: string
  rulesEvaluated: number
  rulesNotMatched: number
  matchedRules: RuleResult[]
  insufficientData: RuleResult[]
  invalidRules: RuleResult[]
  disclaimer: string
}

export interface Health {
  status: 'UP' | 'DEGRADED'
  components: { application: 'UP' | 'DOWN'; database: 'UP' | 'DOWN' }
  timestamp: string
}

export type Role = 'ADMIN' | 'CLINICIAN'

/** The logged-in user (CurrentUserResponse). */
export interface CurrentUser {
  username: string
  role: Role
}

// ---- medications, allergies and the safety check ----

export type MedicationStatus = 'ACTIVE' | 'DISCONTINUED' | 'COMPLETED'
export type MedicationRoute = 'ORAL' | 'INTRAVENOUS' | 'INTRAMUSCULAR' | 'SUBCUTANEOUS' | 'TOPICAL' | 'INHALED' | 'SUBLINGUAL' | 'RECTAL' | 'OTHER'

export interface Medication {
  id: number
  patientId: number
  drugId: number
  drugName: string
  drugStrength: string
  drugCategory: string
  dose: number
  doseUnit: string
  route: MedicationRoute
  frequency: string
  startDate: string
  endDate: string | null
  status: MedicationStatus
  notes: string | null
  statusReason: string | null
  statusChangedAt: string | null
  statusChangedBy: string | null
  safetyWarnings: string[]
  safetyOverrideReason: string | null
  recordedBy: string
  createdAt: string
  updatedAt: string
}

export interface MedicationInput {
  drugId: number | null
  dose: number | null
  doseUnit: string
  route: MedicationRoute | null
  frequency: string
  startDate: string | null
  endDate?: string
  notes?: string
  acknowledgedWarnings?: string[]
  overrideReason?: string
}

export type MedicationUpdate = Omit<MedicationInput, 'drugId' | 'acknowledgedWarnings' | 'overrideReason'>

/** Fields the source does not provide are null; the backend never fills them in. */
export interface SafetyFinding {
  /** Ends with a content fingerprint: a materially changed finding gets a new code. */
  code: string
  checkType: 'ALLERGY' | 'INTERACTION' | 'DUPLICATE_THERAPY' | 'DATA_COMPLETENESS'
  resultStatus: 'MATCH_FOUND' | 'NO_MATCH_FOUND' | 'UNCERTAIN'
  /** Review order only, not clinical urgency. */
  priority: 'HIGH' | 'MEDIUM' | 'LOW'
  title: string
  severity: string | null
  medications: string[]
  allergens: string[]
  explanation: string
  mechanism: string | null
  recommendedAction: string | null
  source: string
  dataClassification: 'PATIENT_RECORD' | 'ILLUSTRATIVE_SAMPLE' | 'VERIFIED' | null
  sourceVersion: string | null
  sourceReviewedOn: string | null
  reviewStatus: string | null
  requiresReview: boolean
}

export interface SafetyCheck {
  patientId: number
  drugId: number
  drugName: string
  overallStatus: 'WARNINGS' | 'UNCERTAIN' | 'NO_ISSUES_FOUND_IN_AVAILABLE_DATA'
  requiresAcknowledgement: boolean
  findings: SafetyFinding[]
  checkedAt: string
  disclaimer: string
}

export type AllergyCategory = 'DRUG' | 'DRUG_CATEGORY' | 'FOOD' | 'ENVIRONMENTAL' | 'OTHER'
export type AllergySeverity = 'MILD' | 'MODERATE' | 'SEVERE' | 'UNKNOWN'
export type AllergyAssessment = 'ALLERGIES_RECORDED' | 'NO_KNOWN_ALLERGIES' | 'NOT_ASSESSED'

export interface Allergy {
  id: number
  patientId: number
  category: AllergyCategory
  drugId: number | null
  allergen: string
  reaction: string | null
  severity: AllergySeverity
  status: 'ACTIVE' | 'RESOLVED'
  recordedDate: string
  notes: string | null
  resolutionReason: string | null
  resolvedAt: string | null
  resolvedBy: string | null
  recordedBy: string
  createdAt: string
}

export interface PatientAllergies {
  patientId: number
  assessment: AllergyAssessment
  assessmentExplanation: string
  noKnownAllergiesConfirmedAt: string | null
  noKnownAllergiesConfirmedBy: string | null
  allergies: Allergy[]
}

export interface AllergyInput {
  category: AllergyCategory
  drugId?: number
  allergen?: string
  reaction?: string
  severity: AllergySeverity
  recordedDate: string | null
  notes?: string
}

export type AllergyUpdate = Pick<AllergyInput, 'reaction' | 'severity' | 'recordedDate' | 'notes'>

export interface DrugInteraction {
  id: number
  drugNameA: string
  drugNameB: string
  severity: 'MINOR' | 'MODERATE' | 'MAJOR'
  description: string
  mechanism: string | null
  recommendedAction: string | null
  source: string
  sourceVersion: string | null
  lastReviewedOn: string | null
  dataClassification: string
  reviewStatus: string
}

// ---- follow-ups ----

export type FollowUpStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED'
export type FollowUpTiming = 'OVERDUE' | 'UPCOMING' | 'LATER' | 'COMPLETED' | 'CANCELLED'

export interface FollowUp {
  id: number
  patientId: number
  patientName: string
  scheduledAt: string
  reason: string
  notes: string | null
  status: FollowUpStatus
  timing: FollowUpTiming
  rescheduleCount: number
  outcomeNotes: string | null
  statusChangedAt: string | null
  statusChangedBy: string | null
  createdBy: string
  createdAt: string
}

export interface FollowUpInput {
  scheduledAt: string | null
  reason: string
  notes?: string
}

// ---- rule dry run ----

export interface RuleDryRun {
  patientId: number
  valid: boolean
  errors: RuleError[]
  result: 'MATCHED' | 'NOT_MATCHED' | 'INSUFFICIENT_DATA' | null
  reasons: string[]
  evaluatedAt: string
  note: string
}

// ---- user management (admin) ----

export interface UserSummary {
  id: number
  username: string
  role: Role
  enabled: boolean
  createdAt: string | null
}

export interface UserInput {
  username: string
  password: string
  role: Role
}
