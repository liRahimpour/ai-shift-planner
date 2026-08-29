import { describe, expect, it } from 'vitest'
import type { Availability } from '@/api/types'
import { toDayStates, toWindowRequests } from './model'

const DATES = ['2026-09-07', '2026-09-08', '2026-09-09']

const availability = (over: Partial<Availability>): Availability => ({
  id: 'a',
  planningPeriodId: 'p',
  employeeId: 'e',
  date: '2026-09-07',
  availabilityType: 'AVAILABLE',
  startTime: null,
  endTime: null,
  ...over,
})

describe('toDayStates', () => {
  it('gives every date in the period an entry, even those the server knows nothing about', () => {
    const states = toDayStates(DATES, [])
    expect(Object.keys(states)).toEqual(DATES)
    expect(states['2026-09-08']).toEqual({ type: 'AVAILABLE', allDay: true, windows: [] })
  })

  it('treats an entry without times as a full day', () => {
    const states = toDayStates(DATES, [availability({ availabilityType: 'UNAVAILABLE' })])
    expect(states['2026-09-07']).toEqual({ type: 'UNAVAILABLE', allDay: true, windows: [] })
  })

  it('collects several windows on the same day', () => {
    const states = toDayStates(DATES, [
      availability({ id: '1', startTime: '10:00:00', endTime: '14:00:00' }),
      availability({ id: '2', startTime: '18:00:00', endTime: '23:00:00' }),
    ])
    expect(states['2026-09-07']).toEqual({
      type: 'AVAILABLE',
      allDay: false,
      windows: [
        { start: '10:00', end: '14:00' },
        { start: '18:00', end: '23:00' },
      ],
    })
  })
})

describe('toWindowRequests', () => {
  it('never sends time windows for an unavailable day', () => {
    // Regression guard: switching a day to UNAVAILABLE after entering times must not leave
    // those times behind, or a full-day block silently becomes a partial one.
    const requests = toWindowRequests(['2026-09-07'], {
      '2026-09-07': {
        type: 'UNAVAILABLE',
        allDay: false,
        windows: [{ start: '10:00', end: '14:00' }],
      },
    })
    expect(requests).toEqual([
      { date: '2026-09-07', availabilityType: 'UNAVAILABLE', startTime: null, endTime: null },
    ])
  })

  it('emits one entry per window for a partially available day', () => {
    const requests = toWindowRequests(['2026-09-07'], {
      '2026-09-07': {
        type: 'PREFERRED',
        allDay: false,
        windows: [
          { start: '10:00', end: '14:00' },
          { start: '18:00', end: '23:00' },
        ],
      },
    })
    expect(requests).toHaveLength(2)
    expect(requests[0]).toMatchObject({ availabilityType: 'PREFERRED', startTime: '10:00' })
    expect(requests[1]).toMatchObject({ startTime: '18:00', endTime: '23:00' })
  })

  it('falls back to a full day when a window is half-filled', () => {
    const requests = toWindowRequests(['2026-09-07'], {
      '2026-09-07': { type: 'AVAILABLE', allDay: false, windows: [{ start: '10:00', end: '' }] },
    })
    expect(requests).toEqual([
      { date: '2026-09-07', availabilityType: 'AVAILABLE', startTime: null, endTime: null },
    ])
  })

  it('covers every date so an untouched day is still submitted', () => {
    const requests = toWindowRequests(DATES, {})
    expect(requests.map((r) => r.date)).toEqual(DATES)
  })

  it('round-trips server state without changing its meaning', () => {
    const stored = [
      availability({ id: '1', startTime: '10:00:00', endTime: '14:00:00' }),
      availability({ id: '2', date: '2026-09-08', availabilityType: 'UNAVAILABLE' }),
    ]
    const requests = toWindowRequests(DATES, toDayStates(DATES, stored))

    expect(requests).toContainEqual({
      date: '2026-09-07',
      availabilityType: 'AVAILABLE',
      startTime: '10:00',
      endTime: '14:00',
    })
    expect(requests).toContainEqual({
      date: '2026-09-08',
      availabilityType: 'UNAVAILABLE',
      startTime: null,
      endTime: null,
    })
  })
})
