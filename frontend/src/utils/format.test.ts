import { describe, expect, it } from 'vitest'
import { ageInYears, dateTimeInputToIso, optionalNumber, optionalText } from './format'

describe('format helpers', () => {
  it('calculates age in whole years around the birthday', () => {
    expect(ageInYears('1958-04-12', new Date(2026, 3, 11))).toBe(67)
    expect(ageInYears('1958-04-12', new Date(2026, 3, 12))).toBe(68)
  })

  it('turns empty inputs into undefined so optional fields are omitted', () => {
    expect(optionalText('  ')).toBeUndefined()
    expect(optionalText(' a ')).toBe('a')
    expect(optionalNumber('')).toBeUndefined()
    expect(optionalNumber('37.5')).toBe(37.5)
  })

  it('converts a local datetime-local value to an ISO instant', () => {
    expect(dateTimeInputToIso('2026-10-13T09:30')).toBe(new Date(2026, 9, 13, 9, 30).toISOString())
  })
})

describe('dateTimeInputToIso', () => {
  it('returns null for a blank or invalid value so the backend reports the field', () => {
    expect(dateTimeInputToIso('')).toBeNull()
    expect(dateTimeInputToIso('not a date')).toBeNull()
  })
})
