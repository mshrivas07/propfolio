import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import Badge from '../../components/ui/Badge'
import Button from '../../components/ui/Button'
import { TextField } from '../../components/ui/Field'
import FormProblem from '../../components/ui/FormProblem'
import { EmptyState, ErrorState, LoadingState } from '../../components/ui/States'
import { useApiData } from '../../hooks/useApiData'
import { useFormSubmit } from '../../hooks/useFormSubmit'
import {
  archiveInvoice,
  cancelInvoice,
  createInvoice,
  deleteInvoice,
  deletePayment,
  fetchInvoicePdf,
  getInvoice,
  recordPayment,
  unarchiveInvoice,
  updateInvoiceDueDate,
} from '../../lib/api'
import {
  formatDate,
  formatDateTime,
  formatMoney,
  formatPercent,
  formatPeriod,
  invoiceBadge,
  todayIso,
  utilityTypeLabels,
} from '../../lib/format'

/** Manage one invoice: its charges, status moves, payments and PDF. */
export default function InvoiceDetailPage() {
  const { invoiceId } = useParams()
  const load = useCallback(() => getInvoice(invoiceId), [invoiceId])
  const { data, error, loading, reload, setData } = useApiData(load)

  return (
    <section>
      <Link to="/invoices" className="text-sm text-action underline-offset-4 hover:underline">← All invoices</Link>
      {loading && !data && <LoadingState />}
      {error && <div className="mt-4"><ErrorState error={error} onRetry={reload} /></div>}
      {data && <InvoiceView key={data.invoice.id} detail={data} onChanged={setData} />}
    </section>
  )
}

function InvoiceView({ detail, onChanged }) {
  const { invoice, charges } = detail
  const actions = new Set(invoice.actions)
  const badge = invoiceBadge(invoice)
  const move = useFormSubmit()

  async function run(call) {
    let updated = null
    const ok = await move.submit(async () => {
      updated = await call(invoice.id)
    })
    if (ok) onChanged(updated)
  }

  async function handleDelete() {
    if (!window.confirm(`Delete ${invoice.invoiceNumber}? This undoes "Create invoice": its charges go back to collecting with the same number.`)) return
    await run(deleteInvoice)
  }

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
        </div>
        <DueDate invoice={invoice} editable={actions.has('EDIT_DUE_DATE')} onChanged={onChanged} />
      </div>
      <History invoice={invoice} />

      <div className="mt-5 flex flex-wrap items-center gap-2">
        {actions.has('CREATE_INVOICE') && (
          <Button onClick={() => run(createInvoice)} disabled={move.busy}>Create invoice</Button>
        )}
        {invoice.status === 'OPEN' && !actions.has('CREATE_INVOICE') && (
          <span className="text-sm text-ink-muted">Nothing to invoice yet.</span>
        )}
        {(invoice.status === 'INVOICE_CREATED' || invoice.status === 'EMAIL_DRAFTED') && (
          <>
            <Button disabled title="Available after Gmail is connected">
              {invoice.status === 'EMAIL_DRAFTED' ? 'Redo Gmail draft' : 'Create Gmail draft'}
            </Button>
            <Button variant="secondary" disabled title="Available after Gmail is connected">Mark as sent</Button>
            <span className="text-xs text-ink-muted">Draft and sent are available after Gmail is connected.</span>
          </>
        )}
        {actions.has('ARCHIVE') && <Button variant="secondary" onClick={() => run(archiveInvoice)} disabled={move.busy}>Archive</Button>}
        {actions.has('UNARCHIVE') && <Button variant="secondary" onClick={() => run(unarchiveInvoice)} disabled={move.busy}>Unarchive</Button>}
        {actions.has('DELETE') && <Button variant="danger" onClick={handleDelete} disabled={move.busy}>Delete</Button>}
        <PdfButtons invoice={invoice} />
      </div>
      <div className="mt-3"><FormProblem error={move.error} /></div>

      {invoice.status === 'OPEN' && (
        <p className="mt-2 max-w-prose rounded-lg border border-action/30 bg-action/5 p-4 text-sm text-ink-muted">
          Still collecting: charges are added and updated automatically as you verify bills or change leases and split
          rules. Create the invoice when you're ready to bill the tenant.
        </p>
      )}
      {invoice.status === 'CANCELLED' && (
        <p className="mt-2 max-w-prose rounded-lg border border-danger/30 bg-danger/5 p-4 text-sm text-ink-muted">
          Cancelled on {formatDateTime(invoice.cancelledAt)}. Kept for your records; its charges went back to the
          tenant's next invoice.
        </p>
      )}

      <h2 className="mt-8 mb-3 text-lg font-semibold">Charges</h2>
      <ChargesTable charges={charges} invoice={invoice} />

      <OtherUnpaid detail={detail} />

      {(actions.has('RECORD_PAYMENT') || detail.payments.length > 0) && (
        <Payments detail={detail} canRecord={actions.has('RECORD_PAYMENT')} canDelete={actions.has('DELETE_PAYMENT')} onChanged={onChanged} />
      )}

      <PdfPreview invoice={invoice} />

      <CancelLink invoice={invoice} canCancel={actions.has('CANCEL')} onCancel={() => run(cancelInvoice)} busy={move.busy} />
      {move.busy && <span className="sr-only" role="status">Working…</span>}
    </>
  )
}

