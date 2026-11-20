import { useCallback, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import Badge from '../../components/ui/Badge'
import Button from '../../components/ui/Button'
import { SelectField } from '../../components/ui/Field'
import FormProblem from '../../components/ui/FormProblem'
import Section from '../../components/ui/Section'
import { EmptyState, ErrorState, LoadingState } from '../../components/ui/States'
import { useApiData } from '../../hooks/useApiData'
import { useFormSubmit } from '../../hooks/useFormSubmit'
import { createInvoice, deleteInvoice, listInvoices, listProperties, listTenants } from '../../lib/api'
import {
  formatDate,
  formatDateTime,
  formatMoney,
  formatPeriod,
  invoiceBadge,
  invoiceStatusLabels,
} from '../../lib/format'

const statusOptions = Object.entries(invoiceStatusLabels)
  .filter(([value]) => value !== 'OPEN')
  .map(([value, label]) => ({ value, label }))

/**
 * Invoices: what's collecting for each tenant (with "Create invoice"), then every created invoice.
 * Filters live in the URL so tenant and property pages can link here.
 */
export default function InvoicesPage() {
  const [params, setParams] = useSearchParams()
  const status = params.get('status') ?? ''
  const propertyId = params.get('propertyId') ?? ''
  const tenantId = params.get('tenantId') ?? ''
  const includeArchived = params.get('archived') === '1'

  const loadOpen = useCallback(() => listInvoices({ status: 'OPEN', propertyId, tenantId }), [propertyId, tenantId])
  const loadCreated = useCallback(
    () => listInvoices({ status, propertyId, tenantId, includeArchived }),
    [status, propertyId, tenantId, includeArchived],
  )
  const open = useApiData(loadOpen)
  const created = useApiData(loadCreated)
  const properties = useApiData(listProperties)
  const tenants = useApiData(listTenants)

  const setParam = (name, value) => {
    const next = new URLSearchParams(params)
    if (value) next.set(name, value)
    else next.delete(name)
    setParams(next, { replace: true })
  }
  const reloadAll = () => {
    open.reload({ silent: true })
    created.reload({ silent: true })
  }

  const createdRows = (created.data ?? []).filter((invoice) => invoice.status !== 'OPEN')
  const filtered = Boolean(status || propertyId || tenantId)

  return (
    <section>
      <h1 className="text-2xl font-semibold">Invoices</h1>
      <p className="mt-1 max-w-prose text-sm text-ink-muted">
        Each tenant's charges collect on their next invoice as you verify bills. When you're ready to bill them,
        create the invoice: its charges and total are fixed, and new charges start collecting on the next one.
      </p>

      <div className="mt-6 grid gap-4 sm:grid-cols-2 md:max-w-xl">
        <SelectField
          label="Property"
          placeholder="Any property"
          value={propertyId}
          onChange={(e) => setParam('propertyId', e.target.value)}
          options={(properties.data ?? []).map((p) => ({ value: p.id, label: p.name }))}
        />
        <SelectField
          label="Tenant"
          placeholder="Any tenant"
          value={tenantId}
          onChange={(e) => setParam('tenantId', e.target.value)}
          options={(tenants.data ?? []).map((t) => ({ value: t.id, label: t.fullName }))}
        />
      </div>

      <Section title="Next invoice (collecting)" description="One per tenant. Not owed yet.">
        {open.loading && !open.data && <LoadingState />}
        {open.error && <ErrorState error={open.error} onRetry={open.reload} />}
        {open.data && open.data.length === 0 && (
          <EmptyState title="Nothing collecting">
            Charges appear here when you{' '}
            <Link to="/bills" className="text-action underline underline-offset-4">verify a bill</Link> for a unit
            with a tenant and a split rule.
          </EmptyState>
        )}
        {open.data && open.data.length > 0 && (
          <ul className="divide-y divide-line rounded-lg border border-line bg-panel">
            {open.data.map((invoice) => (
              <CollectingRow key={invoice.id} invoice={invoice} onChanged={reloadAll} />
            ))}
          </ul>
        )}
      </Section>

      <Section title="Created invoices">
        <div className="mb-4 flex flex-wrap items-end gap-4">
          <SelectField
            className="w-56"
            label="Status"
            placeholder="Any status"
            value={status}
            onChange={(e) => setParam('status', e.target.value)}
            options={statusOptions}
          />
          <label className="flex items-center gap-2 pb-2 text-sm">
            <input
              type="checkbox"
              className="size-4 accent-action"
              checked={includeArchived}
              onChange={(e) => setParam('archived', e.target.checked ? '1' : '')}
            />
            Show archived
          </label>
        </div>

        {created.loading && !created.data && <LoadingState />}
        {created.error && <ErrorState error={created.error} onRetry={created.reload} />}
        {created.data && createdRows.length === 0 && (
          <EmptyState title={filtered ? 'No invoices match these filters' : 'No invoices created yet'}>
            {filtered ? 'Try a different status, property or tenant.' : 'Use "Create invoice" on a collecting invoice above.'}
          </EmptyState>
        )}
        {createdRows.length > 0 && (
          <div className={`overflow-x-auto rounded-lg border border-line bg-panel ${created.loading ? 'opacity-60' : ''}`}>
            <table className="w-full min-w-[52rem] text-sm">
              <thead className="border-b border-line text-left text-xs text-ink-muted">
                <tr>
                  <th scope="col" className="px-4 py-2 font-medium">Invoice</th>
                  <th scope="col" className="px-4 py-2 font-medium">Tenant</th>
                  <th scope="col" className="px-4 py-2 font-medium">Created</th>
                  <th scope="col" className="px-4 py-2 text-right font-medium">Total</th>
                  <th scope="col" className="px-4 py-2 text-right font-medium">Still owed</th>
                  <th scope="col" className="px-4 py-2 font-medium">Due</th>
                  <th scope="col" className="px-4 py-2 font-medium">Status</th>
                  <th scope="col" className="px-4 py-2"><span className="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {createdRows.map((invoice) => (
                  <CreatedRow key={invoice.id} invoice={invoice} onChanged={reloadAll} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Section>
    </section>
  )
}

function CollectingRow({ invoice, onChanged }) {
  const action = useFormSubmit()
  const canCreate = invoice.actions.includes('CREATE_INVOICE')

  return (
    <li className="px-4 py-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <span className="min-w-0">
          <span className="font-medium">{invoice.tenantName}</span>
          <span className="block text-sm text-ink-muted">
            {invoice.unitName}, {invoice.propertyName} · {invoice.invoiceNumber}
            {' · '}
            {invoice.chargeCount === 0
              ? 'no charges yet'
              : `${invoice.chargeCount} ${invoice.chargeCount === 1 ? 'charge' : 'charges'}, ${formatPeriod(invoice.periodStart, invoice.periodEnd)}`}
          </span>
        </span>
        <span className="flex items-center gap-3">
          <span className="font-medium tabular-nums">{formatMoney(invoice.totalAmount)}</span>
          <Link to={`/invoices/${invoice.id}`} className="text-sm text-action underline-offset-4 hover:underline">View</Link>
          <Button
            onClick={async () => (await action.submit(() => createInvoice(invoice.id))) && onChanged()}
            disabled={!canCreate || action.busy}
            title={canCreate ? undefined : 'Nothing to invoice yet'}
          >
            {action.busy ? 'Creating…' : 'Create invoice'}
          </Button>
        </span>
      </div>
      {action.error && <div className="mt-2"><FormProblem error={action.error} /></div>}
    </li>
  )
}

function CreatedRow({ invoice, onChanged }) {
  const action = useFormSubmit()
  const badge = invoiceBadge(invoice)
  const [confirming, setConfirming] = useState(false)

  async function handleDelete() {
    if (!confirming) {
      setConfirming(true)
      return
    }
    if (await action.submit(() => deleteInvoice(invoice.id))) onChanged()
    setConfirming(false)
  }

  return (
    <tr className="align-top">
      <td className="px-4 py-3">
        <Link to={`/invoices/${invoice.id}`} className="font-medium text-action underline-offset-4 hover:underline">
          {invoice.invoiceNumber}
        </Link>
        <span className="block text-xs text-ink-muted">{formatPeriod(invoice.periodStart, invoice.periodEnd)}</span>
      </td>
      <td className="px-4 py-3">
        {invoice.tenantName}
        <span className="block text-xs text-ink-muted">{invoice.unitName}, {invoice.propertyName}</span>
      </td>
      <td className="px-4 py-3">{formatDateTime(invoice.invoiceCreatedAt)}</td>
      <td className="px-4 py-3 text-right tabular-nums">{formatMoney(invoice.totalAmount)}</td>
      <td className="px-4 py-3 text-right font-medium tabular-nums">
        {invoice.status === 'CANCELLED' ? '—' : formatMoney(invoice.remaining)}
      </td>
      <td className="px-4 py-3">{formatDate(invoice.dueDate)}</td>
      <td className="px-4 py-3"><Badge tone={badge.tone}>{badge.label}</Badge></td>
      <td className="px-4 py-3 text-right">
        <span className="flex justify-end gap-3 whitespace-nowrap">
          <Link to={`/invoices/${invoice.id}`} className="text-sm font-medium text-action underline-offset-4 hover:underline">
            Manage
          </Link>
          {invoice.actions.includes('DELETE') && (
            <Button variant="link" className="text-danger" onClick={handleDelete} disabled={action.busy}>
              {confirming ? 'Confirm delete' : 'Delete'}
            </Button>
          )}
        </span>
        {confirming && (
          <span className="mt-1 block max-w-48 text-left text-xs text-ink-muted">
            Undoes "Create invoice": its charges go back to collecting, same number.{' '}
            <button type="button" className="underline" onClick={() => setConfirming(false)}>Keep it</button>
          </span>
        )}
        {action.error && <span className="mt-1 block text-left"><FormProblem error={action.error} /></span>}
      </td>
    </tr>
  )
}
