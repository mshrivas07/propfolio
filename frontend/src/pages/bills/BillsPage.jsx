import { useCallback, useState } from 'react'
import { Link } from 'react-router'
import Badge from '../../components/ui/Badge'
import { SelectField } from '../../components/ui/Field'
import Section from '../../components/ui/Section'
import { EmptyState, ErrorState, LoadingState } from '../../components/ui/States'
import { useApiData, useInterval } from '../../hooks/useApiData'
import { listAllUtilityAccounts, listBills, listProperties } from '../../lib/api'
import {
  billBadge,
  billStatusLabels,
  formatDate,
  formatDateTime,
  formatMoney,
  utilityTypeLabels,
} from '../../lib/format'
import BillUploader from './BillUploader'

const statusOptions = ['UPLOADED', 'NEEDS_REVIEW', 'READY', 'VERIFIED'].map((value) => ({
  value,
  label: billStatusLabels[value],
}))
const POLL_MS = 3000

export default function BillsPage() {
  const [status, setStatus] = useState('')
  const [propertyId, setPropertyId] = useState('')

  const loadBills = useCallback(() => listBills({ status, propertyId }), [status, propertyId])
  const bills = useApiData(loadBills)
  const accounts = useApiData(listAllUtilityAccounts)
  const properties = useApiData(listProperties)

  const filtered = Boolean(status || propertyId)
  const anyReading = (bills.data ?? []).some((bill) => bill.extractionState === 'RUNNING')
  // While a bill is being read, refresh quietly until the reading finishes.
  useInterval(() => bills.reload({ silent: true }), POLL_MS, anyReading)

  return (
    <section>
      <h1 className="text-2xl font-semibold">Bills</h1>
      <p className="mt-1 max-w-prose text-sm text-ink-muted">
        Upload the utility bills you download from your providers. Each one is read automatically; check the details
        and verify it before it goes on an invoice.
      </p>

      <div className="mt-6">
        <BillUploader accounts={accounts.data ?? []} onUploaded={() => bills.reload({ silent: true })} />
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
            {bills.data.map((bill) => {
              const badge = billBadge(bill)
              return (
                <li key={bill.id}>
                  <Link
                    to={`/bills/${bill.id}`}
                    className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 px-4 py-3 hover:bg-surface"
                  >
                    <span className="min-w-0">
                      <span className="block truncate font-medium">
                        {bill.providerName
                          ? `${bill.providerName}${bill.utilityType ? ` (${utilityTypeLabels[bill.utilityType]})` : ''}`
                          : bill.originalFilename}
                      </span>
                      <span className="block text-sm text-ink-muted">
                        {[
                          bill.propertyName,
                          bill.periodStart && bill.periodEnd
                            ? `${formatDate(bill.periodStart)} – ${formatDate(bill.periodEnd)}`
                            : null,
                          bill.providerName ? bill.originalFilename : 'No utility account yet',
                        ]
                          .filter(Boolean)
                          .join(' · ')}
                      </span>
                    </span>
                    <span className="flex items-center gap-3 text-sm text-ink-muted">
                      {(bill.amountToSplit ?? bill.currentCharges) != null ? (
                        <span className="font-medium tabular-nums text-ink">
                          {formatMoney(bill.amountToSplit ?? bill.currentCharges)}
                        </span>
                      ) : (
                        <span>Uploaded {formatDateTime(bill.createdAt)}</span>
                      )}
                      <Badge tone={badge.tone}>{badge.label}</Badge>
                    </span>
                  </Link>
                </li>
              )
            })}
          </ul>
        )}
      </Section>
    </section>
  )
}
