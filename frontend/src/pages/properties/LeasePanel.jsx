import { useState } from 'react'
import { Link } from 'react-router'
import Button from '../../components/ui/Button'
import { SelectField, TextField } from '../../components/ui/Field'
import FormProblem from '../../components/ui/FormProblem'
import { useApiData } from '../../hooks/useApiData'
import { useFormSubmit } from '../../hooks/useFormSubmit'
import { createLease, deleteLease, listTenants, updateLease } from '../../lib/api'
import { formatDate } from '../../lib/format'

const statusStyles = {
  CURRENT: 'bg-paid/10 text-paid',
  UPCOMING: 'bg-action/10 text-action',
  ENDED: 'bg-surface text-ink-muted',
}
const statusLabels = { CURRENT: 'Current', UPCOMING: 'Upcoming', ENDED: 'Ended' }

/** Who lives in the unit: lease history plus add / end / delete. */
export default function LeasePanel({ unit, onChanged }) {
  const [adding, setAdding] = useState(false)
  const leases = unit.leases ?? []

  return (
    <div className="mt-4 border-t border-line pt-4">
      <div className="flex items-center justify-between gap-2">
        <h4 className="text-sm font-semibold">Leases</h4>
        {!adding && <Button variant="link" onClick={() => setAdding(true)}>Add lease</Button>}
      </div>

      {leases.length === 0 && !adding && (
        <p className="mt-2 text-sm text-ink-muted">Vacant. Add a lease to say who lives here.</p>
      )}
      {leases.length > 0 && (
        <ul className="mt-2 space-y-2">
          {leases.map((lease) => (
            <LeaseRow key={lease.id} lease={lease} onChanged={onChanged} />
          ))}
        </ul>
      )}

      {adding && (
        <NewLeaseForm
          unit={unit}
          onDone={() => {
            setAdding(false)
            onChanged()
          }}
          onCancel={() => setAdding(false)}
        />
      )}
    </div>
  )
}

function LeaseRow({ lease, onChanged }) {
  const [ending, setEnding] = useState(false)
  const [endDate, setEndDate] = useState(lease.endDate ?? '')
  const action = useFormSubmit()

  async function handleEnd(event) {
    event.preventDefault()
    const ok = await action.submit(() => updateLease(lease.id, { startDate: lease.startDate, endDate: endDate || null }))
    if (ok) {
      setEnding(false)
      onChanged()
    }
  }

  async function handleDelete() {
    if (!window.confirm(`Delete ${lease.tenantName}'s lease? Use "End lease" if they moved out.`)) return
    if (await action.submit(() => deleteLease(lease.id))) onChanged()
  }

  return (
    <li className="rounded-md bg-surface px-3 py-2 text-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="flex flex-wrap items-center gap-2">
          <span className={`rounded px-2 py-0.5 text-xs font-medium ${statusStyles[lease.status]}`}>
            {statusLabels[lease.status]}
          </span>
          <span className="font-medium">{lease.tenantName}</span>
          <span className="text-ink-muted">
            {formatDate(lease.startDate)} – {lease.endDate ? formatDate(lease.endDate) : 'no end date'}
          </span>
        </span>
        {!ending && (
          <span className="flex gap-3">
            {lease.status !== 'ENDED' && (
              <Button variant="link" onClick={() => setEnding(true)}>
                {lease.endDate ? 'Change end date' : 'End lease'}
              </Button>
            )}
            <Button variant="link" className="text-danger" onClick={handleDelete} disabled={action.busy}>Delete</Button>
          </span>
        )}
      </div>
      {ending && (
        <form onSubmit={handleEnd} noValidate className="mt-3 flex flex-wrap items-end gap-3">
          <TextField
            label="Last day of the lease"
            type="date"
            value={endDate}
            min={lease.startDate}
            onChange={(e) => setEndDate(e.target.value)}
            error={action.fieldErrors.endDate}
          />
          <Button type="submit" disabled={action.busy}>{action.busy ? 'Saving…' : 'Save'}</Button>
          <Button variant="secondary" onClick={() => setEnding(false)} disabled={action.busy}>Cancel</Button>
        </form>
      )}
      {action.error && <div className="mt-2"><FormProblem error={action.error} /></div>}
    </li>
  )
}

function NewLeaseForm({ unit, onDone, onCancel }) {
  const tenants = useApiData(listTenants)
  const [values, setValues] = useState({ tenantId: '', startDate: '', endDate: '' })
  const { busy, error, fieldErrors, submit } = useFormSubmit()
  const set = (field) => (event) => setValues((v) => ({ ...v, [field]: event.target.value }))

  async function handleSubmit(event) {
    event.preventDefault()
    const ok = await submit(() =>
      createLease(unit.id, {
        tenantId: values.tenantId || null,
        startDate: values.startDate || null,
        endDate: values.endDate || null,
      }),
    )
    if (ok) onDone()
  }

  if (tenants.loading && !tenants.data) return <p className="mt-3 text-sm text-ink-muted">Loading tenants…</p>
  if (tenants.error) return <p className="mt-3 text-sm text-danger">Couldn't load tenants. {tenants.error.message}</p>
  if (tenants.data.length === 0) {
    return (
      <p className="mt-3 text-sm text-ink-muted">
        You have no tenants yet. <Link to="/tenants" className="text-action underline underline-offset-4">Add a tenant</Link>{' '}
        first, then come back to add the lease.
      </p>
    )
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="mt-3 grid gap-4 rounded-md border border-line p-3 sm:grid-cols-3">
      <SelectField
        label="Tenant"
        placeholder="Choose a tenant"
        value={values.tenantId}
        onChange={set('tenantId')}
        options={tenants.data.map((t) => ({ value: t.id, label: t.fullName }))}
        error={fieldErrors.tenantId}
      />
      <TextField label="Start date" type="date" value={values.startDate} onChange={set('startDate')} error={fieldErrors.startDate} />
      <TextField
        label="End date (optional)"
        type="date"
        value={values.endDate}
        min={values.startDate || undefined}
        onChange={set('endDate')}
        error={fieldErrors.endDate}
        hint="Leave empty if ongoing"
      />
      <div className="space-y-3 sm:col-span-3">
        <FormProblem error={error} />
        <div className="flex gap-2">
          <Button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Add lease'}</Button>
          <Button variant="secondary" onClick={onCancel} disabled={busy}>Cancel</Button>
        </div>
      </div>
    </form>
  )
}