function History({ invoice }) {
  const steps = [
    ['Created', invoice.invoiceCreatedAt],
    ['Email drafted', invoice.emailDraftedAt],
    ['Sent', invoice.sentAt],
    ['Archived', invoice.archivedAt],
  ].filter(([, at]) => at)
  const period = formatPeriod(invoice.periodStart, invoice.periodEnd)
  return (
    <p className="mt-1 text-sm text-ink-muted">
      {[period && `Covers ${period}`, ...steps.map(([label, at]) => `${label} ${formatDateTime(at)}`)]
        .filter(Boolean)
        .join(' · ')}
    </p>
  )
}

function ChargesTable({ charges, invoice }) {
  if (charges.length === 0) {
    return (
      <EmptyState title="No charges yet">
        Charges appear when a bill for this unit is verified for days this tenant lived there.
      </EmptyState>
    )
  }
  const paid = Number(invoice.received) > 0
  return (
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
                {charge.utilityType && <span className="block text-xs text-ink-muted">{utilityTypeLabels[charge.utilityType]}</span>}
              </td>
              <td className="px-4 py-3">{formatPeriod(charge.billPeriodStart, charge.billPeriodEnd)}</td>
              <td className="px-4 py-3 text-right tabular-nums">{charge.splitAmount != null ? formatMoney(charge.splitAmount) : '—'}</td>
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
            <th scope="row" colSpan={5} className="px-4 py-2 text-right font-semibold">Total</th>
            <td className="px-4 py-2 text-right text-base font-semibold tabular-nums">{formatMoney(invoice.totalAmount)}</td>
          </tr>
          {paid && (
            <>
              <tr>
                <th scope="row" colSpan={5} className="px-4 py-1 text-right font-normal text-ink-muted">Received</th>
                <td className="px-4 py-1 text-right tabular-nums text-ink-muted">−{formatMoney(invoice.received)}</td>
              </tr>
              <tr>
                <th scope="row" colSpan={5} className="px-4 py-2 text-right font-semibold">Still owed</th>
                <td className="px-4 py-2 text-right font-semibold tabular-nums">{formatMoney(invoice.remaining)}</td>
              </tr>
            </>
          )}
        </tfoot>
      </table>
    </div>
  )
}

