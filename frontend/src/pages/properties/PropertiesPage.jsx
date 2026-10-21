import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import Button from '../../components/ui/Button'
import Panel from '../../components/ui/Panel'
import { EmptyState, ErrorState, LoadingState } from '../../components/ui/States'
import { useApiData } from '../../hooks/useApiData'
import { createProperty, listProperties } from '../../lib/api'
import { formatAddress } from '../../lib/format'
import PropertyForm from './PropertyForm'

export default function PropertiesPage() {
  const { data, error, loading, reload } = useApiData(listProperties)
  const [adding, setAdding] = useState(false)
  const navigate = useNavigate()

  async function handleCreate(values) {
    const created = await createProperty(values)
    navigate(`/properties/${created.id}`)
  }

  return (
    <section>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Properties</h1>
        {!adding && <Button onClick={() => setAdding(true)}>Add property</Button>}
      </div>

      {adding && (
        <Panel className="mt-6">
          <h2 className="mb-4 font-semibold">New property</h2>
          <PropertyForm submitLabel="Create property" onSave={handleCreate} onCancel={() => setAdding(false)} />
        </Panel>
      )}

      <div className="mt-6">
        {loading && !data && <LoadingState />}
        {error && <ErrorState error={error} onRetry={reload} />}
        {data && data.length === 0 && !adding && (
          <EmptyState title="No properties yet">
            Add your first property, then its units and utility accounts.
          </EmptyState>
        )}
        {data && data.length > 0 && (
          <ul className="divide-y divide-line overflow-hidden rounded-lg border border-line bg-panel">
            {data.map((property) => (
              <li key={property.id}>
                <Link
                  to={`/properties/${property.id}`}
                  className="flex flex-wrap items-center justify-between gap-2 px-4 py-3 hover:bg-surface"
                >
                  <span>
                    <span className="block font-medium">{property.name}</span>
                    {formatAddress(property) && (
                      <span className="block text-sm text-ink-muted">{formatAddress(property)}</span>
                    )}
                  </span>
                  <span className="text-sm text-ink-muted">
                    {property.unitCount} {property.unitCount === 1 ? 'unit' : 'units'} ·{' '}
                    {property.utilityAccountCount} {property.utilityAccountCount === 1 ? 'utility' : 'utilities'}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}
