import { useCallback, useState } from 'react'
import { Link } from 'react-router'
import Badge from '../../components/ui/Badge'
import { SelectField } from '../../components/ui/Field'
import Section from '../../components/ui/Section'
import { EmptyState, ErrorState, LoadingState } from '../../components/ui/States'
import { useApiData } from '../../hooks/useApiData'
import { listAllUtilityAccounts, listBills, listProperties } from '../../lib/api'
import {
  billStatusLabels,
  billStatusTones,
  formatDateTime,
  formatFileSize,
  utilityTypeLabels,
} from '../../lib/format'
import BillUploader from './BillUploader'

const statusOptions = Object.entries(billStatusLabels).map(([value, label]) => ({ value, label }))

export default function BillsPage() {
  const [status, setStatus] = useState('')
  const [propertyId, setPropertyId] = useState('')

  const loadBills = useCallback(() => listBills({ status, propertyId }), [status, propertyId])
  const bills = useApiData(loadBills)
  const accounts = useApiData(listAllUtilityAccounts)
  const properties = useApiData(listProperties)

  const filtered = Boolean(status || propertyId)

  return (
    <section>
      <h1 className="text-2xl font-semibold">Bills</h1>
      <p className="mt-1 max-w-prose text-sm text-ink-muted">
        Upload the utility bills you download from your providers. Reading the amounts and dates from them comes next.
      </p>

      <div className="mt-6">
        <BillUploader accounts={accounts.data ?? []} onUploaded={bills.reload} />
        {accounts.error && (
          <p className="mt-2 text-sm text-danger">Couldn't load your utility accounts. You can still upload.</p>
        )}
      </div>

      <Section title="All bills">
        <div className="mb-4 grid gap-4 sm:grid-cols-2 md:max-w-xl">
          <SelectField
            label="Status"
            placeholder="Any status"
            value={status}
            onChange={(event) => setStatus(event.target.value)}
            options={statusOptions}
          />
          <SelectField
            label="Property"
            placeholder="Any property"
            value={propertyId}
            onChange={(event) => setPropertyId(event.target.value)}
            options={(properties.data ?? []).map((p) => ({ value: p.id, label: p.name }))}
          />
        </div>

        {bills.loading && !bills.data && <LoadingState />}
        {bills.error && <ErrorState error={bills.error} onRetry={bills.reload} />}
        {bills.data && bills.data.length === 0 && (
          <EmptyState title={filtered ? 'No bills match these filters' : 'No bills yet'}>
            {filtered ? 'Try a different status or property.' : 'Drop your first bill above.'}
          </EmptyState>
        )}
        {bills.data && bills.data.length > 0 && (
          <ul className={`divide-y divide-line overflow-hidden rounded-lg border border-line bg-panel ${bills.loading ? 'opacity-60' : ''}`}>
            {bills.data.map((bill) => (
              <li key={bill.id}>
                <Link
                  to={`/bills/${bill.id}`}
                  className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 px-4 py-3 hover:bg-surface"
                >
                  <span className="min-w-0">
                    <span className="block truncate font-medium">{bill.originalFilename}</span>
                    <span className="block text-sm text-ink-muted">
                      {bill.providerName
                        ? `${bill.propertyName ?? ''}${bill.propertyName ? ' · ' : ''}${bill.providerName}${
                            bill.utilityType ? ` (${utilityTypeLabels[bill.utilityType]})` : ''
                          }`
                        : 'No utility account yet'}
                    </span>
                  </span>
                  <span className="flex items-center gap-3 text-sm text-ink-muted">
                    <span>
                      {formatDateTime(bill.createdAt)} · {formatFileSize(bill.fileSizeBytes)}
                    </span>
                    <Badge tone={billStatusTones[bill.status]}>{billStatusLabels[bill.status]}</Badge>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </Section>
    </section>
  )
}
