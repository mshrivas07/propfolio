import { useMemo, useState } from 'react'
import Button from '../../components/ui/Button'
import { SelectField, TextField } from '../../components/ui/Field'
import FormProblem from '../../components/ui/FormProblem'
import { useFormSubmit } from '../../hooks/useFormSubmit'
import { updateBillFields, verifyBill } from '../../lib/api'
import { utilityTypeLabels } from '../../lib/format'

const FIELDS = [
  { name: 'providerName', label: 'Provider' },
  { name: 'accountNumber', label: 'Account number' },
  { name: 'periodStart', label: 'Billing period starts', type: 'date' },
  { name: 'periodEnd', label: 'Billing period ends', type: 'date' },
  {
    name: 'currentCharges',
    label: 'Current charges ($)',
    type: 'number',
    note: 'This period only, including tax.',
  },
  { name: 'previousBalance', label: 'Previous balance ($)', type: 'number', note: 'Carried over from earlier bills.' },
  { name: 'amountDue', label: 'Amount due ($)', type: 'number' },
  { name: 'dueDate', label: 'Due date', type: 'date' },
]
const NUMBER_FIELDS = new Set(['currentCharges', 'previousBalance', 'amountDue'])

/** Element id for a field, so review issues can jump to it. */
export const fieldId = (name) => `bill-field-${name}`

function initialValues(bill) {
  const values = { utilityAccountId: bill.utilityAccountId ?? '' }
  for (const { name } of FIELDS) {
    const source = name === 'providerName' ? bill.billProviderName : name === 'accountNumber' ? bill.billAccountNumber : bill[name]
    values[name] = source == null ? '' : NUMBER_FIELDS.has(name) ? Number(source).toFixed(2) : String(source)
  }
  return values
}

function toRequest(values) {
  const body = { utilityAccountId: values.utilityAccountId || null }
  for (const { name } of FIELDS) {
    const value = values[name].trim()
    body[name] = value === '' ? null : NUMBER_FIELDS.has(name) ? Number(value) : value
  }
  return body
}

/**
 * The editable details of a bill, with how sure the reader was about each one.
 * Saving and verifying both return the updated bill, passed to `onSaved`.
 */
export default function BillReviewForm({ bill, accounts, disabled, onSaved }) {
  const initial = useMemo(() => initialValues(bill), [bill])
  const [values, setValues] = useState(initial)
  const save = useFormSubmit()
  const locked = disabled || bill.status === 'VERIFIED'
  const dirty = JSON.stringify(values) !== JSON.stringify(initial)

  const set = (name) => (event) => setValues((v) => ({ ...v, [name]: event.target.value }))

  async function handleSave(event) {
    event?.preventDefault()
    let updated = null
    const ok = await save.submit(async () => {
      updated = await updateBillFields(bill.id, toRequest(values))
    })
    if (ok) onSaved(updated)
    return ok
  }

  async function handleVerify() {
    let updated = null
    const ok = await save.submit(async () => {
      if (dirty) await updateBillFields(bill.id, toRequest(values))
      updated = await verifyBill(bill.id)
    })
    if (ok) onSaved(updated)
  }

  const chosenAccount = accounts.find((a) => a.id === values.utilityAccountId)
  const onPlan = chosenAccount ? Boolean(chosenAccount.equalPaymentPlan) : Boolean(bill.equalPaymentPlan)
  const review = bill.fieldReview ?? {}
  const read = Boolean(bill.extractedAt)

  return (
    <form onSubmit={handleSave} noValidate className="space-y-4">
      <SelectField
        id={fieldId('utilityAccountId')}
        label="Utility account"
        placeholder="Choose the account this bill is for"
        value={values.utilityAccountId}
        onChange={set('utilityAccountId')}
        disabled={locked}
        error={save.fieldErrors.utilityAccountId}
        hint={
          bill.billUtilityType && (
            <>Looks like {article(utilityTypeLabels[bill.billUtilityType].toLowerCase())} bill.</>
          )
        }
        options={accounts.map((a) => ({
          value: a.id,
          label: `${a.propertyName} · ${a.providerName} (${utilityTypeLabels[a.utilityType]})${
            a.accountNumber ? ` · ${a.accountNumber}` : ''
          }`,
        }))}
      />

      <div className="grid gap-4 sm:grid-cols-2">
        {FIELDS.map((field) => {
          const info = review[field.name]
          const splitField = (onPlan ? 'amountDue' : 'currentCharges') === field.name
          const note = splitField ? [field.note, "It's the amount split with tenants."].filter(Boolean).join(' ') : field.note
          return (
            <TextField
              key={field.name}
              id={fieldId(field.name)}
              label={field.label}
              type={field.type ?? 'text'}
              inputMode={field.type === 'number' ? 'decimal' : undefined}
              step={field.type === 'number' ? '0.01' : undefined}
              value={values[field.name]}
              onChange={set(field.name)}
              disabled={locked}
              error={save.fieldErrors[field.name]}
              inputClassName={info?.lowConfidence ? 'ring-2 ring-attention/60 bg-attention/5' : ''}
              className={field.name === 'currentCharges' ? 'sm:col-span-2' : ''}
              hint={<FieldHint info={info} read={read} note={note} />}
            />
          )
        })}
      </div>

      {!locked && (
        <div className="space-y-3 border-t border-line pt-4">
          <FormProblem error={save.error} />
          <div className="flex flex-wrap gap-2">
            <Button onClick={handleVerify} disabled={save.busy}>
              {save.busy ? 'Saving…' : dirty ? 'Save and verify' : 'Verify bill'}
            </Button>
            <Button type="submit" variant="secondary" disabled={save.busy || !dirty}>
              Save changes
            </Button>
          </div>
          <p className="text-xs text-ink-muted">Only verified bills go on invoices.</p>
        </div>
      )}
    </form>
  )
}

function FieldHint({ info, read, note }) {
  let status = null
  if (read && info?.edited) {
    status = <span>Changed by you</span>
  } else if (info?.lowConfidence) {
    status = (
      <span className="font-medium text-attention">
        Only {Math.round(Number(info.confidence) * 100)}% sure. Check it against the bill.
      </span>
    )
  } else if (info?.confidence != null) {
    status = <span>{Math.round(Number(info.confidence) * 100)}% sure</span>
  }
  if (!status && !note) return null
  return (
    <>
      {status}
      {status && note && ' · '}
      {note}
    </>
  )
}

function article(word) {
  return /^[aeiou]/i.test(word) ? `an ${word}` : `a ${word}`
}
