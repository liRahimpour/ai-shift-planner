import type {
  AvailabilityType,
  EmploymentType,
  IsoDate,
  IsoInstant,
  IsoTime,
  PlanningJobStatus,
  PlanningPeriodStatus,
  PlanningStrategy,
  ScheduleStatus,
} from '@/api/types'

/**
 * Display formatting and German labels for domain enums.
 *
 * Kept in one module so that adding a language later means adding a second lookup table,
 * not hunting for hard-coded German strings inside components. The API always speaks
 * enum constants; only this file decides what a human reads.
 */

const dateFmt = new Intl.DateTimeFormat('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
const dateShortFmt = new Intl.DateTimeFormat('de-DE', { day: '2-digit', month: '2-digit' })
const weekdayFmt = new Intl.DateTimeFormat('de-DE', { weekday: 'long' })
const weekdayShortFmt = new Intl.DateTimeFormat('de-DE', { weekday: 'short' })
const dateTimeFmt = new Intl.DateTimeFormat('de-DE', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})
const currencyFmt = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

/** Parses "2026-09-12" without the timezone shift `new Date(string)` would apply. */
export function parseIsoDate(value: IsoDate): Date {
  const [y, m, d] = value.split('-').map(Number)
  return new Date(y ?? 1970, (m ?? 1) - 1, d ?? 1)
}

export const formatDate = (value: IsoDate): string => dateFmt.format(parseIsoDate(value))
export const formatDateShort = (value: IsoDate): string => dateShortFmt.format(parseIsoDate(value))
export const formatWeekday = (value: IsoDate): string => weekdayFmt.format(parseIsoDate(value))
export const formatWeekdayShort = (value: IsoDate): string =>
  weekdayShortFmt.format(parseIsoDate(value))

export const formatInstant = (value: IsoInstant): string => dateTimeFmt.format(new Date(value))

/** "17:00:00" and "17:00" both render as "17:00". */
export const formatTime = (value: IsoTime | null): string => (value ? value.slice(0, 5) : '')

export const formatCurrency = (value: number): string => currencyFmt.format(value)

export const formatHours = (value: number): string =>
  `${new Intl.NumberFormat('de-DE', { maximumFractionDigits: 1 }).format(value)} h`

export const formatPercent = (fraction: number, digits = 0): string =>
  new Intl.NumberFormat('de-DE', {
    style: 'percent',
    maximumFractionDigits: digits,
  }).format(fraction)

/** Human-friendly countdown, e.g. "in 2 Tagen" or "vor 3 Stunden". */
export function formatRelative(value: IsoInstant): string {
  const target = new Date(value).getTime()
  const diffMs = target - Date.now()
  const rtf = new Intl.RelativeTimeFormat('de-DE', { numeric: 'auto' })
  const units: [Intl.RelativeTimeFormatUnit, number][] = [
    ['day', 86_400_000],
    ['hour', 3_600_000],
    ['minute', 60_000],
  ]
  for (const [unit, ms] of units) {
    if (Math.abs(diffMs) >= ms || unit === 'minute') {
      return rtf.format(Math.round(diffMs / ms), unit)
    }
  }
  return rtf.format(0, 'minute')
}

/** Every date from start to end inclusive, as ISO strings. */
export function eachDate(start: IsoDate, end: IsoDate): IsoDate[] {
  const dates: IsoDate[] = []
  const cursor = parseIsoDate(start)
  const last = parseIsoDate(end)
  while (cursor <= last) {
    dates.push(toIsoDate(cursor))
    cursor.setDate(cursor.getDate() + 1)
  }
  return dates
}

export function toIsoDate(date: Date): IsoDate {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/** Converts a `datetime-local` value ("2026-09-02T18:00") to a UTC instant. */
export const localInputToInstant = (value: string): IsoInstant => new Date(value).toISOString()

/** Converts a UTC instant to the `datetime-local` value for the browser's timezone. */
export function instantToLocalInput(value: IsoInstant): string {
  const d = new Date(value)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(
    d.getMinutes(),
  )}`
}

// --- enum labels --------------------------------------------------------------

export const availabilityLabels: Record<AvailabilityType, string> = {
  AVAILABLE: 'Verfügbar',
  PREFERRED: 'Wunschzeit',
  UNAVAILABLE: 'Nicht verfügbar',
}

export const periodStatusLabels: Record<PlanningPeriodStatus, string> = {
  OPEN_FOR_AVAILABILITY: 'Verfügbarkeiten offen',
  READY_FOR_PLANNING: 'Bereit zur Planung',
  PLANNING: 'Planung läuft',
  DRAFT: 'Entwurf',
  PUBLISHED: 'Veröffentlicht',
  ARCHIVED: 'Archiviert',
}

export const scheduleStatusLabels: Record<ScheduleStatus, string> = {
  DRAFT: 'Entwurf',
  PLANNED: 'Geplant',
  PUBLISHED: 'Veröffentlicht',
  ARCHIVED: 'Archiviert',
}

export const strategyLabels: Record<PlanningStrategy, string> = {
  FAIR: 'Fair',
  COST_OPTIMIZED: 'Kostenoptimiert',
  BALANCED: 'Ausgewogen',
  MANUAL: 'Manuell',
}

export const strategyDescriptions: Record<PlanningStrategy, string> = {
  FAIR: 'Gleichmäßige Verteilung von Wochenenden, Abend- und Closing-Schichten. Wünsche und Vertragsstunden haben hohes Gewicht.',
  COST_OPTIMIZED:
    'Minimiert Personalkosten, Überstunden und unnötige Überbesetzung — ohne harte Regeln zu verletzen.',
  BALANCED: 'Kombiniert Kosten, Fairness, Wünsche und Besetzung. Die übliche Empfehlung.',
  MANUAL: 'Manuell bearbeiteter Plan.',
}

export const jobStatusLabels: Record<PlanningJobStatus, string> = {
  QUEUED: 'In Warteschlange',
  RUNNING: 'Berechnung läuft',
  COMPLETED: 'Fertig',
  FAILED: 'Fehlgeschlagen',
  CANCELLED: 'Abgebrochen',
}

export const employmentTypeLabels: Record<EmploymentType, string> = {
  FULL_TIME: 'Vollzeit',
  PART_TIME: 'Teilzeit',
  MINIJOB: 'Minijob',
  WORKING_STUDENT: 'Werkstudent',
  TEMPORARY: 'Aushilfe',
  OTHER: 'Sonstige',
}

/** Maps a validation issue code to something a shift manager can act on. */
export const validationCodeLabels: Record<string, string> = {
  REST_TIME: 'Ruhezeit',
  AVAILABILITY: 'Verfügbarkeit',
  SKILL: 'Qualifikation',
  OVERLAP: 'Überschneidung',
  MAX_HOURS: 'Maximale Arbeitszeit',
  UNDERSTAFFED: 'Unterbesetzung',
  LOCATION: 'Standort',
}
