import { useState } from 'react'
import { useAuditLog } from '@/api/queries'
import { formatInstant } from '@/lib/format'
import { Badge, Card, EmptyState, ErrorNotice, LoadingBlock, Select } from '@/ui/primitives'

/**
 * Who changed what, and when.
 *
 * The trail is append-only on the server; this screen only reads it. Metadata is shown raw
 * on purpose — it is the evidence, and prettifying it would mean deciding which parts matter
 * before anyone knows what they are looking for.
 */
export default function AuditPage() {
  const [limit, setLimit] = useState(50)
  const { data, isLoading, error } = useAuditLog(limit)

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Audit-Log</h1>
          <p className="subtitle">
            Verfügbarkeiten, Deadlines, Plangenerierung, Schichtänderungen, Veröffentlichungen.
          </p>
        </div>
        <Select
          value={limit}
          onChange={(e) => setLimit(Number(e.target.value))}
          aria-label="Anzahl Einträge"
        >
          <option value={25}>25 Einträge</option>
          <option value={50}>50 Einträge</option>
          <option value={100}>100 Einträge</option>
          <option value={200}>200 Einträge</option>
        </Select>
      </header>

      <ErrorNotice error={error} />
      {isLoading && (
        <Card>
          <LoadingBlock rows={6} />
        </Card>
      )}

      {data?.length === 0 && (
        <Card>
          <EmptyState title="Noch keine Einträge" description="Sobald etwas passiert, steht es hier." />
        </Card>
      )}

      {data && data.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Zeitpunkt</th>
                <th>Aktion</th>
                <th>Objekt</th>
                <th>Details</th>
              </tr>
            </thead>
            <tbody>
              {data.map((entry) => (
                <tr key={entry.id}>
                  <td className="num" style={{ whiteSpace: 'nowrap' }}>
                    {formatInstant(entry.occurredAt)}
                  </td>
                  <td>
                    <Badge tone="accent">{entry.action}</Badge>
                  </td>
                  <td className="small">
                    {entry.entityType}
                    {entry.entityId && (
                      <div className="mono subtle">{entry.entityId.slice(0, 8)}…</div>
                    )}
                  </td>
                  <td className="mono subtle" style={{ maxWidth: 420, overflowWrap: 'anywhere' }}>
                    {entry.metadata ?? '—'}
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
