import type { Availability, AvailabilityType, AvailabilityWindowRequest, IsoDate } from '@/api/types'
import { formatTime } from '@/lib/format'

/**
 * The translation between what an employee sees (one decision per day) and what the API
 * stores (a flat list of windows).
 *
 * This lives outside the component because it carries the rules that are easy to get wrong
 * and expensive to get wrong quietly — an "all day" that silently becomes 00:00–00:00, or an
 * UNAVAILABLE day that keeps stale time windows and stops being a full-day block.
 */

export interface TimeWindow {
  start: string
  end: string
}

export interface DayState {
  type: AvailabilityType
  allDay: boolean
  windows: TimeWindow[]
}

export const emptyDay = (): DayState => ({ type: 'AVAILABLE', allDay: true, windows: [] })

/** Builds editor state for every date in the period from what the server has stored. */
export function toDayStates(dates: IsoDate[], existing: Availability[]): Record<IsoDate, DayState> {
  const result: Record<IsoDate, DayState> = {}
  for (const date of dates) result[date] = emptyDay()

  for (const entry of existing) {
    const day = result[entry.date] ?? emptyDay()
    day.type = entry.availabilityType
    if (entry.startTime && entry.endTime) {
      // The first timed entry flips the day out of "all day"; further ones just add windows.
      if (day.allDay) {
        day.allDay = false
        day.windows = []
      }
      day.windows.push({ start: formatTime(entry.startTime), end: formatTime(entry.endTime) })
    }
    result[entry.date] = day
  }
  return result
}

/**
 * Flattens editor state back into the request payload.
 *
 * Rules, in order of precedence:
 * 1. UNAVAILABLE is always a single full-day block — a day someone cannot work is not
 *    partially bookable, and leftover windows from an earlier choice must not survive.
 * 2. "All day" (or a day with no usable window) is one entry with no times.
 * 3. Otherwise one entry per window, skipping incomplete ones.
 */
export function toWindowRequests(
  dates: IsoDate[],
  days: Record<IsoDate, DayState>,
): AvailabilityWindowRequest[] {
  const requests: AvailabilityWindowRequest[] = []

  for (const date of dates) {
    const day = days[date] ?? emptyDay()
    const usable = day.windows.filter((w) => w.start && w.end)

    if (day.type === 'UNAVAILABLE' || day.allDay || usable.length === 0) {
      requests.push({ date, availabilityType: day.type, startTime: null, endTime: null })
      continue
    }

    for (const window of usable) {
      requests.push({
        date,
        availabilityType: day.type,
        startTime: window.start,
        endTime: window.end,
      })
    }
  }

  return requests
}
