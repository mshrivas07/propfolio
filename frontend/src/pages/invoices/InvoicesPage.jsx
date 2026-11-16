import { useCallback } from 'react'
import { Link, useSearchParams } from 'react-router'
import Badge from '../../components/ui/Badge'
import { SelectField } from '../../components/ui/Field'
import { EmptyState, ErrorState, LoadingState } from '../../components/ui/States'
import { useApiData } from '../../hooks/useApiData'
import { listInvoices, listProperties, listTenants } from '../../lib/api'
import { formatDate, formatMoney, formatPeriod, invoiceBadge, invoiceStatusLabels } from '../../lib/format'

const statusOptions = Object.entries(invoiceStatusLabels).map(([value, label]) => ({ value, label }))

/** All invoices, newest first. Filters live in the URL so tenant and property pages can link here. */
export default function InvoicesPage() {
  const [params, setParams] = useSearchParams()
  const status = params.get('status') ?? ''
  const propertyId = params.get('propertyId') ?? ''
  const tenantId = params.get('tenantId') ?? ''

  const load = useCallback(() => listInvoices({ status, propertyId, tenantId }), [status, propertyId, tenantId])
  const invoices = useApiData(load)
  const properties = useApiData(listProperties)
  const tenants = useApiData(listTenants)

  const setFilter = (name) => (event) => {
    const next = new URLSearchParams(params)
    if (event.target.value) next.set(name, event.target.value)
    else next.delete(name)
    setParams(next, { replace: true })
  }
  const filtered = Boolean(status || propertyId || tenantId)

  return (
    <section>
      <h1 className="text-2xl font-semibold">Invoices</h1>
      <p className="mt-1 max-w-prose text-sm text-ink-muted">
        Each tenant has one open invoice. When you verify a bill, each tenant's share of it is added there
        automatically, worked out from the split rule and the days they lived in the unit.
      </p>

      <div className="mt-6 mb-4 grid gap-4 sm:grid-cols-3 md:max-w-3xl">
        <SelectField label="Status" placeholder="Any status" value={status} onChange={setFilter('status')} options={statusOptions} />
        <SelectField
          label="Property"
          placeholder="Any property"
          value={propertyId}
          onChange={setFilter('propertyId')}
          options={(properties.data ?? []).map((p) => ({ value: p.id, label: p.name }))}
        />
        <SelectField
          label="Tenant"
          placeholder="Any tenant"
          value={tenantId}
          onChange={setFilter('tenantId')}
          options={(tenants.data ?? []).map((t) => ({ value: t.id, label: t.fullName }))}
        />
      </div>

      {invoices.loading && !invoices.data && <LoadingState />}
      {invoices.error && <ErrorState error={invoices.error} onRetry={invoices.reload} />}
      {invoices.data && invoices.data.length === 0 && (
        <EmptyState title={filtered ? 'No invoices match these filters' : 'No invoices yet'}>
          {filtered ? (
            'Try a different status, property or tenant.'
          ) : (
            <>
              Invoices appear when you <Link to="/bills" className="text-action underline underline-offset-4">verify a bill</Link>{' '}
              for a unit that has a tenant and a split rule.
            </>
          )}
        </EmptyState>
      )}
      {invoices.data && invoices.data.length > 0 && (
        <div className={`overflow-x-auto rounded-lg border border-line bg-panel ${invoices.loading ? 'opacity-60' : ''}`}>
          <table className="w-full min-w-[44rem] text-sm">
            <thead className="border-b border-line text-left text-xs text-ink-muted">
              <tr>
                <th scope="col" className="px-4 py-2 font-medium">Invoice</th>
                <th scope="col" className="px-4 py-2 font-medium">Tenant</th>
                <th scope="col" className="px-4 py-2 font-medium">Unit</th>
                <th scope="col" className="px-4 py-2 font-medium">Period</th>
                <th scope="col" className="px-4 py-2 text-right font-medium">Total</th>
                <th scope="col" className="px-4 py-2 font-medium">Due</th>
                <th scope="col" className="px-4 py-2 font-medium">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {invoices.data.map((invoice) => {
                const badge = invoiceBadge(invoice)
                return (
                  <tr key={invoice.id} className="hover:bg-surface">
                    <td className="px-4 py-3">
                      <Link to={`/invoices/${invoice.id}`} className="font-medium text-action underline-offset-4 hover:underline">
                        {invoice.invoiceNumber}
                      </Link>
                    </td>
                    <td className="px-4 py-3">{invoice.tenantName}</td>
                    <td className="px-4 py-3">
                      {invoice.unitName}
                      <span className="block text-xs text-ink-muted">{invoice.propertyName}</span>
                    </td>
                    <td className="px-4 py-3">
                      {formatPeriod(invoice.periodStart, invoice.periodEnd) || <span className="text-ink-muted">No charges yet</span>}
                    </td>
                    <td className="px-4 py-3 text-right font-medium tabular-nums">{formatMoney(invoice.totalAmount)}</td>
                    <td className="px-4 py-3">{formatDate(invoice.dueDate)}</td>
                    <td className="px-4 py-3"><Badge tone={badge.tone}>{badge.label}</Badge></td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
