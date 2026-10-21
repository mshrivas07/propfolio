import { useCallback, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import Button from '../../components/ui/Button'
import FormProblem from '../../components/ui/FormProblem'
import Panel from '../../components/ui/Panel'
import { ErrorState, LoadingState } from '../../components/ui/States'
import { useApiData } from '../../hooks/useApiData'
import { useFormSubmit } from '../../hooks/useFormSubmit'
import { deleteProperty, getProperty, updateProperty } from '../../lib/api'
import { formatAddress } from '../../lib/format'
import PropertyForm from './PropertyForm'
import SplitRuleGrid from './SplitRuleGrid'
import UnitsSection from './UnitsSection'
import UtilityAccountsSection from './UtilityAccountsSection'

export default function PropertyDetailPage() {
  const { propertyId } = useParams()
  const load = useCallback(() => getProperty(propertyId), [propertyId])
  const { data, error, loading, reload } = useApiData(load)
  const [editing, setEditing] = useState(false)
  const remove = useFormSubmit()
  const navigate = useNavigate()

  if (loading && !data) return <LoadingState />
  if (error && !data) {
    return (
      <>
        <BackLink />
        <div className="mt-4"><ErrorState error={error} onRetry={reload} /></div>
      </>
    )
  }

  const { property, units, utilityAccounts, splitRules, percentTotals } = data

  async function handleSave(values) {
    await updateProperty(property.id, values)
    setEditing(false)
    reload()
  }

  async function handleDelete() {
    if (!window.confirm(`Delete ${property.name}? This can't be undone.`)) return
    const ok = await remove.submit(() => deleteProperty(property.id))
    if (ok) navigate('/properties', { replace: true })
  }

  return (
    <div>
      <BackLink />

      {editing ? (
        <Panel className="mt-4">
          <h1 className="mb-4 text-lg font-semibold">Edit property</h1>
          <PropertyForm initial={property} submitLabel="Save changes" onSave={handleSave} onCancel={() => setEditing(false)} />
        </Panel>
      ) : (
        <div className="mt-4 flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold">{property.name}</h1>
            {formatAddress(property) && <p className="mt-1 text-ink-muted">{formatAddress(property)}</p>}
          </div>
          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => setEditing(true)}>Edit</Button>
            <Button variant="danger" onClick={handleDelete} disabled={remove.busy}>Delete</Button>
          </div>
        </div>
      )}
      <div className="mt-3"><FormProblem error={remove.error} /></div>

      <UnitsSection propertyId={property.id} units={units} onChanged={reload} />
      <UtilityAccountsSection propertyId={property.id} accounts={utilityAccounts} onChanged={reload} />
      <SplitRuleGrid
        units={units}
        accounts={utilityAccounts}
        rules={splitRules}
        percentTotals={percentTotals}
        onChanged={reload}
      />
    </div>
  )
}

function BackLink() {
  return (
    <Link to="/properties" className="text-sm text-action underline-offset-4 hover:underline">
      ← All properties
    </Link>
  )
}
