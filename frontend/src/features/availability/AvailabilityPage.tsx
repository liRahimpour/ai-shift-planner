import { useEffect, useMemo, useState } from 'react'
import {
  useMyAvailability,
  useMyComments,
  usePlanningPeriods,
  useSubmitAvailability,
  useSubmitComment,
} from '@/api/queries'
import type { AvailabilityType, IsoDate, UUID } from '@/api/types'
import {
  availabilityLabels,
  eachDate,
  formatDate,
  formatDateShort,
  formatInstant,
  formatRelative,
  formatTime,
  formatWeekday,
} from '@/lib/format'
import { useSelectedLocation } from '@/lib/useSelectedLocation'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorNotice,
  LoadingBlock,
  Notice,
  Select,
} from '@/ui/primitives'
import { emptyDay, toDayStates, toWindowRequests } from './model'
import type { DayState, TimeWindow } from './model'

/**
 * Employee self-service: when am I available, and what should the planner know?
 *
 * The editing model is deliberately one decision per day — available, wish, or not at all —
 * with time windows only where they mean something. That is how people actually think about
 * their week, and it keeps a full week's entry to a handful of clicks.
 */

export default function AvailabilityPage() {
  const { locationId, locations, setLocationId } = useSelectedLocation()
  const { data: periods, isLoading: periodsLoading } = usePlanningPeriods(locationId)

  const [periodId, setPeriodId] = useState<UUID | undefined>()
  const openPeriods = useMemo(
    () => (periods ?? []).filter((p) => p.status !== 'ARCHIVED'),
    [periods],
  )

  useEffect(() => {
    if (!periodId && openPeriods.length > 0) setPeriodId(openPeriods[0]?.id)
  }, [openPeriods, periodId])

  const period = openPeriods.find((p) => p.id === periodId)

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Meine Verfügbarkeit</h1>
          <p className="subtitle">
            Trage ein, wann du kannst. Wünsche werden berücksichtigt, harte Sperren immer
            eingehalten.
          </p>
        </div>
        <div className="row">
          {locations.length > 1 && (
            <Select
              value={locationId ?? ''}
              onChange={(e) => setLocationId(e.target.value)}
              aria-label="Standort"
            >
              {locations.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.name}
                </option>
              ))}
            </Select>
          )}
          {openPeriods.length > 0 && (
            <Select
              value={periodId ?? ''}
              onChange={(e) => setPeriodId(e.target.value)}
              aria-label="Planungszeitraum"
            >
              {openPeriods.map((p) => (
                <option key={p.id} value={p.id}>
                  {formatDate(p.startDate)} – {formatDate(p.endDate)}
                </option>
              ))}
            </Select>
          )}
        </div>
      </header>

      {periodsLoading && (
        <Card>
          <LoadingBlock />
        </Card>
      )}

      {!periodsLoading && openPeriods.length === 0 && (
        <Card>
          <EmptyState
            title="Kein offener Zeitraum"
            description="Sobald deine Schichtleitung einen Planungszeitraum anlegt, kannst du hier deine Zeiten eintragen."
          />
        </Card>
      )}

      {period && (
        <AvailabilityEditor
          periodId={period.id}
          startDate={period.startDate}
          endDate={period.endDate}
          // Matching the exact status rather than a prefix: a future status starting with
          // "OPEN_" must not silently unlock availability editing.
          locked={period.deadlinePassed || period.status !== 'OPEN_FOR_AVAILABILITY'}
          deadline={period.availabilityDeadline}
        />
      )}
    </div>
  )
}