function OtherUnpaid({ detail }) {
  if (detail.otherUnpaid.length === 0) return null
  return (
    <div className="mt-4 rounded-lg border border-attention/40 bg-attention/5 p-4 text-sm">
      <p className="font-medium">Also unpaid for this tenant (not part of this invoice's total)</p>
      <ul className="mt-2 space-y-1">
        {detail.otherUnpaid.map((other) => (
          <li key={other.id} className="flex justify-between gap-3">
            <Link to={`/invoices/${other.id}`} className="text-action underline-offset-4 hover:underline">{other.invoiceNumber}</Link>
            <span className="tabular-nums">{formatMoney(other.remaining)}</span>
          </li>
        ))}
      </ul>
      <p className="mt-2 flex justify-between border-t border-attention/30 pt-2 font-semibold">
        <span>Total owing</span>
        <span className="tabular-nums">{formatMoney(detail.totalOwing)}</span>
      </p>
    </div>
  )
}

function Payments({ detail, canRecord, canDelete, onChanged }) {
  const { invoice, payments } = detail
  const [values, setValues] = useState({ amount: '', paidOn: todayIso(), note: '' })
  const save = useFormSubmit()
  const remove = useFormSubmit()

  async function handleRecord(event) {
    event.preventDefault()
    let updated = null
    const ok = await save.submit(async () => {
      updated = await recordPayment(invoice.id, {
        amount: values.amount === '' ? null : Number(values.amount),
        paidOn: values.paidOn || null,
        note: values.note.trim() || null,
      })
    })
    if (ok) {
      setValues({ amount: '', paidOn: todayIso(), note: '' })
      onChanged(updated)
    }
  }

  async function handleDelete(payment) {
    if (!window.confirm(`Delete the ${formatMoney(payment.amount)} payment from ${formatDate(payment.paidOn)}?`)) return
    let updated = null
    if (await remove.submit(async () => { updated = await deletePayment(payment.id) })) onChanged(updated)
  }

  return (
    <div className="mt-8">
      <h2 className="mb-3 text-lg font-semibold">Payments</h2>
      {payments.length > 0 && (
        <ul className="mb-4 divide-y divide-line rounded-lg border border-line bg-panel text-sm">
          {payments.map((payment) => (
            <li key={payment.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-2">
              <span>
                <span className="font-medium tabular-nums">{formatMoney(payment.amount)}</span>
                <span className="ml-2 text-ink-muted">received {formatDate(payment.paidOn)}{payment.note && ` · ${payment.note}`}</span>
              </span>
              {canDelete && (
                <Button variant="link" className="text-danger" onClick={() => handleDelete(payment)} disabled={remove.busy}>
                  Delete
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      <FormProblem error={remove.error} />

      {canRecord && (
        <form noValidate onSubmit={handleRecord} className="rounded-lg border border-line bg-panel p-4">
          <div className="grid gap-4 sm:grid-cols-[10rem_11rem_1fr]">
            <TextField
              label="Amount received ($)"
              type="number"
              inputMode="decimal"
              step="0.01"
              min="0.01"
              value={values.amount}
              onChange={(e) => setValues((v) => ({ ...v, amount: e.target.value }))}
              error={save.fieldErrors.amount}
            />
            <TextField
              label="Received on"
              type="date"
              max={todayIso()}
              value={values.paidOn}
              onChange={(e) => setValues((v) => ({ ...v, paidOn: e.target.value }))}
              error={save.fieldErrors.paidOn}
            />
            <TextField
              label="Note (optional)"
              value={values.note}
              maxLength={500}
              onChange={(e) => setValues((v) => ({ ...v, note: e.target.value }))}
              error={save.fieldErrors.note}
              placeholder="e.g. e-transfer"
            />
          </div>
          <p className="mt-2 text-sm">
            <button
              type="button"
              className="text-action underline underline-offset-4"
              onClick={() => setValues((v) => ({ ...v, amount: Number(invoice.remaining).toFixed(2) }))}
            >
              Fill in the full amount still owed ({formatMoney(invoice.remaining)})
            </button>
          </p>
          <div className="mt-3 space-y-2">
            <FormProblem error={save.error} />
            <Button type="submit" disabled={save.busy}>{save.busy ? 'Saving…' : 'Record payment'}</Button>
            <p className="text-xs text-ink-muted">
              Less than what's owed makes it partially paid; the full amount marks it paid.
            </p>
          </div>
        </form>
      )}
    </div>
  )
}

function CancelLink({ invoice, canCancel, onCancel, busy }) {
  if (canCancel) {
    return (
      <p className="mt-10 border-t border-line pt-4 text-xs text-ink-muted">
        Sent by mistake?{' '}
        <button
          type="button"
          className="text-danger underline underline-offset-4 disabled:opacity-60"
          disabled={busy}
          onClick={() => {
            if (window.confirm(`Cancel ${invoice.invoiceNumber}? It stays in your records as cancelled, and its charges go back to the tenant's next invoice. Only for invoices sent by mistake.`)) onCancel()
          }}
        >
          Cancel invoice
        </button>
      </p>
    )
  }
  if (invoice.status === 'PARTIALLY_PAID') {
    return (
      <p className="mt-10 border-t border-line pt-4 text-xs text-ink-muted">
        Sent by mistake? Delete its payments first; then it can be cancelled.
      </p>
    )
  }
  return null
}

function PdfButtons({ invoice }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  async function download() {
    setBusy(true)
    setError(null)
    try {
      const blob = await fetchInvoicePdf(invoice.id)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${invoice.invoiceNumber}.pdf`
      link.click()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <Button variant="secondary" onClick={download} disabled={busy}>{busy ? 'Preparing…' : 'Download PDF'}</Button>
      {error && <span className="text-sm text-danger">{error.message}</span>}
    </>
  )
}

/** The PDF, fetched with the sign-in token and shown from a temporary blob URL. */
function PdfPreview({ invoice }) {
  const [open, setOpen] = useState(false)
  const [state, setState] = useState({ url: null, error: null })
  const version = `${invoice.status}-${invoice.totalAmount}-${invoice.received}-${invoice.dueDate}`

  useEffect(() => {
    if (!open) return undefined
    let url = null
    let cancelled = false
    setState({ url: null, error: null })
    fetchInvoicePdf(invoice.id)
      .then((blob) => {
        if (cancelled) return
        url = URL.createObjectURL(blob)
        setState({ url, error: null })
      })
      .catch((error) => !cancelled && setState({ url: null, error }))
    return () => {
      cancelled = true
      if (url) URL.revokeObjectURL(url)
    }
  }, [open, invoice.id, version])

  return (
    <div className="mt-8">
      <Button variant="link" onClick={() => setOpen((o) => !o)}>{open ? 'Hide PDF preview' : 'Show PDF preview'}</Button>
      {open && (
        <div className="mt-3">
          {!state.url && !state.error && <LoadingState label="Preparing the PDF…" />}
          {state.error && <ErrorState error={state.error} />}
          {state.url && (
            <iframe title={`${invoice.invoiceNumber} PDF`} src={state.url} className="h-[48rem] w-full rounded-lg border border-line bg-white" />
          )}
        </div>
      )}
    </div>
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
        <p className="font-medium">{formatDate(invoice.dueDate) || '—'}</p>
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
