// Helpers that make JSON rule conditions readable and editable without writing JSON.
// The JSON format itself is unchanged; the backend still validates every rule.
import type { Fact, RuleError } from '../api/types'
import { Button, inputClass } from './ui'

const operatorWords: Record<string, string> = {
  gt: 'is greater than', gte: 'is at least', lt: 'is less than', lte: 'is at most',
  eq: 'equals', neq: 'does not equal', contains: 'includes',
}

type Json = Record<string, unknown>

function isObject(value: unknown): value is Json {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/** A condition in plain English, e.g. "Heart rate is greater than 100 beats/min". */
export function describeCondition(condition: unknown, facts: Fact[] = []): string {
  if (!isObject(condition)) return '(not a valid condition)'
  for (const group of ['all', 'any'] as const) {
    if (Array.isArray(condition[group])) {
      const parts = (condition[group] as unknown[]).map((c) => describeCondition(c, facts))
      return `${group === 'all' ? 'ALL of' : 'ANY of'}: (${parts.join('; ')})`
    }
  }
  if ('not' in condition) return `NOT (${describeCondition(condition.not, facts)})`
  const fact = facts.find((f) => f.name.toLowerCase() === String(condition.fact ?? '').toLowerCase())
  const label = fact?.label ?? String(condition.fact ?? '?')
  const words = operatorWords[String(condition.operator)] ?? String(condition.operator ?? '?')
  const unit = fact?.unit ? ` ${fact.unit}` : ''
  return `${label} ${words} ${String(condition.value ?? '?')}${unit}`
}

/** "condition.all[0].fact" -> "Condition › ALL group › item 1 › fact". */
const pathNames: Record<string, string> = { condition: 'Condition', all: 'ALL group', any: 'ANY group', not: 'NOT' }

export function humanPath(path: string): string {
  return path
    .split('.')
    .map((part) => {
      const name = part.replace(/\[\d+\]/g, '')
      const items = [...part.matchAll(/\[(\d+)\]/g)].map((match) => `item ${Number(match[1]) + 1}`)
      return [pathNames[name] ?? name, ...items].join(' › ')
    })
    .join(' › ')
}

export function RuleErrorList({ errors }: { errors: RuleError[] }) {
  return (
    <ul className="mt-1 list-disc pl-5">
      {errors.map((e) => (
        <li key={e.path + e.message}>
          <span className="font-medium">{humanPath(e.path)}</span>: {e.message} <span className="font-mono text-xs text-slate-500">{e.path}</span>
        </li>
      ))}
    </ul>
  )
}

interface Row { fact: string; operator: string; value: unknown }
interface Builder { combine: 'all' | 'any'; rows: Row[] }

/** The condition as builder rows, or null when it uses nesting or NOT (edit it as JSON instead). */
export function toBuilder(condition: unknown): Builder | null {
  if (!isObject(condition)) return null
  const isComparison = (c: unknown): c is Row => isObject(c) && 'fact' in c && !('all' in c) && !('any' in c) && !('not' in c)
  if (isComparison(condition)) return { combine: 'all', rows: [{ fact: String(condition.fact), operator: String(condition.operator ?? ''), value: condition.value ?? '' }] }
  for (const group of ['all', 'any'] as const) {
    const items = condition[group]
    if (Array.isArray(items) && Object.keys(condition).length === 1 && items.every(isComparison)) {
      return { combine: group, rows: (items as Row[]).map((r) => ({ fact: String(r.fact), operator: String(r.operator ?? ''), value: r.value ?? '' })) }
    }
  }
  return null
}

/** Builder rows back to the rule JSON: one row is a single comparison, more rows an ALL/ANY group. */
export function fromBuilder(builder: Builder): Json {
  const rows = builder.rows.map((r) => ({ fact: r.fact, operator: r.operator, value: r.value }))
  return rows.length === 1 ? rows[0] : { [builder.combine]: rows }
}

/** Numbers stay numbers in the JSON; anything else (e.g. ICD-10 codes) stays text. */
function parseValue(text: string): unknown {
  return /^-?\d+(\.\d+)?$/.test(text.trim()) ? Number(text) : text
}

/** Pick facts and operators from lists instead of typing JSON. */
export function ConditionBuilder({ condition, facts, onChange }: { condition: unknown; facts: Fact[]; onChange: (condition: Json) => void }) {
  const builder = toBuilder(condition)
  if (!builder) {
    return <p className="text-sm text-slate-600">This condition uses nested groups or NOT, so edit it in the JSON box below.</p>
  }
  const set = (next: Builder) => onChange(fromBuilder(next))
  const setRow = (index: number, row: Partial<Row>) =>
    set({ ...builder, rows: builder.rows.map((r, i) => (i === index ? { ...r, ...row } : r)) })

  return (
    <div className="space-y-2">
      {builder.rows.length > 1 && (
        <label className="flex items-center gap-2 text-sm text-slate-700">
          Match
          <select aria-label="Combine conditions" className={`${inputClass} w-auto`} value={builder.combine}
            onChange={(e) => set({ ...builder, combine: e.target.value as 'all' | 'any' })}>
            <option value="all">ALL of these (AND)</option>
            <option value="any">ANY of these (OR)</option>
          </select>
        </label>
      )}
      {builder.rows.map((row, index) => {
        const fact = facts.find((f) => f.name.toLowerCase() === row.fact.toLowerCase())
        return (
          <div key={index} className="grid gap-2 sm:grid-cols-[2fr_1.4fr_1fr_auto]">
            <select aria-label={`Fact ${index + 1}`} className={inputClass} value={fact?.name ?? row.fact}
              onChange={(e) => {
                const next = facts.find((f) => f.name === e.target.value)
                setRow(index, { fact: e.target.value, operator: next?.operators[0] ?? '' })
              }}>
              <option value="">Choose a fact</option>
              {!fact && row.fact && <option value={row.fact}>{row.fact} (unknown)</option>}
              {facts.map((f) => <option key={f.name} value={f.name}>{f.label}{f.unit ? ` (${f.unit})` : ''}</option>)}
            </select>
            <select aria-label={`Operator ${index + 1}`} className={inputClass} value={row.operator} onChange={(e) => setRow(index, { operator: e.target.value })}>
              <option value="">Choose</option>
              {(fact?.operators ?? Object.keys(operatorWords)).map((op) => <option key={op} value={op}>{operatorWords[op] ?? op}</option>)}
            </select>
            <input aria-label={`Value ${index + 1}`} className={inputClass} value={String(row.value)}
              placeholder={fact?.type === 'CODE_LIST' ? 'ICD-10 code' : 'Number'}
              onChange={(e) => setRow(index, { value: fact?.type === 'CODE_LIST' ? e.target.value : parseValue(e.target.value) })} />
            <Button variant="ghost" disabled={builder.rows.length === 1}
              onClick={() => set({ ...builder, rows: builder.rows.filter((_, i) => i !== index) })}>Remove</Button>
          </div>
        )
      })}
      <Button variant="secondary" onClick={() => set({ ...builder, rows: [...builder.rows, { fact: '', operator: '', value: '' }] })}>
        Add another condition
      </Button>
    </div>
  )
}
