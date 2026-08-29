import { useMemo, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/api/client'
import { qk, useDepartments, useEmployees, useSkills } from '@/api/queries'
import type { Employee, EmploymentType, UUID } from '@/api/types'
import { employmentTypeLabels, formatCurrency, formatHours } from '@/lib/format'
import { useSelectedLocation } from '@/lib/useSelectedLocation'
import { useAuth } from '@/features/auth/AuthContext'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorNotice,
  Field,
  LoadingBlock,
  Modal,
  Select,
} from '@/ui/primitives'

/**
 * The team: who works here, on what terms, with which qualifications.
 *
 * Skills and departments are shown as their names rather than ids because this table is
 * what a manager scans when they are trying to answer "who could even do this shift" — the
 * same question the solver answers formally.
 */
export default function EmployeesPage() {
  const { hasRole } = useAuth()
  const { locationId, locations, setLocationId } = useSelectedLocation()
  const { data: employees, isLoading, error } = useEmployees()
  const { data: skills } = useSkills()
  const { data: departments } = useDepartments(locationId)
  const [editing, setEditing] = useState<Employee | 'new' | null>(null)

  const canEdit = hasRole('ORG_ADMIN', 'LOCATION_MANAGER', 'SUPER_ADMIN')

  const visible = useMemo(
    () => (employees ?? []).filter((e) => !locationId || e.locationId === locationId),
    [employees, locationId],
  )

  const skillName = (id: UUID) => skills?.find((s) => s.id === id)?.code ?? '?'
  const departmentName = (id: UUID) => departments?.find((d) => d.id === id)?.name ?? '?'

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Mitarbeitende</h1>
          <p className="subtitle">
            Qualifikationen und Vertragsstunden bestimmen, wer eingeplant werden kann.
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
          {canEdit && (
            <Button variant="primary" onClick={() => setEditing('new')}>
              Neue Person
            </Button>
          )}
        </div>
      </header>

      <ErrorNotice error={error} />
      {isLoading && (
        <Card>
          <LoadingBlock rows={5} />
        </Card>
      )}

      {!isLoading && visible.length === 0 && (
        <Card>
          <EmptyState title="Noch niemand angelegt" description="Lege die erste Person an." />
        </Card>
      )}

      {visible.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Beschäftigung</th>
                <th className="num">Vertrag</th>
                <th className="num">Spanne</th>
                <th className="num">Lohn</th>
                <th>Abteilungen</th>
                <th>Qualifikationen</th>
                {canEdit && <th />}
              </tr>
            </thead>
            <tbody>
              {visible.map((e) => (
                <tr key={e.id} style={e.active ? undefined : { opacity: 0.55 }}>
                  <td>
                    <div style={{ fontWeight: 550 }}>
                      {e.firstName} {e.lastName}
                    </div>
                    <div className="subtle small">{e.email ?? '—'}</div>
                  </td>
                  <td>
                    {employmentTypeLabels[e.employmentType]}
                    {!e.active && <Badge tone="danger">inaktiv</Badge>}
                  </td>
                  <td className="num">{formatHours(e.contractHoursPerWeek)}</td>
                  <td className="num subtle">
                    {formatHours(e.minimumHoursPerWeek)} – {formatHours(e.maximumHoursPerWeek)}
                  </td>
                  <td className="num">{formatCurrency(e.hourlyWage)}</td>
                  <td>
                    <div className="row row-tight">
                      {e.departmentIds.map((id) => (
                        <Badge key={id}>{departmentName(id)}</Badge>
                      ))}
                    </div>
                  </td>
                  <td>
                    <div className="row row-tight">
                      {e.skillIds.map((id) => (
                        <Badge key={id} tone="accent">
                          {skillName(id)}
                        </Badge>
                      ))}
                    </div>
                  </td>
                  {canEdit && (
                    <td style={{ textAlign: 'right' }}>
                      <Button size="sm" onClick={() => setEditing(e)}>
                        Bearbeiten
                      </Button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editing && locationId && (
        <EmployeeDialog
          employee={editing === 'new' ? null : editing}
          locationId={locationId}
          onClose={() => setEditing(null)}
        />
      )}
    </div>
  )
}

const EMPLOYMENT_TYPES: EmploymentType[] = [
  'FULL_TIME',
  'PART_TIME',
  'MINIJOB',
  'WORKING_STUDENT',
  'TEMPORARY',
  'OTHER',
]

interface FormState {
  firstName: string
  lastName: string
  email: string
  employmentType: EmploymentType
  hourlyWage: number
  contractHoursPerWeek: number
  minimumHoursPerWeek: number
  maximumHoursPerWeek: number
  skillIds: UUID[]
  departmentIds: UUID[]
  active: boolean
}

function EmployeeDialog({
  employee,
  locationId,
  onClose,
}: {
  employee: Employee | null
  locationId: UUID
  onClose: () => void
}) {
  const qc = useQueryClient()
  const { data: skills } = useSkills()
  const { data: departments } = useDepartments(locationId)

  const [form, setForm] = useState<FormState>({
    firstName: employee?.firstName ?? '',
    lastName: employee?.lastName ?? '',
    email: employee?.email ?? '',
    employmentType: employee?.employmentType ?? 'PART_TIME',
    hourlyWage: employee?.hourlyWage ?? 14,
    contractHoursPerWeek: employee?.contractHoursPerWeek ?? 20,
    minimumHoursPerWeek: employee?.minimumHoursPerWeek ?? 0,
    maximumHoursPerWeek: employee?.maximumHoursPerWeek ?? 40,
    skillIds: employee?.skillIds ?? [],
    departmentIds: employee?.departmentIds ?? [],
    active: employee?.active ?? true,
  })

  const patch = (change: Partial<FormState>) => setForm((prev) => ({ ...prev, ...change }))

  const toggle = (list: UUID[], id: UUID) =>
    list.includes(id) ? list.filter((x) => x !== id) : [...list, id]

  const save = useMutation({
    mutationFn: () => {
      const body = {
        locationId,
        firstName: form.firstName,
        lastName: form.lastName,
        email: form.email || null,
        employmentType: form.employmentType,
        hourlyWage: form.hourlyWage,
        contractHoursPerWeek: form.contractHoursPerWeek,
        minimumHoursPerWeek: form.minimumHoursPerWeek,
        maximumHoursPerWeek: form.maximumHoursPerWeek,
        skillIds: form.skillIds,
        departmentIds: form.departmentIds,
        additionalLocationIds: employee?.additionalLocationIds ?? [],
      }
      return employee
        ? api.put<Employee>(`/api/v1/employees/${employee.id}`, { ...body, active: form.active })
        : api.post<Employee>('/api/v1/employees', body)
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: qk.employees })
      onClose()
    },
  })

  return (
    <Modal
      title={employee ? `${employee.firstName} ${employee.lastName} bearbeiten` : 'Neue Person'}
      onClose={onClose}
      footer={
        <div className="row row-tight" style={{ marginLeft: 'auto' }}>
          <Button variant="ghost" onClick={onClose}>
            Abbrechen
          </Button>
          <Button variant="primary" loading={save.isPending} onClick={() => save.mutate()}>
            Speichern
          </Button>
        </div>
      }
    >
      <div className="stack">
        <div className="row">
          <Field label="Vorname" htmlFor="fn">
            <input
              id="fn"
              className="input"
              value={form.firstName}
              onChange={(e) => patch({ firstName: e.target.value })}
            />
          </Field>
          <Field label="Nachname" htmlFor="ln">
            <input
              id="ln"
              className="input"
              value={form.lastName}
              onChange={(e) => patch({ lastName: e.target.value })}
            />
          </Field>
        </div>

        <Field label="E-Mail" htmlFor="mail">
          <input
            id="mail"
            className="input"
            type="email"
            value={form.email}
            onChange={(e) => patch({ email: e.target.value })}
          />
        </Field>

        <div className="row">
          <Field label="Beschäftigungsart" htmlFor="etype">
            <Select
              id="etype"
              value={form.employmentType}
              onChange={(e) => patch({ employmentType: e.target.value as EmploymentType })}
            >
              {EMPLOYMENT_TYPES.map((t) => (
                <option key={t} value={t}>
                  {employmentTypeLabels[t]}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Stundenlohn (€)" htmlFor="wage">
            <input
              id="wage"
              className="input"
              type="number"
              step="0.01"
              min={0}
              value={form.hourlyWage}
              onChange={(e) => patch({ hourlyWage: Number(e.target.value) })}
            />
          </Field>
        </div>

        <div className="row">
          <Field label="Vertragsstunden / Woche" htmlFor="contract">
            <input
              id="contract"
              className="input"
              type="number"
              min={0}
              value={form.contractHoursPerWeek}
              onChange={(e) => patch({ contractHoursPerWeek: Number(e.target.value) })}
            />
          </Field>
          <Field label="Minimum" htmlFor="minh">
            <input
              id="minh"
              className="input"
              type="number"
              min={0}
              value={form.minimumHoursPerWeek}
              onChange={(e) => patch({ minimumHoursPerWeek: Number(e.target.value) })}
            />
          </Field>
          <Field label="Maximum" htmlFor="maxh">
            <input
              id="maxh"
              className="input"
              type="number"
              min={0}
              value={form.maximumHoursPerWeek}
              onChange={(e) => patch({ maximumHoursPerWeek: Number(e.target.value) })}
            />
          </Field>
        </div>

        <div className="field">
          <span className="label">Abteilungen</span>
          <div className="row row-tight">
            {departments?.map((d) => (
              <button
                key={d.id}
                type="button"
                className="chip"
                style={
                  form.departmentIds.includes(d.id)
                    ? { background: 'var(--accent-soft)', color: 'var(--accent)' }
                    : undefined
                }
                onClick={() => patch({ departmentIds: toggle(form.departmentIds, d.id) })}
              >
                {d.name}
              </button>
            ))}
          </div>
        </div>

        <div className="field">
          <span className="label">Qualifikationen</span>
          <div className="row row-tight">
            {skills?.map((s) => (
              <button
                key={s.id}
                type="button"
                className="chip"
                title={s.name}
                style={
                  form.skillIds.includes(s.id)
                    ? { background: 'var(--accent-soft)', color: 'var(--accent)' }
                    : undefined
                }
                onClick={() => patch({ skillIds: toggle(form.skillIds, s.id) })}
              >
                {s.code}
              </button>
            ))}
          </div>
        </div>

        {employee && (
          <label className="row row-tight small" style={{ cursor: 'pointer' }}>
            <input
              type="checkbox"
              checked={form.active}
              onChange={(e) => patch({ active: e.target.checked })}
            />
            Aktiv (wird eingeplant)
          </label>
        )}

        <ErrorNotice error={save.error} />
      </div>
    </Modal>
  )
}
