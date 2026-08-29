import { describe, expect, it } from 'vitest'
import { eachDate, formatTime, instantToLocalInput, localInputToInstant, parseIsoDate } from './format'

describe('parseIsoDate', () => {
  it('keeps the calendar day regardless of the browser timezone', () => {
    // `new Date('2026-09-12')` parses as UTC midnight, which is 11 September in any
    // timezone west of Greenwich - i.e. the shift would show up on the wrong day.
    const parsed = parseIsoDate('2026-09-12')
    expect(parsed.getFullYear()).toBe(2026)
    expect(parsed.getMonth()).toBe(8)
    expect(parsed.getDate()).toBe(12)
  })
})

describe('eachDate', () => {
  it('includes both ends of the range', () => {
    expect(eachDate('2026-09-07', '2026-09-13')).toHaveLength(7)
    expect(eachDate('2026-09-07', '2026-09-13')[6]).toBe('2026-09-13')
  })

  it('returns a single day when start equals end', () => {
    expect(eachDate('2026-09-07', '2026-09-07')).toEqual(['2026-09-07'])
  })

  it('crosses a month boundary', () => {
    expect(eachDate('2026-09-29', '2026-10-02')).toEqual([
      '2026-09-29',
      '2026-09-30',
      '2026-10-01',
      '2026-10-02',
    ])
  })

  it('crosses a DST change without losing or duplicating a day', () => {
    // Central European DST ends on 25 October 2026; that day has 25 hours. Stepping by
    // calendar date rather than by adding 86_400_000 ms is what keeps this correct.
    expect(eachDate('2026-10-24', '2026-10-26')).toEqual([
      '2026-10-24',
      '2026-10-25',
      '2026-10-26',
    ])
  })

  it('returns nothing when the range is inverted', () => {
    expect(eachDate('2026-09-13', '2026-09-07')).toEqual([])
  })
})

describe('formatTime', () => {
  it('trims seconds', () => {
    expect(formatTime('17:00:00')).toBe('17:00')
  })
  it('leaves an already-short time alone', () => {
    expect(formatTime('17:00')).toBe('17:00')
  })
  it('renders nothing for an absent time', () => {
    expect(formatTime(null)).toBe('')
  })
})

describe('datetime-local conversion', () => {
  it('round-trips through the browser timezone', () => {
    const instant = localInputToInstant('2026-09-02T18:00')
    expect(instantToLocalInput(instant)).toBe('2026-09-02T18:00')
  })
})
