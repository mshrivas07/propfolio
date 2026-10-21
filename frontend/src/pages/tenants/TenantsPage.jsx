import { useState } from 'react'
import { Link } from 'react-router'
import Button from '../../components/ui/Button'
import FormProblem from '../../components/ui/FormProblem'
import Panel from '../../components/ui/Panel'
import { EmptyState, ErrorState, LoadingState } from '../../components/ui/States'
import { useApiData } from '../../hooks/useApiData'
import { useFormSubmit } from '../../hooks/useFormSubmit'
import { createTenant, deleteTenant, listTenants, updateTenant } from '../../lib/api'
import { formatDate } from '../../lib/format'
import TenantForm from './TenantForm'

export default function TenantsPage() {
  const { data, error, loading, reload } = useApiData(listTenants)
  const [adding, setAdding] = useState(false)

  return (
    <section>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Tenants</h1>
          <p className="mt-1 text-sm text-ink-muted">Assign tenants to units from a property's page.</p>
        </div>
        {!adding && <Button onClick={() => setAdding(true)}>Add tenant</Button>}
      </div>

      {adding && (
        <Panel className="mt-6">
          <h2 className="mb-4 font-semibold">New tenant</h2>
          <TenantForm
            submitLabel="Create tenant"
            onSave={async (values) => {
              await createTenant(values)
              setAdding(false)
              reload()
            }}
            onCancel={() => setAdding(false)}
          />
        </Panel>
      )}

      <div className="mt-6">
        {loading && !data && <LoadingState />}
        {error && <ErrorState error={error} onRetry={reload} />}
        {data && data.length === 0 && !adding && (
          <EmptyState title="No tenants yet">Add the people you send invoices to.</EmptyState>
        )}
        {data && data.length > 0 && (
          <ul className="space-y-3">
            {data.map((tenant) => (
              <TenantRow key={tenant.id} tenant={tenant} onChanged={reload} />
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}

function TenantRow({ tenant, onChanged }) {
  const [editing, setEditing] = useState(false)
  const remove = useFormSubmit()
  const activeLease = tenant.leases.find((l) => l.status === 'CURRENT') ?? tenant.leases.find((l) => l.status === 'UPCOMING')

  async function handleDelete() {
    if (!window.confirm(`Delete ${tenant.fullName}?`)) return
    if (await remove.submit(() => deleteTenant(tenant.id))) onChanged()
  }

  if (editing) {
    return (
      <li>
        <Panel>
          <h2 className="mb-4 font-semibold">Edit tenant</h2>
          <TenantForm
            initial={tenant}
            submitLabel="Save changes"
            onSave={async (values) => {
              await updateTenant(tenant.id, values)
              setEditing(false)
              onChanged()
            }}
            onCancel={() => setEditing(false)}
          />
        </Panel>
      </li>
    )
  }

  return (
    <li className="rounded-lg border border-line bg-panel p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="font-medium">{tenant.fullName}</p>
          <p className="break-all text-sm text-ink-muted">{tenant.emails.join(', ')}</p>
          <p className="mt-1 text-sm">
            {activeLease ? (
              <>
                {activeLease.status === 'UPCOMING' ? 'Moving into ' : 'Lives in '}
                <Link to={`/properties/${activeLease.propertyId}`} className="text-action underline-offset-4 hover:underline">
                  {activeLease.unitName}, {activeLease.propertyName}
                </Link>{' '}
                <span className="text-ink-muted">
                  {activeLease.status === 'UPCOMING' ? 'on' : 'since'} {formatDate(activeLease.startDate)}
                </span>
              </>
            ) : (
              <span className="text-ink-muted">No current lease</span>
            )}
          </p>
        </div>
        <div className="flex gap-3">
          <Button variant="link" onClick={() => setEditing(true)}>Edit</Button>
          <Button variant="link" className="text-danger" onClick={handleDelete} disabled={remove.busy}>Delete</Button>
        </div>
      </div>
      {remove.error && <div className="mt-2"><FormProblem error={remove.error} /></div>}
    </li>
  )
}
