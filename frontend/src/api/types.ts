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
