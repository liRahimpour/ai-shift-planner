import type { ShiftWithAssignments, UUID, ValidationIssue } from '@/api/types'

/** Pure helpers behind the schedule screens, kept testable and free of React. */

/** Shifts grouped by date, days ascending and shifts within a day by start time. */
export function groupByDate(shifts: ShiftWithAssignments[]): [string, ShiftWithAssignments[]][] {
  const map = new Map<string, ShiftWithAssignments[]>()
  for (const shift of shifts) {
    const list = map.get(shift.date) ?? []
    list.push(shift)
    map.set(shift.date, list)
  }
  return [...map.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, list]) => [date, [...list].sort((a, b) => a.startTime.localeCompare(b.startTime))])
}

/**
 * Length of a shift in hours.
 *
 * A shift ending at or before its start time runs past midnight (18:00–02:00 is eight hours,
 * not minus sixteen). Getting this wrong would understate a bar team's hours on exactly the
 * shifts that matter most to them.
 */
export function shiftHours(shift: ShiftWithAssignments): number {
  const [sh = 0, sm = 0] = shift.startTime.split(':').map(Number)
  const [eh = 0, em = 0] = shift.endTime.split(':').map(Number)
  let minutes = eh * 60 + em - (sh * 60 + sm)
  if (minutes <= 0) minutes += 24 * 60
  return minutes / 60
}

export const totalHours = (shifts: ShiftWithAssignments[]): number =>
  shifts.reduce((sum, shift) => sum + shiftHours(shift), 0)

/** Validation issues indexed by shift, so each shift card can show its own problems. */
export function indexIssues(issues: ValidationIssue[]): Map<UUID, ValidationIssue[]> {
  const map = new Map<UUID, ValidationIssue[]>()
  for (const issue of issues) {
    if (!issue.shiftId) continue
    const list = map.get(issue.shiftId) ?? []
    list.push(issue)
    map.set(issue.shiftId, list)
  }
  return map
}