function AvailabilityEditor({
  periodId,
  startDate,
  endDate,
  locked,
  deadline,
}: {
  periodId: UUID
  startDate: IsoDate
  endDate: IsoDate
  locked: boolean
  deadline: string
}) {
  const { data: existing, isLoading, error } = useMyAvailability(periodId)
  const submit = useSubmitAvailability(periodId)
  const [days, setDays] = useState<Record<IsoDate, DayState>>({})
  const [dirty, setDirty] = useState(false)
  const [saved, setSaved] = useState(false)

  const dates = useMemo(() => eachDate(startDate, endDate), [startDate, endDate])

  // Rebuild the editor state whenever the server's version changes. Local edits are
  // discarded on purpose: the server is the source of truth, and silently keeping a stale
  // local draft over a newer server state is how people lose work without noticing.
  useEffect(() => {
    if (!existing) return
    setDays(toDayStates(dates, existing))
    setDirty(false)
  }, [existing, dates])

  const update = (date: IsoDate, change: Partial<DayState>) => {
    setDays((prev) => ({ ...prev, [date]: { ...(prev[date] ?? emptyDay()), ...change } }))
    setDirty(true)
    setSaved(false)
  }

  const save = () => {
    submit.mutate(toWindowRequests(dates, days), {
      onSuccess: () => {
        setDirty(false)
        setSaved(true)
      },
    })
  }

  if (isLoading) {
    return (
      <Card>
        <LoadingBlock rows={5} />
      </Card>
    )
  }

  return (
    <div className="stack-lg">
      <ErrorNotice error={error} />

      {locked ? (
        <Notice tone="warning">
          Die Deadline war am {formatInstant(deadline)}. Deine Angaben sind gesperrt — für eine
          Änderung wende dich an deine Schichtleitung.
        </Notice>
      ) : (
        <Notice tone="info">
          Änderungen sind bis {formatInstant(deadline)} möglich ({formatRelative(deadline)}).
        </Notice>
      )}

      <div className="stack">
        {dates.map((date) => (
          <DayEditor
            key={date}
            date={date}
            state={days[date] ?? emptyDay()}
            locked={locked}
            onChange={(change) => update(date, change)}
          />
        ))}
      </div>

      <ErrorNotice error={submit.error} />

      {!locked && (
        <div className="row spread">
          <span className="small muted">
            {dirty ? 'Nicht gespeicherte Änderungen' : saved ? 'Gespeichert.' : 'Alles gespeichert.'}
          </span>
          <Button variant="primary" size="lg" loading={submit.isPending} onClick={save} disabled={!dirty}>
            Verfügbarkeit speichern
          </Button>
        </div>
      )}

      <CommentSection periodId={periodId} locked={locked} />
    </div>
  )
}

function DayEditor({
  date,
  state,
  locked,
  onChange,
}: {
  date: IsoDate
  state: DayState
  locked: boolean
  onChange: (change: Partial<DayState>) => void
}) {
  const setWindow = (index: number, patch: Partial<TimeWindow>) => {
    const windows = state.windows.map((w, i) => (i === index ? { ...w, ...patch } : w))
    onChange({ windows })
  }

  const showWindows = state.type !== 'UNAVAILABLE' && !state.allDay

  return (
    <div className={`day-card${state.type === 'UNAVAILABLE' ? ' is-unavailable' : ''}`}>
      <div className="day-head">
        <div>
          <span className="day-name">{formatWeekday(date)}</span>{' '}
          <span className="day-date">{formatDateShort(date)}</span>
        </div>
        <div className="segmented" role="group" aria-label={`Verfügbarkeit am ${formatDate(date)}`}>
          {(['AVAILABLE', 'PREFERRED', 'UNAVAILABLE'] as AvailabilityType[]).map((type) => (
            <button
              key={type}
              type="button"
              disabled={locked}
              data-variant={type.toLowerCase()}
              className={state.type === type ? 'is-on' : ''}
              aria-pressed={state.type === type}
              onClick={() => onChange({ type })}
            >
              {availabilityLabels[type]}
            </button>
          ))}
        </div>
      </div>

      {state.type !== 'UNAVAILABLE' && (
        <div className="row row-tight">
          <label className="row row-tight small muted" style={{ cursor: locked ? 'default' : 'pointer' }}>
            <input
              type="checkbox"
              disabled={locked}
              checked={state.allDay}
              onChange={(e) =>
                onChange({
                  allDay: e.target.checked,
                  windows: e.target.checked ? [] : [{ start: '10:00', end: '18:00' }],
                })
              }
            />
            Ganztägig
          </label>
          {!state.allDay && (
            <Button
              size="sm"
              variant="ghost"
              disabled={locked}
              onClick={() => onChange({ windows: [...state.windows, { start: '18:00', end: '23:00' }] })}
            >
              + Zeitfenster
            </Button>
          )}
        </div>
      )}

      {showWindows && (
        <div className="stack" style={{ gap: 'var(--space-2)' }}>
          {state.windows.map((w, i) => (
            <div className="window-row" key={i}>
              <input
                className="input input-sm"
                type="time"
                disabled={locked}
                value={w.start}
                onChange={(e) => setWindow(i, { start: e.target.value })}
                aria-label="Von"
              />
              <span className="subtle">–</span>
              <input
                className="input input-sm"
                type="time"
                disabled={locked}
                value={w.end}
                onChange={(e) => setWindow(i, { end: e.target.value })}
                aria-label="Bis"
              />
              <Button
                size="sm"
                variant="ghost"
                disabled={locked}
                aria-label="Zeitfenster entfernen"
                onClick={() => onChange({ windows: state.windows.filter((_, j) => j !== i) })}
              >
                ✕
              </Button>
            </div>
          ))}
          {state.windows.length === 0 && (
            <p className="hint">Ohne Zeitfenster gilt der ganze Tag.</p>
          )}
        </div>
      )}
    </div>
  )
}

