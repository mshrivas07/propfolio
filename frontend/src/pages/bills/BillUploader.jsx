import { useRef, useState } from 'react'
import { Link } from 'react-router'
import Badge from '../../components/ui/Badge'
import Button from '../../components/ui/Button'
import { SelectField } from '../../components/ui/Field'
import { MAX_BILL_BYTES, uploadBill } from '../../lib/api'
import { formatFileSize, utilityTypeLabels } from '../../lib/format'

const ACCEPT = '.pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg'
const ACCEPTED_TYPES = ['application/pdf', 'image/png', 'image/jpeg']
const ACCEPTED_EXTENSIONS = /\.(pdf|png|jpe?g)$/i

const itemTone = { queued: 'neutral', uploading: 'info', done: 'success', duplicate: 'attention', error: 'danger' }
const itemLabel = { queued: 'Waiting', uploading: 'Uploading…', done: 'Uploaded', duplicate: 'Already uploaded', error: 'Failed' }

/**
 * Drop or pick bill files; they upload one at a time, each with its own status.
 * The server re-checks every file (type by signature, size, duplicates).
 */
export default function BillUploader({ accounts, onUploaded }) {
  const [accountId, setAccountId] = useState('')
  const [items, setItems] = useState([])
  const [dragging, setDragging] = useState(false)
  const [busy, setBusy] = useState(false)
  const inputRef = useRef(null)
  const nextKey = useRef(0)

  const update = (key, changes) => setItems((list) => list.map((item) => (item.key === key ? { ...item, ...changes } : item)))

  async function addFiles(fileList) {
    const files = Array.from(fileList ?? [])
    if (files.length === 0) return
    const batch = files.map((file) => ({ key: nextKey.current++, file, ...precheck(file) }))
    setItems((list) => [...batch, ...list])

    setBusy(true)
    let uploadedAny = false
    for (const item of batch) {
      if (item.status !== 'queued') continue
      update(item.key, { status: 'uploading' })
      try {
        const bill = await uploadBill(item.file, accountId || undefined)
        update(item.key, { status: 'done', billId: bill.id })
        uploadedAny = true
      } catch (error) {
        if (error.status === 409 && error.problem?.existingBillId) {
          update(item.key, { status: 'duplicate', message: error.message, billId: error.problem.existingBillId })
        } else {
          const message = error.fieldErrors?.file ?? error.fieldErrors?.utilityAccountId ?? error.message
          update(item.key, { status: 'error', message })
        }
      }
    }
    setBusy(false)
    if (uploadedAny) onUploaded()
  }

  function handleDrop(event) {
    event.preventDefault()
    setDragging(false)
    addFiles(event.dataTransfer.files)
  }

  return (
    <div className="rounded-lg border border-line bg-panel p-4">
      <div className="grid gap-4 md:grid-cols-[1fr_18rem] md:items-start">
        <div
          onDragOver={(event) => {
            event.preventDefault()
            setDragging(true)
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={handleDrop}
          className={`flex flex-col items-center justify-center rounded-md border-2 border-dashed px-4 py-8 text-center transition-colors ${
            dragging ? 'border-action bg-action/5' : 'border-line'
          }`}
        >
          <p className="font-medium">Drop bill files here</p>
          <p className="mt-1 text-sm text-ink-muted">PDF, PNG or JPG, up to 10 MB each</p>
          <Button variant="secondary" className="mt-4" onClick={() => inputRef.current?.click()}>
            Choose files
          </Button>
          <input
            ref={inputRef}
            type="file"
            multiple
            accept={ACCEPT}
            className="sr-only"
            tabIndex={-1}
            aria-hidden="true"
            onChange={(event) => {
              addFiles(event.target.files)
              event.target.value = '' // allow picking the same file again
            }}
          />
        </div>

        <SelectField
          label="Utility account (optional)"
          hint="Applies to the files you add next. Leave empty if they're for different accounts."
          placeholder="Not sure yet"
          value={accountId}
          onChange={(event) => setAccountId(event.target.value)}
          disabled={busy}
          options={accounts.map((a) => ({
            value: a.id,
            label: `${a.propertyName} · ${a.providerName} (${utilityTypeLabels[a.utilityType]})`,
          }))}
        />
      </div>

      {items.length > 0 && (
        <div className="mt-4">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold">This session's uploads</h3>
            {!busy && (
              <Button variant="link" onClick={() => setItems([])}>
                Clear list
              </Button>
            )}
          </div>
          <ul className="mt-2 divide-y divide-line rounded-md border border-line" aria-live="polite">
            {items.map((item) => (
              <li key={item.key} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 text-sm">
                <span className="min-w-0">
                  <span className="block truncate font-medium">{item.file.name}</span>
                  <span className="text-xs text-ink-muted">
                    {formatFileSize(item.file.size)}
                    {item.message && ` · ${item.message}`}
                  </span>
                </span>
                <span className="flex items-center gap-3">
                  {item.billId && (
                    <Link to={`/bills/${item.billId}`} className="text-action underline-offset-4 hover:underline">
                      {item.status === 'duplicate' ? 'View existing' : 'View'}
                    </Link>
                  )}
                  <Badge tone={itemTone[item.status]}>{itemLabel[item.status]}</Badge>
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

/** Quick checks so obvious problems show at once instead of after an upload. */
function precheck(file) {
  if (file.size === 0) return { status: 'error', message: 'The file is empty' }
  if (file.size > MAX_BILL_BYTES) return { status: 'error', message: 'Files can be at most 10 MB' }
  if (!ACCEPTED_TYPES.includes(file.type) && !ACCEPTED_EXTENSIONS.test(file.name)) {
    return { status: 'error', message: 'Upload a PDF, PNG or JPG file' }
  }
  return { status: 'queued' }
}
