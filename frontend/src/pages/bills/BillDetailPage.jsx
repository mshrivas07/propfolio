import { useCallback } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import Badge from '../../components/ui/Badge'
import Button from '../../components/ui/Button'
import FormProblem from '../../components/ui/FormProblem'
import { ErrorState, LoadingState } from '../../components/ui/States'
import { useApiData } from '../../hooks/useApiData'
import { useFormSubmit } from '../../hooks/useFormSubmit'
import { deleteBill, getBill } from '../../lib/api'
import {
  billStatusLabels,
  billStatusTones,
  formatDateTime,
  formatFileSize,
  utilityTypeLabels,
} from '../../lib/format'
import BillPreview from './BillPreview'

export default function BillDetailPage() {
  const { billId } = useParams()
  const load = useCallback(() => getBill(billId), [billId])
  const { data: bill, error, loading, reload } = useApiData(load)
  const remove = useFormSubmit()
  const navigate = useNavigate()

  if (loading && !bill) return <LoadingState />
  if (error && !bill) {
    return (
      <>
        <BackLink />
        <div className="mt-4"><ErrorState error={error} onRetry={reload} /></div>
      </>
    )
  }

  async function handleDelete() {
    if (!window.confirm(`Delete ${bill.originalFilename}? The file is removed too.`)) return
    if (await remove.submit(() => deleteBill(bill.id))) navigate('/bills', { replace: true })
  }

  return (
    <div>
      <BackLink />
      <div className="mt-4 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="break-words text-2xl font-semibold">{bill.originalFilename}</h1>
          <p className="mt-1 flex flex-wrap items-center gap-2 text-sm text-ink-muted">
            <Badge tone={billStatusTones[bill.status]}>{billStatusLabels[bill.status]}</Badge>
            <span>
              Uploaded {formatDateTime(bill.createdAt)} · {formatFileSize(bill.fileSizeBytes)}
            </span>
          </p>
        </div>
        {bill.status !== 'VERIFIED' && (
          <Button variant="danger" onClick={handleDelete} disabled={remove.busy}>
            Delete bill
          </Button>
        )}
      </div>
      <div className="mt-3"><FormProblem error={remove.error} /></div>

      <div className="mt-6 grid gap-6 lg:grid-cols-[18rem_1fr]">
        <dl className="h-fit space-y-3 rounded-lg border border-line bg-panel p-4 text-sm">
          <Detail label="Property">
            {bill.propertyId ? (
              <Link to={`/properties/${bill.propertyId}`} className="text-action underline-offset-4 hover:underline">
                {bill.propertyName}
              </Link>
            ) : (
              'Not linked yet'
            )}
          </Detail>
          <Detail label="Utility account">
            {bill.providerName
              ? `${bill.providerName}${bill.utilityType ? ` (${utilityTypeLabels[bill.utilityType]})` : ''}`
              : 'Not linked yet'}
          </Detail>
          {bill.accountNumber && <Detail label="Account number">{bill.accountNumber}</Detail>}
          <p className="border-t border-line pt-3 text-xs text-ink-muted">
            The billing period, amount and due date are read from the bill in the next step.
          </p>
        </dl>

        <BillPreview bill={bill} />
      </div>
    </div>
  )
}

function Detail({ label, children }) {
  return (
    <div>
      <dt className="text-ink-muted">{label}</dt>
      <dd className="font-medium">{children}</dd>
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