/**
 * Free-text notes plus what the system made of them.
 *
 * Showing the interpretation back to the employee matters: it is the only way they find out
 * that "erst ab 17 Uhr" was understood, and it makes the confidence score meaningful instead
 * of an internal number nobody sees.
 */
function CommentSection({ periodId, locked }: { periodId: UUID; locked: boolean }) {
  const { data: comments, isLoading } = useMyComments(periodId)
  const submit = useSubmitComment(periodId)
  const [text, setText] = useState('')

  return (
    <Card>
      <div className="card-header">
        <h2>Anmerkungen</h2>
      </div>
      <p className="small muted" style={{ marginBottom: 'var(--space-3)' }}>
        Alles, was sich nicht in Zeitfenster pressen lässt. Zum Beispiel: „Samstag kann ich
        arbeiten, aber bitte erst ab 17 Uhr, weil ich vorher Uni habe.“
      </p>

      {!locked && (
        <form
          className="stack"
          onSubmit={(e) => {
            e.preventDefault()
            if (text.trim()) submit.mutate(text.trim(), { onSuccess: () => setText('') })
          }}
        >
          <textarea
            className="textarea"
            maxLength={2000}
            placeholder="Deine Anmerkung …"
            value={text}
            onChange={(e) => setText(e.target.value)}
          />
          <ErrorNotice error={submit.error} />
          <div className="row spread">
            <span className="hint">{text.length} / 2000</span>
            <Button type="submit" loading={submit.isPending} disabled={!text.trim()}>
              Anmerkung senden
            </Button>
          </div>
        </form>
      )}

      {isLoading && <LoadingBlock rows={2} />}

      {comments && comments.length > 0 && (
        <div className="stack" style={{ marginTop: 'var(--space-4)' }}>
          {comments.map((c) => (
            <div key={c.id} className="card card-tight">
              <p>{c.originalText}</p>
              <p className="hint" style={{ marginTop: 4 }}>
                {formatInstant(c.createdAt)}
              </p>
              {c.interpretations.length > 0 && (
                <div className="stack" style={{ gap: 6, marginTop: 'var(--space-3)' }}>
                  <span className="label">So wurde es verstanden</span>
                  {c.interpretations.map((i) => (
                    <div key={i.id} className="row row-tight small">
                      <Badge tone={i.reviewStatus === 'ACCEPTED' ? 'success' : 'default'}>
                        {i.reviewStatus === 'ACCEPTED' ? 'übernommen' : 'in Prüfung'}
                      </Badge>
                      <span>
                        {i.interpretedDate ? formatDate(i.interpretedDate) : 'ohne Datum'}
                        {i.availabilityType ? ` · ${availabilityLabels[i.availabilityType]}` : ''}
                        {i.preferredStartTime ? ` ab ${formatTime(i.preferredStartTime)}` : ''}
                        {i.preferredEndTime ? ` bis ${formatTime(i.preferredEndTime)}` : ''}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </Card>
  )
}
