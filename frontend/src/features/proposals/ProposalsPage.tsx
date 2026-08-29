import { Link, useParams } from 'react-router-dom'
import { useProposals, useSelectSchedule } from '@/api/queries'
import type { ScheduleMetrics, ScheduleSummary } from '@/api/types'
import {
  formatCurrency,
  formatHours,
  formatPercent,
  jobStatusLabels,
  strategyDescriptions,
  strategyLabels,
} from '@/lib/format'
import { useGenerationFlow } from './useGenerationFlow'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorNotice,
  LoadingBlock,
  Notice,
} from '@/ui/primitives'

/**
 * The three plans side by side.
 *
 * The comparison is the product: any one plan looks reasonable in isolation, and the value
 * of generating three is seeing what each one costs in the other dimensions. So the best
 * value in every row is marked, and an infeasible plan is called out rather than quietly
 * shown next to feasible ones as if it were an equal option.
 */

interface MetricRow {
  key: string
  label: string
  render: (m: ScheduleMetrics) => string
  /** Which direction is better, for highlighting. */
  better: 'lower' | 'higher'
  value: (m: ScheduleMetrics) => number
}

const METRICS: MetricRow[] = [
  {
    key: 'cost',
    label: 'Personalkosten',
    render: (m) => formatCurrency(m.totalStaffCost),
    better: 'lower',
    value: (m) => m.totalStaffCost,
  },
  {
    key: 'wishes',
    label: 'Wunscherfüllung',
    render: (m) => formatPercent(m.preferenceSatisfaction),
    better: 'higher',
    value: (m) => m.preferenceSatisfaction,
  },
  {
    key: 'contract',
    label: 'Vertragsstunden-Abweichung',
    render: (m) => formatPercent(m.contractHoursDeviation, 1),
    better: 'lower',
    value: (m) => m.contractHoursDeviation,
  },
  {
    key: 'unfilled',
    label: 'Nicht besetzte Positionen',
    render: (m) => String(m.unfilledPositions),
    better: 'lower',
    value: (m) => m.unfilledPositions,
  },
  {
    key: 'overtime',
    label: 'Überstunden',
    render: (m) => formatHours(m.overtimeHours),
    better: 'lower',
    value: (m) => m.overtimeHours,
  },
  {
    key: 'fairness',
    label: 'Fairness',
    render: (m) => `${Math.round(m.fairnessScore)}/100`,
    better: 'higher',
    value: (m) => m.fairnessScore,
  },
]

