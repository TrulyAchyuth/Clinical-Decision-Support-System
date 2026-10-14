import type { SafetyCheck, SafetyFinding } from '../api/types'
import { formatDate, formatDateTime, titleCase } from '../utils/format'
import { Badge } from './ui'

const overall: Record<SafetyCheck['overallStatus'], { label: string; box: string }> = {
  WARNINGS: { label: 'Warnings found — clinician review required', box: 'border-red-300 bg-red-50' },
  UNCERTAIN: { label: 'Safety status uncertain — data missing or not matchable', box: 'border-amber-300 bg-amber-50' },
  NO_ISSUES_FOUND_IN_AVAILABLE_DATA: {
    label: 'No issues found in the available data (this is not a guarantee of safety)',
    box: 'border-slate-300 bg-slate-50',
  },
}

const checkLabels: Record<SafetyFinding['checkType'], string> = {
  ALLERGY: 'Allergy',
  INTERACTION: 'Drug interaction',
  DUPLICATE_THERAPY: 'Duplicate therapy',
  DATA_COMPLETENESS: 'Missing data',
}

const priorityTone = { HIGH: 'red', MEDIUM: 'amber', LOW: 'slate' } as const

/** Where a finding's information came from, so trusted, sample and patient-record data are never confused. */
function sourceKind(f: SafetyFinding): { label: string; tone: 'green' | 'amber' | 'slate' } {
  switch (f.dataClassification) {
    case 'VERIFIED': return { label: 'Trusted source', tone: 'green' }
    case 'ILLUSTRATIVE_SAMPLE': return { label: 'Illustrative sample data', tone: 'amber' }
    case 'PATIENT_RECORD': return { label: 'Patient record', tone: 'slate' }
    default: return { label: 'No source available', tone: 'amber' }
  }
}

/**
 * Patient-specific feedback from a medication safety check. When
 * `acknowledged` and `onAcknowledge` are given, each warning that needs
 * review gets a checkbox the clinician must tick before saving. Codes in
 * `changedCodes` are marked as new or changed since the previous check.
 */
export function SafetyCheckPanel({ check, acknowledged, onAcknowledge, changedCodes }: {
  check: SafetyCheck
  acknowledged?: Set<string>
  onAcknowledge?: (code: string, checked: boolean) => void
  changedCodes?: Set<string>
}) {
  const usesSample = check.findings.some((f) => f.dataClassification === 'ILLUSTRATIVE_SAMPLE')
  return (
    <section className={`rounded-lg border p-4 ${overall[check.overallStatus].box}`} aria-label="Safety check result">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h4 className="text-sm font-semibold text-slate-900">Safety check: {check.drugName}</h4>
        <span className="text-xs text-slate-500">Checked {formatDateTime(check.checkedAt)}</span>
      </div>
      <p className="mt-1 text-sm font-medium text-slate-800">{overall[check.overallStatus].label}</p>
      {usesSample && (
        <p className="mt-2 rounded border border-amber-400 bg-amber-100 px-3 py-2 text-xs font-semibold text-amber-900" role="note">
          Interaction results use ILLUSTRATIVE SAMPLE DATA — not a verified drug-interaction database and not authoritative clinical guidance.
        </p>
      )}
      <ul className="mt-3 space-y-2">
        {check.findings.map((f) => <FindingItem key={f.code} f={f} isChanged={changedCodes?.has(f.code) ?? false}
          acknowledged={acknowledged} onAcknowledge={onAcknowledge} />)}
      </ul>
      <p className="mt-3 text-xs text-slate-600">{check.disclaimer}</p>
      <p className="mt-1 text-xs text-slate-500">Custom clinical rules are evaluated separately under “Request decision” and are not part of this check.</p>
    </section>
  )
}

function FindingItem({ f, isChanged, acknowledged, onAcknowledge }: {
  f: SafetyFinding
  isChanged: boolean
  acknowledged?: Set<string>
  onAcknowledge?: (code: string, checked: boolean) => void
}) {
  const kind = sourceKind(f)
  const isMatchFromDataset = f.resultStatus === 'MATCH_FOUND' && f.dataClassification !== 'PATIENT_RECORD'
  const sourceDetails = [
    f.sourceVersion && `version ${f.sourceVersion}`,
    f.sourceReviewedOn && `reviewed ${formatDate(f.sourceReviewedOn)}`,
    f.reviewStatus && titleCase(f.reviewStatus).replaceAll('_', ' '),
  ].filter(Boolean).join(' · ')
  return (
    <li className="rounded-md border border-slate-200 bg-white p-3 text-sm">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-medium text-slate-900">{checkLabels[f.checkType]}</span>
        <Badge tone={f.resultStatus === 'MATCH_FOUND' ? 'red' : f.resultStatus === 'UNCERTAIN' ? 'amber' : 'slate'}>
          {f.resultStatus === 'MATCH_FOUND' ? 'Match found' : f.resultStatus === 'UNCERTAIN' ? 'Uncertain' : 'No match found'}
        </Badge>
        <Badge tone={priorityTone[f.priority]}>Priority: {titleCase(f.priority)}</Badge>
        {f.severity && <Badge tone="slate">Severity: {titleCase(f.severity)}</Badge>}
        <Badge tone={kind.tone}>{kind.label}</Badge>
        {f.requiresReview && <Badge tone="red">Review required</Badge>}
        {isChanged && <Badge tone="amber">New or changed since last check</Badge>}
      </div>
      <p className="mt-1 font-medium text-slate-900">{f.title}</p>
      <p className="mt-1 text-slate-700">{f.explanation}</p>
      <dl className="mt-2 grid gap-x-4 gap-y-1 text-xs sm:grid-cols-[max-content_1fr]">
        <dt className="font-medium text-slate-600">Medications</dt><dd className="text-slate-700">{f.medications.join(', ')}</dd>
        {f.allergens.length > 0 && <>
          <dt className="font-medium text-slate-600">Recorded allergens</dt><dd className="text-slate-700">{f.allergens.join(', ')}</dd>
        </>}
        {(f.mechanism || isMatchFromDataset) && <>
          <dt className="font-medium text-slate-600">Mechanism / rationale</dt>
          <dd className="text-slate-700">{f.mechanism ?? <span className="italic text-slate-500">Not provided by the source</span>}</dd>
        </>}
        {(f.recommendedAction || isMatchFromDataset) && <>
          <dt className="font-medium text-slate-600">Recommended review</dt>
          <dd className="text-slate-700">{f.recommendedAction ?? <span className="italic text-slate-500">Not provided by the source</span>}</dd>
        </>}
        <dt className="font-medium text-slate-600">Source</dt>
        <dd className="text-slate-700">{f.source}{sourceDetails && ` (${sourceDetails})`}</dd>
      </dl>
      {f.requiresReview && onAcknowledge && (
        <label className="mt-2 flex items-center gap-2 text-xs font-medium text-slate-800">
          <input type="checkbox" className="h-4 w-4 accent-teal-700" checked={acknowledged?.has(f.code) ?? false}
            onChange={(e) => onAcknowledge(f.code, e.target.checked)} />
          I have reviewed this warning
        </label>
      )}
    </li>
  )
}
