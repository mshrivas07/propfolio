import { useCallback, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import Badge from '../../components/ui/Badge'
import Button from '../../components/ui/Button'
import FormProblem from '../../components/ui/FormProblem'
import { ErrorState, LoadingState } from '../../components/ui/States'
import { useApiData, useInterval } from '../../hooks/useApiData'
import { useFormSubmit } from '../../hooks/useFormSubmit'
import { deleteBill, extractBill, getBill, listAllUtilityAccounts, unverifyBill } from '../../lib/api'
import { billBadge, formatDateTime, formatFileSize } from '../../lib/format'
import BillPreview from './BillPreview'
import BillReviewForm, { fieldId } from './BillReviewForm'

const POLL_MS = 3000

/** The review screen: the bill's details beside the original file. */
export default function BillDetailPage() {
  const { billId } = useParams()
  const load = useCallback(() => getBill(billId), [billId])
  const { data: bill, error, loading, reload, setData } = useApiData(load)
  const accounts = useApiData(listAllUtilityAccounts)
  const action = useFormSubmit()
  const navigate = useNavigate()
  // Bumped whenever fresh data should reset the form (reading finished, saved, verified...).
  const [formVersion, setFormVersion] = useState(0)

  const reading = bill?.extractionState === 'RUNNING'
  useInterval(
    async () => {
      const fresh = await getBill(billId).catch(() => null)
      if (fresh && fresh.extractionState !== 'RUNNING') {
        setData(fresh)
        setFormVersion((v) => v + 1)
      }
    },
    POLL_MS,
    reading,
  )

  if (loading && !bill) return <LoadingState />
  if (error && !bill) {
    return (
      <>
        <BackLink />
        <div className="mt-4"><ErrorState error={error} onRetry={reload} /></div>
      </>
    )
  }

  function replace(updated) {
    setData(updated)
    setFormVersion((v) => v + 1)
  }

  async function run(call) {
    let updated = null
    const ok = await action.submit(async () => {
      updated = await call()
    })
    if (ok) replace(updated)
  }

  async function handleReadAgain() {
    const reviewed = bill.status === 'READY' || bill.status === 'NEEDS_REVIEW'
    if (reviewed && !window.confirm('Read the bill again? The details below, including your changes, will be replaced.')) {
      return
    }
    await run(() => extractBill(bill.id))
  }

  async function handleDelete() {
    if (!window.confirm(`Delete ${bill.originalFilename}? The file is removed too.`)) return
    if (await action.submit(() => deleteBill(bill.id))) navigate('/bills', { replace: true })
  }

  const badge = billBadge(bill)
  const verified = bill.status === 'VERIFIED'
  const failed = bill.extractionState === 'FAILED' && bill.status === 'UPLOADED'
  const notRead = bill.extractionState === 'NOT_STARTED' && bill.status === 'UPLOADED'

  return (
    <div>
      <BackLink />
      <div className="mt-4 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="break-words text-2xl font-semibold">{bill.providerName ?? bill.originalFilename}</h1>
          <p className="mt-1 flex flex-wrap items-center gap-2 text-sm text-ink-muted">
            <Badge tone={badge.tone}>{badge.label}</Badge>
            <span>
              {bill.originalFilename} · {formatFileSize(bill.fileSizeBytes)} · uploaded {formatDateTime(bill.createdAt)}
            </span>
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {verified ? (
            <Button variant="secondary" onClick={() => run(() => unverifyBill(bill.id))} disabled={action.busy}>
              Un-verify
            </Button>
          ) : (
            <>
              {!reading && !notRead && !failed && (
                <Button variant="secondary" onClick={handleReadAgain} disabled={action.busy}>
                  Read again
                </Button>
              )}
              <Button variant="danger" onClick={handleDelete} disabled={action.busy}>
                Delete bill
              </Button>
            </>
          )}
        </div>
      </div>
      <div className="mt-3"><FormProblem error={action.error} /></div>

      <div className="mt-4 grid gap-6 lg:grid-cols-[minmax(0,28rem)_1fr]">
        <div className="space-y-4">
          {reading && (
            <Notice tone="info" title="Reading this bill…">
              This usually takes less than a minute. The details appear here when it's done.
            </Notice>
          )}
          {notRead && (
            <Notice tone="neutral" title="This bill hasn't been read yet.">
              <Button className="mt-2" onClick={() => run(() => extractBill(bill.id))} disabled={action.busy}>
                Read bill
              </Button>
            </Notice>
          )}
          {failed && (
            <Notice tone="danger" title="Couldn't read this bill.">
              <p>{bill.extractionError}</p>
              <p className="mt-1">Try again, or fill in the details yourself below.</p>
              <Button className="mt-2" onClick={() => run(() => extractBill(bill.id))} disabled={action.busy}>
                Try again
              </Button>
            </Notice>
          )}
          {verified && (
            <Notice tone="success" title={`Verified on ${formatDateTime(bill.verifiedAt)}`}>
              It can go on invoices. Un-verify it to make changes.
            </Notice>
          )}
          {!verified && !reading && bill.issues.length > 0 && <IssueList issues={bill.issues} />}
          {!verified && !reading && bill.status === 'READY' && (
            <Notice tone="info" title="Everything checks out.">
              Compare the details with the bill, then verify it.
            </Notice>
          )}

          <div className="rounded-lg border border-line bg-panel p-4">
            <BillReviewForm
              key={`${bill.id}-${formVersion}`}
              bill={bill}
              accounts={accounts.data ?? []}
              disabled={reading || action.busy}
              onSaved={replace}
            />
          </div>
        </div>

        <div className="lg:sticky lg:top-6 lg:self-start">
          <BillPreview bill={bill} />
        </div>
      </div>
    </div>
  )
}

const noticeTones = {
  info: 'border-action/30 bg-action/5',
  neutral: 'border-line bg-panel',
  danger: 'border-danger/30 bg-danger/5',
  success: 'border-paid/30 bg-paid/5',
  attention: 'border-attention/40 bg-attention/5',
}

function Notice({ tone, title, children }) {
  return (
    <div role={tone === 'danger' ? 'alert' : 'status'} className={`rounded-lg border p-4 text-sm ${noticeTones[tone]}`}>
      <p className="font-medium">{title}</p>
      {children && <div className="mt-1 text-ink-muted">{children}</div>}
    </div>
  )
}

const fieldLabels = {
  utilityAccountId: 'Utility account',
  providerName: 'Provider',
  accountNumber: 'Account number',
  periodStart: 'Billing period',
  periodEnd: 'Billing period',
  currentCharges: 'Current charges',
  previousBalance: 'Previous balance',
  amountDue: 'Amount due',
  dueDate: 'Due date',
}

function IssueList({ issues }) {
  return (
    <div className={`rounded-lg border p-4 text-sm ${noticeTones.attention}`}>
      <p className="font-medium">Check before verifying</p>
      <ul className="mt-2 space-y-2">
        {issues.map((issue, index) => (
          <li key={index} className="text-ink-muted">
            {issue.field ? (
              <button
                type="button"
                onClick={() => document.getElementById(fieldId(issue.field))?.focus()}
                className="mr-1 font-medium text-attention underline-offset-4 hover:underline"
              >
                {fieldLabels[issue.field] ?? issue.field}:
              </button>
            ) : null}
            {issue.message}
          </li>
        ))}
      </ul>
    </div>
  )
}

function BackLink() {
  return (
    <Link to="/bills" className="text-sm text-action underline-offset-4 hover:underline">
      ← All bills
    </Link>
  )
}
