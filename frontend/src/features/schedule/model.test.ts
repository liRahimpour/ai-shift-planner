import { describe, expect, it } from 'vitest'
import type { ShiftWithAssignments, ValidationIssue } from '@/api/types'
import { groupByDate, indexIssues, shiftHours, totalHours } from './model'

const shift = (over: Partial<ShiftWithAssignments>): ShiftWithAssignments => ({
  shiftId: 's1',
  departmentId: 'd1',
  departmentName: 'Bar',
  date: '2026-09-12',
  startTime: '18:00',
  endTime: '23:00',
  crossesMidnight: false,
  requiredEmployees: 3,
  minimumEmployees: 2,
  assignments: [],
  ...over,
})

describe('shiftHours', () => {
  it('measures a normal shift', () => {
    expect(shiftHours(shift({ startTime: '10:00', endTime: '16:00' }))).toBe(6)
  })

  it('measures a shift that runs past midnight', () => {
    // 18:00-02:00 is eight hours, not minus sixteen. Bar and closing shifts depend on this,
    // and they are exactly the shifts where hours matter most to the people working them.
    expect(shiftHours(shift({ startTime: '18:00', endTime: '02:00' }))).toBe(8)
  })

  it('treats an exact 24-hour span as a full day rather than zero', () => {
    expect(shiftHours(shift({ startTime: '08:00', endTime: '08:00' }))).toBe(24)
  })

  it('handles times with seconds', () => {
    expect(shiftHours(shift({ startTime: '09:30', endTime: '17:00' }))).toBe(7.5)
  })

  it('sums a week', () => {
    expect(
      totalHours([
        shift({ startTime: '10:00', endTime: '16:00' }),
        shift({ startTime: '18:00', endTime: '02:00' }),
      ]),
    ).toBe(14)
  })
})

describe('groupByDate', () => {
  it('orders days ascending and shifts within a day by start time', () => {
    const grouped = groupByDate([
      shift({ shiftId: 'b', date: '2026-09-13', startTime: '18:00' }),
      shift({ shiftId: 'c', date: '2026-09-12', startTime: '20:00' }),
      shift({ shiftId: 'a', date: '2026-09-12', startTime: '08:00' }),
    ])

    expect(grouped.map(([date]) => date)).toEqual(['2026-09-12', '2026-09-13'])
    expect(grouped[0]?.[1].map((s) => s.shiftId)).toEqual(['a', 'c'])
  })

  it('does not mutate the input array order', () => {
    const input = [
      shift({ shiftId: 'late', startTime: '20:00' }),
      shift({ shiftId: 'early', startTime: '08:00' }),
    ]
    groupByDate(input)
    expect(input.map((s) => s.shiftId)).toEqual(['late', 'early'])
  })
})

describe('indexIssues', () => {
  const issue = (over: Partial<ValidationIssue>): ValidationIssue => ({
    severity: 'WARNING',
    code: 'REST_TIME',
    message: 'nur 9 Stunden Ruhezeit',
    employeeId: 'e1',
    shiftId: 's1',
    ...over,
  })

  it('groups issues by the shift they belong to', () => {
    const map = indexIssues([issue({}), issue({ code: 'SKILL' }), issue({ shiftId: 's2' })])
    expect(map.get('s1')).toHaveLength(2)
    expect(map.get('s2')).toHaveLength(1)
  })

  it('drops issues with no shift so they cannot be attributed to the wrong card', () => {
    const map = indexIssues([issue({ shiftId: null })])
    expect(map.size).toBe(0)
  })
})
