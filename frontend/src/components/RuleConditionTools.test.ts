import { describe, expect, it } from 'vitest'
import type { Fact } from '../api/types'
import { describeCondition, fromBuilder, humanPath, toBuilder } from './RuleConditionTools'

const facts: Fact[] = [
  { name: 'vitals.heartRate', label: 'Heart rate', unit: 'beats/min', type: 'NUMBER', operators: ['gt', 'lt'] },
  { name: 'conditions', label: 'Active conditions (ICD-10 codes)', unit: null, type: 'CODE_LIST', operators: ['contains'] },
]

describe('rule condition helpers', () => {
  it('describes nested conditions in plain English', () => {
    const condition = { all: [{ fact: 'vitals.heartRate', operator: 'gt', value: 100 }, { not: { fact: 'conditions', operator: 'contains', value: 'I10' } }] }

    expect(describeCondition(condition, facts))
      .toBe('ALL of: (Heart rate is greater than 100 beats/min; NOT (Active conditions (ICD-10 codes) includes I10))')
  })

  it('turns rule-error paths into readable locations', () => {
    expect(humanPath('condition.all[0].fact')).toBe('Condition › ALL group › item 1 › fact')
  })

  it('round-trips simple conditions through the builder and refuses nested ones', () => {
    const single = { fact: 'vitals.heartRate', operator: 'gt', value: 100 }
    const group = { any: [single, { fact: 'conditions', operator: 'contains', value: 'I10' }] }

    expect(fromBuilder(toBuilder(single)!)).toEqual(single)
    expect(fromBuilder(toBuilder(group)!)).toEqual(group)
    expect(toBuilder({ not: single })).toBeNull()
    expect(toBuilder({ all: [group] })).toBeNull()
  })
})
