import { useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  useCreateStaffingRequirement,
  useDeleteStaffingRequirement,
  useDepartments,
  useGenerateShifts,
  usePeriodSummary,
  useSkills,
  useStaffingRequirements,
} from '@/api/queries'
import type { CreateStaffingRequirementRequest, UUID } from '@/api/types'
import { eachDate, formatDate, formatDateShort, formatTime, formatWeekdayShort } from '@/lib/format'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorNotice,
  Field,
  LoadingBlock,
  Notice,
  Select,
} from '@/ui/primitives'

/**
 * How many people are needed where, and when.
 *
 * This is the input the solver optimizes against, so it comes before generating anything.
 * Requirements are turned into concrete shifts by an explicit action rather than implicitly,
 * so a manager can build the whole week's needs first and only then commit.
 */
export default function StaffingPage() {
  const { periodId } = useParams<{ periodId: string }>()
  const { data: summary } = usePeriodSummary(periodId)
  const { data: requirements, isLoading, error } = useStaffingRequirements(periodId)
  const { data: departments } = useDepartments(summary?.period.locationId)
  const generate = useGenerateShifts(periodId)
  const remove = useDeleteStaffingRequirement(periodId)

  const departmentName = (id: UUID) => departments?.find((d) => d.id === id)?.name ?? '—'

  const byDate = useMemo(() => {
    const map = new Map<string, typeof requirements>()
    for (const r of requirements ?? []) {
      const list = map.get(r.date) ?? []
      list.push(r)
      map.set(r.date, list)
    }
    return [...map.entries()].sort(([a], [b]) => a.localeCompare(b))
  }, [requirements])

  return (
    <div className="stack-lg">
      {summary && periodId && (
        <RequirementForm
          periodId={periodId}
          locationId={summary.period.locationId}
          startDate={summary.period.startDate}
          endDate={summary.period.endDate}
        />
      )}

      <Card>
        <div className="card-header">
          <h2>Schichten erzeugen</h2>
          {generate.data && (
            <Badge tone="success">
              {generate.data.shiftsCreated} Schichten aus {generate.data.requirementsProcessed}{' '}
              Bedarfen
            </Badge>
          )}
        </div>
        <p className="small muted">
          Erzeugt aus jedem Personalbedarf die konkreten Schichten mit ihren Plätzen. Erst danach
          kann geplant werden.
        </p>
        <ErrorNotice error={generate.error} />
        <div style={{ marginTop: 'var(--space-4)' }}>
          <Button
            variant="primary"
            loading={generate.isPending}
            disabled={(requirements?.length ?? 0) === 0}
            onClick={() => generate.mutate()}
          >
            Schichten erzeugen
          </Button>
        </div>
      </Card>

      <ErrorNotice error={error} />
      {isLoading && (
        <Card>
          <LoadingBlock rows={3} />
        </Card>
      )}

      {requirements?.length === 0 && (
        <Card>
          <EmptyState
            title="Noch kein Personalbedarf"
            description="Lege oben fest, wie viele Personen wann in welcher Abteilung gebraucht werden."
          />
        </Card>
      )}

      {byDate.map(([date, list]) => (
        <div className="day-group" key={date}>
          <div className="day-heading">
            <h2>
              {formatWeekdayShort(date)} {formatDateShort(date)}
            </h2>
            <span className="muted small">{list?.length} Bedarfe</span>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Abteilung</th>
                  <th>Zeit</th>
                  <th className="num">Min.</th>
                  <th className="num">Soll</th>
                  <th className="num">Max.</th>
                  <th>Qualifikationen</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {list?.map((r) => (
                  <tr key={r.id}>
                    <td>{departmentName(r.departmentId)}</td>
                    <td className="num">
                      {formatTime(r.startTime)} – {formatTime(r.endTime)}
                      {r.crossesMidnight && <Badge>+1 Tag</Badge>}
                    </td>
                    <td className="num">{r.minimumStaff}</td>
                    <td className="num">{r.preferredStaff}</td>
                    <td className="num">{r.maximumStaff}</td>
                    <td>
                      <RequiredSkills skills={r.requiredSkills} />
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <Button
                        size="sm"
                        variant="danger"
                        onClick={() => remove.mutate(r.id)}
                        loading={remove.isPending && remove.variables === r.id}
                      >
                        Löschen
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ))}
    </div>
  )
}

function RequiredSkills({ skills }: { skills: Record<UUID, number> }) {
  const { data: catalogue } = useSkills()
  const entries = Object.entries(skills ?? {})
  if (entries.length === 0) return <span className="subtle small">—</span>
  return (
    <div className="row row-tight">
      {entries.map(([skillId, count]) => (
        <Badge key={skillId} tone="accent">
          {count}× {catalogue?.find((s) => s.id === skillId)?.code ?? 'Skill'}
        </Badge>
      ))}
    </div>
  )
}

function RequirementForm({
  periodId,
  locationId,
  startDate,
  endDate,
}: {
  periodId: string
  locationId: UUID
  startDate: string
  endDate: string
}) {
  const { data: departments } = useDepartments(locationId)
  const { data: skills } = useSkills()
  const create = useCreateStaffingRequirement(periodId)
  const dates = useMemo(() => eachDate(startDate, endDate), [startDate, endDate])

  const [form, setForm] = useState<CreateStaffingRequirementRequest>({
    departmentId: '',
    date: dates[0] ?? '',
    startTime: '10:00',
    endTime: '18:00',
    crossesMidnight: false,
    minimumStaff: 2,
    preferredStaff: 3,
    maximumStaff: 4,
    requiredSkills: {},
  })

  const patch = (change: Partial<CreateStaffingRequirementRequest>) =>
    setForm((prev) => ({ ...prev, ...change }))

  const toggleSkill = (skillId: UUID, count: number) => {
    const next = { ...form.requiredSkills }
    if (count <= 0) delete next[skillId]
    else next[skillId] = count
    patch({ requiredSkills: next })
  }

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!form.departmentId) return
    create.mutate(form)
  }

  return (
    <Card>
      <div className="card-header">
        <h2>Personalbedarf hinzufügen</h2>
      </div>
      <form className="stack" onSubmit={submit}>
        <div className="row" style={{ alignItems: 'flex-end' }}>
          <Field label="Abteilung" htmlFor="dept">
            <Select
              id="dept"
              required
              value={form.departmentId}
              onChange={(e) => patch({ departmentId: e.target.value })}
            >
              <option value="">Bitte wählen …</option>
              {departments?.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </Select>
          </Field>

          <Field label="Tag" htmlFor="date">
            <Select id="date" value={form.date} onChange={(e) => patch({ date: e.target.value })}>
              {dates.map((d) => (
                <option key={d} value={d}>
                  {formatWeekdayShort(d)} {formatDate(d)}
                </option>
              ))}
            </Select>
          </Field>

          <Field label="Von" htmlFor="from">
            <input
              id="from"
              className="input"
              type="time"
              required
              value={form.startTime}
              onChange={(e) => patch({ startTime: e.target.value })}
            />
          </Field>

          <Field label="Bis" htmlFor="to">
            <input
              id="to"
              className="input"
              type="time"
              required
              value={form.endTime}
              onChange={(e) => patch({ endTime: e.target.value })}
            />
          </Field>
        </div>

        <label className="row row-tight small muted" style={{ cursor: 'pointer' }}>
          <input
            type="checkbox"
            checked={form.crossesMidnight}
            onChange={(e) => patch({ crossesMidnight: e.target.checked })}
          />
          Endet am Folgetag (z.&nbsp;B. 18:00–02:00)
        </label>

        <div className="row" style={{ alignItems: 'flex-end' }}>
          <Field label="Minimum" htmlFor="min" hint="darf nie unterschritten werden">
            <input
              id="min"
              className="input"
              type="number"
              min={0}
              value={form.minimumStaff}
              onChange={(e) => patch({ minimumStaff: Number(e.target.value) })}
            />
          </Field>
          <Field label="Soll" htmlFor="pref">
            <input
              id="pref"
              className="input"
              type="number"
              min={0}
              value={form.preferredStaff}
              onChange={(e) => patch({ preferredStaff: Number(e.target.value) })}
            />
          </Field>
          <Field label="Maximum" htmlFor="max">
            <input
              id="max"
              className="input"
              type="number"
              min={0}
              value={form.maximumStaff}
              onChange={(e) => patch({ maximumStaff: Number(e.target.value) })}
            />
          </Field>
        </div>

        {skills && skills.length > 0 && (
          <div className="field">
            <span className="label">Erforderliche Qualifikationen</span>
            <div className="row row-tight">
              {skills.map((skill) => {
                const count = form.requiredSkills[skill.id] ?? 0
                return (
                  <span key={skill.id} className="row row-tight">
                    <button
                      type="button"
                      className={`chip${count > 0 ? ' is-on' : ''}`}
                      style={
                        count > 0
                          ? { background: 'var(--accent-soft)', color: 'var(--accent)' }
                          : undefined
                      }
                      onClick={() => toggleSkill(skill.id, count > 0 ? 0 : 1)}
                    >
                      {skill.code}
                    </button>
                    {count > 0 && (
                      <input
                        className="input input-sm"
                        style={{ width: 58 }}
                        type="number"
                        min={1}
                        value={count}
                        aria-label={`Anzahl ${skill.code}`}
                        onChange={(e) => toggleSkill(skill.id, Number(e.target.value))}
                      />
                    )}
                  </span>
                )
              })}
            </div>
            <span className="hint">
              Beispiel: eine Bar-Schicht mit 1× BAR und 1× CLOSING unter drei Plätzen.
            </span>
          </div>
        )}

        <ErrorNotice error={create.error} />

        {form.minimumStaff > form.maximumStaff && (
          <Notice tone="warning">Das Minimum liegt über dem Maximum.</Notice>
        )}

        <div>
          <Button type="submit" variant="primary" loading={create.isPending}>
            Bedarf hinzufügen
          </Button>
        </div>
      </form>
    </Card>
  )
}
