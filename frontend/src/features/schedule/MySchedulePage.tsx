import { useEffect, useMemo, useState } from 'react'
import { ApiError } from '@/api/client'
import { useMySchedule, usePlanningPeriods } from '@/api/queries'
import type { UUID } from '@/api/types'
import { formatDate, formatTime, formatWeekday } from '@/lib/format'
import { useSelectedLocation } from '@/lib/useSelectedLocation'
import { Badge, Card, EmptyState, ErrorNotice, LoadingBlock, Select } from '@/ui/primitives'
import { groupByDate, totalHours as sumHours } from './model'

/**
 * What an employee actually cares about: the shifts they have been given, in date order.
 *
 * Only published plans reach this screen — the backend does not expose draft assignments to
 * employees, so nobody plans their week around a proposal that is still being edited.
 */
export default function MySchedulePage() {
  const { locationId, locations, setLocationId } = useSelectedLocation()
  const { data: periods } = usePlanningPeriods(locationId)
  const [periodId, setPeriodId] = useState<UUID | undefined>()

  const selectable = useMemo(
    () => (periods ?? []).filter((p) => p.status === 'PUBLISHED' || p.status === 'ARCHIVED'),
    [periods],
  )

  useEffect(() => {
    if (!periodId && selectable.length > 0) setPeriodId(selectable[0]?.id)
  }, [selectable, periodId])

  const { data, isLoading, error } = useMySchedule(periodId)

  const byDate = useMemo(() => groupByDate(data?.shifts ?? []), [data])
  const totalHours = useMemo(() => sumHours(data?.shifts ?? []), [data])

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Mein Dienstplan</h1>
          <p className="subtitle">Deine veröffentlichten Schichten.</p>
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
          {selectable.length > 0 && (
            <Select
              value={periodId ?? ''}
              onChange={(e) => setPeriodId(e.target.value)}
              aria-label="Planungszeitraum"
            >
              {selectable.map((p) => (
                <option key={p.id} value={p.id}>
                  {formatDate(p.startDate)} – {formatDate(p.endDate)}
                </option>
              ))}
            </Select>
          )}
        </div>
      </header>

      {selectable.length === 0 && (
        <Card>
          <EmptyState
            title="Noch kein veröffentlichter Plan"
            description="Sobald deine Schichtleitung einen Plan veröffentlicht, erscheint er hier."
          />
        </Card>
      )}

      {isLoading && (
        <Card>
          <LoadingBlock rows={4} />
        </Card>
      )}

      {error instanceof ApiError && error.code === 'NOT_FOUND' ? (
        <Card>
          <EmptyState
            title="Kein Plan für diesen Zeitraum"
            description="Für den gewählten Zeitraum gibt es noch keinen veröffentlichten Plan mit Schichten für dich."
          />
        </Card>
      ) : (
        <ErrorNotice error={error} />
      )}

      {data && data.shifts.length > 0 && (
        <>
          <div className="tiles">
            <Card tight>
              <span className="tile-label">Schichten</span>
              <div className="tile-value">{data.shifts.length}</div>
            </Card>
            <Card tight>
              <span className="tile-label">Stunden gesamt</span>
              <div className="tile-value">
                {new Intl.NumberFormat('de-DE', { maximumFractionDigits: 1 }).format(totalHours)}
              </div>
            </Card>
          </div>

          <div className="stack-lg">
            {byDate.map(([date, shifts]) => (
              <div className="day-group" key={date}>
                <div className="day-heading">
                  <h2>{formatWeekday(date)}</h2>
                  <span className="muted">{formatDate(date)}</span>
                </div>
                {shifts.map((shift) => (
                  <Card key={shift.shiftId} tight>
                    <div className="row spread">
                      <div className="row">
                        <span className="shift-time">
                          {formatTime(shift.startTime)} – {formatTime(shift.endTime)}
                        </span>
                        <Badge tone="accent">{shift.departmentName}</Badge>
                        {shift.crossesMidnight && <Badge>über Mitternacht</Badge>}
                      </div>
                      <span className="small muted">
                        mit{' '}
                        {shift.assignments
                          .map((a) => a.employeeName)
                          .filter(Boolean)
                          .join(', ') || '—'}
                      </span>
                    </div>
                  </Card>
                ))}
              </div>
            ))}
          </div>
        </>
      )}

      {data && data.shifts.length === 0 && (
        <Card>
          <EmptyState
            title="Keine Schichten"
            description="In diesem Zeitraum bist du nicht eingeplant."
          />
        </Card>
      )}
    </div>
  )
}

