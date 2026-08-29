import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { planningApi } from '@/api/endpoints'
import { qk, usePlanningPeriods } from '@/api/queries'
import type { PlanningPeriodStatus } from '@/api/types'
import {
  formatDate,
  formatInstant,
  formatRelative,
  localInputToInstant,
  periodStatusLabels,
} from '@/lib/format'
import { useSelectedLocation } from '@/lib/useSelectedLocation'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorNotice,
  Field,
  LoadingBlock,
  Select,
} from '@/ui/primitives'

export function statusTone(status: PlanningPeriodStatus) {
  switch (status) {
    case 'PUBLISHED':
      return 'success' as const
    case 'PLANNING':
    case 'DRAFT':
      return 'info' as const
    case 'READY_FOR_PLANNING':
      return 'accent' as const
    case 'ARCHIVED':
      return 'default' as const
    default:
      return 'warning' as const
  }
}

export default function PeriodsPage() {
  const { locations, locationId, setLocationId, isLoading: locationsLoading } =
    useSelectedLocation()
  const { data: periods, isLoading, error } = usePlanningPeriods(locationId)
  const [creating, setCreating] = useState(false)

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Planungszeiträume</h1>
          <p className="subtitle">
            Ein Zeitraum bündelt Verfügbarkeiten, Personalbedarf und die daraus erzeugten Pläne.
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
          <Button variant="primary" onClick={() => setCreating((v) => !v)}>
            {creating ? 'Abbrechen' : 'Neuer Zeitraum'}
          </Button>
        </div>
      </header>

      {creating && locationId && (
        <CreatePeriodForm locationId={locationId} onDone={() => setCreating(false)} />
      )}

      <ErrorNotice error={error} />

      {(isLoading || locationsLoading) && (
        <Card>
          <LoadingBlock />
        </Card>
      )}

      {!isLoading && periods?.length === 0 && (
        <Card>
          <EmptyState
            title="Noch kein Planungszeitraum"
            description="Lege den ersten Zeitraum an — danach können Mitarbeitende ihre Verfügbarkeiten eintragen."
            action={
              <Button variant="primary" onClick={() => setCreating(true)}>
                Zeitraum anlegen
              </Button>
            }
          />
        </Card>
      )}

      {periods && periods.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Zeitraum</th>
                <th>Status</th>
                <th>Deadline</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {periods.map((p) => (
                <tr key={p.id}>
                  <td>
                    <Link to={`/periods/${p.id}`} style={{ fontWeight: 550 }}>
                      {formatDate(p.startDate)} – {formatDate(p.endDate)}
                    </Link>
                  </td>
                  <td>
                    <Badge tone={statusTone(p.status)}>{periodStatusLabels[p.status]}</Badge>
                  </td>
                  <td className="num">
                    {formatInstant(p.availabilityDeadline)}
                    <span className="subtle small"> · {formatRelative(p.availabilityDeadline)}</span>
                  </td>
                  <td style={{ textAlign: 'right' }}>
                    <Link className="btn btn-sm" to={`/periods/${p.id}`}>
                      Öffnen
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function CreatePeriodForm({ locationId, onDone }: { locationId: string; onDone: () => void }) {
  const qc = useQueryClient()
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [deadline, setDeadline] = useState('')

  const create = useMutation({
    mutationFn: () =>
      planningApi.create({
        locationId,
        startDate,
        endDate,
        availabilityDeadline: localInputToInstant(deadline),
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: qk.periods(locationId) })
      onDone()
    },
  })

  return (
    <Card>
      <form
        className="stack"
        onSubmit={(e) => {
          e.preventDefault()
          create.mutate()
        }}
      >
        <div className="row" style={{ alignItems: 'flex-end' }}>
          <Field label="Beginn" htmlFor="start">
            <input
              id="start"
              className="input"
              type="date"
              required
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
            />
          </Field>
          <Field label="Ende" htmlFor="end">
            <input
              id="end"
              className="input"
              type="date"
              required
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
            />
          </Field>
          <Field
            label="Deadline für Verfügbarkeiten"
            htmlFor="deadline"
            hint="Danach sind Eingaben gesperrt — bis eine Managerin sie wieder öffnet."
          >
            <input
              id="deadline"
              className="input"
              type="datetime-local"
              required
              value={deadline}
              onChange={(e) => setDeadline(e.target.value)}
            />
          </Field>
          <Button type="submit" variant="primary" loading={create.isPending}>
            Anlegen
          </Button>
        </div>
        <ErrorNotice error={create.error} />
      </form>
    </Card>
  )
}
