import { useState } from 'react'
import Button from '../../components/ui/Button'
import { TextField } from '../../components/ui/Field'
import FormProblem from '../../components/ui/FormProblem'
import { useFormSubmit } from '../../hooks/useFormSubmit'

const empty = { name: '', addressLine1: '', addressLine2: '', city: '', province: '', postalCode: '' }

/** Create or edit a property. `onSave(body)` must return a promise. */
export default function PropertyForm({ initial, submitLabel, onSave, onCancel }) {
  const [values, setValues] = useState({ ...empty, ...initial })
  const { busy, error, fieldErrors, submit } = useFormSubmit()

  const set = (field) => (event) => setValues((v) => ({ ...v, [field]: event.target.value }))

  async function handleSubmit(event) {
    event.preventDefault()
    // Send only the editable fields, not ids or counts from the loaded property.
    const body = Object.fromEntries(Object.keys(empty).map((key) => [key, values[key] ?? '']))
    await submit(() => onSave(body))
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="grid gap-4 sm:grid-cols-2">
      <TextField
        label="Name"
        hint="How you refer to it, e.g. the street address"
        value={values.name}
        onChange={set('name')}
        error={fieldErrors.name}
        className="sm:col-span-2"
        autoFocus
      />
      <TextField label="Address line 1" value={values.addressLine1 ?? ''} onChange={set('addressLine1')} error={fieldErrors.addressLine1} />
      <TextField label="Address line 2" value={values.addressLine2 ?? ''} onChange={set('addressLine2')} error={fieldErrors.addressLine2} />
      <TextField label="City" value={values.city ?? ''} onChange={set('city')} error={fieldErrors.city} />
      <div className="grid grid-cols-2 gap-4">
        <TextField label="Province" value={values.province ?? ''} onChange={set('province')} error={fieldErrors.province} />
        <TextField label="Postal code" value={values.postalCode ?? ''} onChange={set('postalCode')} error={fieldErrors.postalCode} />
      </div>
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