export default function ProposalsPage() {
  const { periodId } = useParams<{ periodId: string }>()
  const { data: proposals, isLoading, error } = useProposals(periodId)
  const generation = useGenerationFlow(periodId)
  const select = useSelectSchedule(periodId)

  const best = (row: MetricRow): number | null => {
    if (!proposals || proposals.length === 0) return null
    const values = proposals.map((p) => row.value(p.metrics))
    return row.better === 'lower' ? Math.min(...values) : Math.max(...values)
  }

  if (isLoading) {
    return (
      <Card>
        <LoadingBlock rows={4} />
      </Card>
    )
  }

  if (error) return <ErrorNotice error={error} />

  if (!proposals || proposals.length === 0) {
    return (
      <Card>
        <EmptyState
          title="Noch keine Vorschläge"
          description="Erzeuge drei Pläne — fair, kostenoptimiert und ausgewogen — und vergleiche sie hier."
          action={
            <Button
              variant="primary"
              loading={generation.isStarting || generation.isRunning}
              onClick={generation.start}
            >
              Pläne generieren
            </Button>
          }
        />
        {generation.job && generation.isRunning && (
          <Notice tone="info">
            <span className="row row-tight">
              <span className="spinner" aria-hidden="true" />
              {jobStatusLabels[generation.job.status]}
            </span>
          </Notice>
        )}
        <ErrorNotice error={generation.error} />
      </Card>
    )
  }

  return (
    <div className="stack-lg">
      <div className="row spread">
        <p className="muted">
          Alle drei Pläne halten dieselben harten Regeln ein. Sie unterscheiden sich darin, was
          sie darüber hinaus optimieren.
        </p>
        <Button
          loading={generation.isStarting || generation.isRunning}
          onClick={generation.start}
        >
          Neu berechnen
        </Button>
      </div>

      <ErrorNotice error={generation.error} />
      <ErrorNotice error={select.error} />

      <div className="row" style={{ alignItems: 'stretch', gap: 'var(--space-4)' }}>
        {proposals.map((p) => (
          <ProposalCard
            key={p.id}
            proposal={p}
            periodId={periodId!}
            onSelect={() => select.mutate(p.id)}
            selecting={select.isPending && select.variables === p.id}
          />
        ))}
      </div>

      <Card>
        <div className="card-header">
          <h2>Direktvergleich</h2>
          <span className="small subtle">Bester Wert je Zeile hervorgehoben</span>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Kennzahl</th>
                {proposals.map((p) => (
                  <th key={p.id} className="num">
                    {strategyLabels[p.strategy]}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {METRICS.map((row) => {
                const bestValue = best(row)
                return (
                  <tr key={row.key}>
                    <td>{row.label}</td>
                    {proposals.map((p) => {
                      const isBest = bestValue !== null && row.value(p.metrics) === bestValue
                      return (
                        <td key={p.id} className="num">
                          <span style={isBest ? { fontWeight: 700, color: 'var(--accent)' } : undefined}>
                            {row.render(p.metrics)}
                          </span>
                        </td>
                      )
                    })}
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  )
}

function ProposalCard({
  proposal,
  periodId,
  onSelect,
  selecting,
}: {
  proposal: ScheduleSummary
  periodId: string
  onSelect: () => void
  selecting: boolean
}) {
  const m = proposal.metrics
  return (
    <Card className="grow" selected={proposal.selected}>
      <div className="card-header">
        <div>
          <h2>{strategyLabels[proposal.strategy]}</h2>
          {proposal.strategy === 'BALANCED' && <Badge tone="accent">Empfehlung</Badge>}
        </div>
        {proposal.selected && <Badge tone="success">gewählt</Badge>}
      </div>

      <p className="small muted" style={{ minHeight: 56 }}>
        {strategyDescriptions[proposal.strategy]}
      </p>

      {!m.feasible && (
        <Notice tone="danger">
          Dieser Plan verletzt harte Regeln — {m.unfilledPositions} Position(en) konnten nicht
          regelkonform besetzt werden.
        </Notice>
      )}

      <div className="stack" style={{ gap: 6, margin: 'var(--space-4) 0' }}>
        <MetricLine label="Personalkosten" value={formatCurrency(m.totalStaffCost)} />
        <MetricLine label="Wunscherfüllung" value={formatPercent(m.preferenceSatisfaction)} />
        <MetricLine label="Überstunden" value={formatHours(m.overtimeHours)} />
        <MetricLine
          label="Unbesetzt"
          value={String(m.unfilledPositions)}
          tone={m.unfilledPositions > 0 ? 'danger' : 'success'}
        />
        <MetricLine label="Fairness" value={`${Math.round(m.fairnessScore)}/100`} />
      </div>

      <div className="row row-tight">
        <Button variant="primary" onClick={onSelect} loading={selecting} disabled={proposal.selected}>
          {proposal.selected ? 'Ausgewählt' : 'Diesen wählen'}
        </Button>
        <Link className="btn" to={`/periods/${periodId}/schedules/${proposal.id}`}>
          Öffnen
        </Link>
      </div>
    </Card>
  )
}

function MetricLine({
  label,
  value,
  tone,
}: {
  label: string
  value: string
  tone?: 'danger' | 'success'
}) {
  const color =
    tone === 'danger' ? 'var(--danger)' : tone === 'success' ? 'var(--success)' : undefined
  return (
    <div className="row spread">
      <span className="small muted">{label}</span>
      <span className="num" style={{ fontWeight: 600, color }}>
        {value}
      </span>
    </div>
  )
}
