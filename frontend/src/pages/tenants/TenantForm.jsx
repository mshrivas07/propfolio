import { useState } from 'react'
import Button from '../../components/ui/Button'
import { TextField } from '../../components/ui/Field'
import FormProblem from '../../components/ui/FormProblem'
import { useFormSubmit } from '../../hooks/useFormSubmit'

const MAX_EMAILS = 10

/** Create or edit a tenant. Roommates sharing a unit go in one tenant with several emails. */
export default function TenantForm({ initial, submitLabel, onSave, onCancel }) {
  const [fullName, setFullName] = useState(initial?.fullName ?? '')
  const [emails, setEmails] = useState(initial?.emails?.length ? initial.emails : [''])
  const [phone, setPhone] = useState(initial?.phone ?? '')
  const [notes, setNotes] = useState(initial?.notes ?? '')
  const { busy, error, fieldErrors, submit } = useFormSubmit()

  const setEmail = (index, value) => setEmails((list) => list.map((e, i) => (i === index ? value : e)))
  const removeEmail = (index) => setEmails((list) => list.filter((_, i) => i !== index))

  async function handleSubmit(event) {
    event.preventDefault()
    // Blank rows are dropped here; the server also trims, lower-cases and removes duplicates.
    const cleaned = emails.map((e) => e.trim()).filter(Boolean)
    await submit(() => onSave({ fullName, emails: cleaned, phone, notes }))
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="grid gap-4 sm:grid-cols-2">
      <TextField label="Name" value={fullName} onChange={(e) => setFullName(e.target.value)} error={fieldErrors.fullName} autoFocus />
      <TextField label="Phone (optional)" type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} error={fieldErrors.phone} />

      <fieldset className="sm:col-span-2">
        <legend className="text-sm font-medium">Email addresses</legend>
        <p className="text-xs text-ink-muted">Invoices go to every address listed.</p>
        <div className="mt-2 space-y-2">
          {emails.map((email, index) => (
            <div key={index} className="flex items-start gap-2">
              <TextField
                label={`Email ${index + 1}`}
                type="email"
                autoComplete="off"
                value={email}
                onChange={(e) => setEmail(index, e.target.value)}
                error={fieldErrors[`emails[${index}]`]}
                className="flex-1"
              />
              {emails.length > 1 && (
                <Button variant="link" className="mt-8 text-danger" onClick={() => removeEmail(index)} aria-label={`Remove email ${index + 1}`}>
                  Remove
                </Button>
              )}
            </div>
          ))}
        </div>
        {fieldErrors.emails && <p className="mt-1 text-xs text-danger">{fieldErrors.emails}</p>}
        {emails.length < MAX_EMAILS && (
          <Button variant="link" className="mt-2" onClick={() => setEmails((list) => [...list, ''])}>
            Add another email
          </Button>
        )}
      </fieldset>

      <TextField label="Notes (optional)" value={notes} onChange={(e) => setNotes(e.target.value)} error={fieldErrors.notes} className="sm:col-span-2" />

      <div className="space-y-3 sm:col-span-2">
        <FormProblem error={error} />
        <div className="flex gap-2">
          <Button type="submit" disabled={busy}>{busy ? 'Saving…' : submitLabel}</Button>
          <Button variant="secondary" onClick={onCancel} disabled={busy}>Cancel</Button>
        </div>
      </div>
    </form>
  )
}
