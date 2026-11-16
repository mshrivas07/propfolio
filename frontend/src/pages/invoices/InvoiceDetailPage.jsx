import { useCallback, useState } from 'react'
import { Link, useParams } from 'react-router'
import Badge from '../../components/ui/Badge'
import Button from '../../components/ui/Button'
import { TextField } from '../../components/ui/Field'
import FormProblem from '../../components/ui/FormProblem'
import { EmptyState, ErrorState, LoadingState } from '../../components/ui/States'
import { useApiData } from '../../hooks/useApiData'
import { useFormSubmit } from '../../hooks/useFormSubmit'
import { getInvoice, updateInvoiceDueDate } from '../../lib/api'
import { formatDate, formatMoney, formatPercent, formatPeriod, invoiceBadge, utilityTypeLabels } from '../../lib/format'

export default function InvoiceDetailPage() {
  const { invoiceId } = useParams()
  const load = useCallback(() => getInvoice(invoiceId), [invoiceId])
  const { data, error, loading, reload, setData } = useApiData(load)

  return (
    <section>
      <Link to="/invoices" className="text-sm text-action underline-offset-4 hover:underline">← All invoices</Link>
      {loading && !data && <LoadingState />}
      {error && <div className="mt-4"><ErrorState error={error} onRetry={reload} /></div>}
      {data && <InvoiceView detail={data} onChanged={setData} />}
    </section>
  )
}

function InvoiceView({ detail, onChanged }) {
  const { invoice, charges } = detail
  const badge = invoiceBadge(invoice)
  const open = invoice.status === 'OPEN'

  return (
    <>
      <div className="mt-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="flex items-center gap-3 text-2xl font-semibold">
            {invoice.invoiceNumber} <Badge tone={badge.tone}>{badge.label}</Badge>
          </h1>
          <p className="mt-1 text-ink-muted">
            {invoice.tenantName} · {invoice.unitName},{' '}
            <Link to={`/properties/${invoice.propertyId}`} className="text-action underline-offset-4 hover:underline">
              {invoice.propertyName}
            </Link>
          </p>
          {formatPeriod(invoice.periodStart, invoice.periodEnd) && (
            <p className="mt-1 text-sm text-ink-muted">Covers {formatPeriod(invoice.periodStart, invoice.periodEnd)}</p>
          )}
        </div>
        <DueDate invoice={invoice} editable={open} onChanged={onChanged} />
      </div>

      {open && (
        <p className="mt-4 max-w-prose rounded-lg border border-action/30 bg-action/5 p-4 text-sm text-ink-muted">
          This invoice is still open: charges are added and updated automatically as you verify bills or change leases
          and split rules. Sending it to the tenant comes next.
        </p>
      )}

      <h2 className="mt-8 mb-3 text-lg font-semibold">Charges</h2>
      {charges.length === 0 ? (
        <EmptyState title="No charges yet">
          Charges appear when a bill for this unit is verified for days this tenant lived there.
        </EmptyState>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-line bg-panel">
          <table className="w-full min-w-[44rem] text-sm">
            <thead className="border-b border-line text-left text-xs text-ink-muted">
              <tr>
                <th scope="col" className="px-4 py-2 font-medium">Utility</th>
                <th scope="col" className="px-4 py-2 font-medium">Bill period</th>
                <th scope="col" className="px-4 py-2 text-right font-medium">Bill amount</th>
                <th scope="col" className="px-4 py-2 font-medium">Days</th>
                <th scope="col" className="px-4 py-2 font-medium">Share</th>
                <th scope="col" className="px-4 py-2 text-right font-medium">Amount</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {charges.map((charge) => (
                <tr key={charge.id}>
                  <td className="px-4 py-3">
                    <Link to={`/bills/${charge.billId}`} className="font-medium text-action underline-offset-4 hover:underline">
                      {charge.providerName ?? 'Bill'}
                    </Link>
                    {charge.utilityType && (
                      <span className="block text-xs text-ink-muted">{utilityTypeLabels[charge.utilityType]}</span>
                    )}
                  </td>
                  <td className="px-4 py-3">{formatPeriod(charge.billPeriodStart, charge.billPeriodEnd)}</td>
                  <td className="px-4 py-3 text-right tabular-nums">
                    {charge.splitAmount != null ? formatMoney(charge.splitAmount) : '—'}
                  </td>
                  <td className="px-4 py-3">
                    {charge.daysCovered} of {charge.billPeriodDays}
                    {charge.daysCovered < charge.billPeriodDays && (
                      <span className="block text-xs text-ink-muted">{formatPeriod(charge.coveredStart, charge.coveredEnd)}</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    {charge.shareType === 'PERCENT' ? formatPercent(charge.shareValue) : `${formatMoney(charge.shareValue)} flat`}
                  </td>
                  <td className="px-4 py-3 text-right font-medium tabular-nums">{formatMoney(charge.amount)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot className="border-t border-line">
              <tr>
                <th scope="row" colSpan={5} className="px-4 py-3 text-right font-semibold">Total</th>
                <td className="px-4 py-3 text-right text-base font-semibold tabular-nums">{formatMoney(invoice.totalAmount)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}
      <p className="mt-3 text-xs text-ink-muted">
        Each charge = bill amount × share × days lived there ÷ days in the bill period, rounded to the cent.
      </p>
    </>
  )
}

function DueDate({ invoice, editable, onChanged }) {
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState(invoice.dueDate ?? '')
  const save = useFormSubmit()

  if (!editing) {
    return (
      <div className="text-right">
        <p className="text-xs text-ink-muted">Due</p>
        <p className="font-medium">{formatDate(invoice.dueDate)}</p>
        {editable && <Button variant="link" onClick={() => setEditing(true)}>Change</Button>}
      </div>
    )
  }

  return (
    <form
      noValidate
      className="w-60 space-y-2"
      onSubmit={async (event) => {
        event.preventDefault()
        let updated = null
        const ok = await save.submit(async () => {
          updated = await updateInvoiceDueDate(invoice.id, value || null)
        })
        if (ok) {
          onChanged(updated)
          setEditing(false)
        }
      }}
    >
      <TextField label="Due date" type="date" value={value} onChange={(e) => setValue(e.target.value)} error={save.fieldErrors.dueDate} />
      <FormProblem error={save.error} />
      <div className="flex gap-2">
        <Button type="submit" disabled={save.busy}>{save.busy ? 'Saving…' : 'Save'}</Button>
        <Button variant="secondary" onClick={() => setEditing(false)} disabled={save.busy}>Cancel</Button>
      </div>
    </form>
  )
}
