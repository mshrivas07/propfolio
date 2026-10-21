import { useState } from 'react'
import Button from '../../components/ui/Button'
import { TextField } from '../../components/ui/Field'
import FormProblem from '../../components/ui/FormProblem'
import Panel from '../../components/ui/Panel'
import Section from '../../components/ui/Section'
import { EmptyState } from '../../components/ui/States'
import { useFormSubmit } from '../../hooks/useFormSubmit'
import { createUnit, deleteUnit, updateUnit } from '../../lib/api'
import LeasePanel from './LeasePanel'

export default function UnitsSection({ propertyId, units, onChanged }) {
  const [adding, setAdding] = useState(false)

  return (
    <Section
      title="Units"
      description="Each unit can have one lease at a time."
      actions={!adding && <Button variant="secondary" onClick={() => setAdding(true)}>Add unit</Button>}
    >
      {adding && (
        <Panel className="mb-4">
          <UnitForm
            submitLabel="Add unit"
            onSave={async (values) => {
              await createUnit(propertyId, values)
              setAdding(false)
              onChanged()
            }}
            onCancel={() => setAdding(false)}
          />
        </Panel>
      )}

      {units.length === 0 && !adding ? (
        <EmptyState title="No units yet">Add one unit per household you bill, e.g. "Main floor" and "Basement".</EmptyState>
      ) : (
        <ul className="space-y-3">
          {units.map((unit) => (
            <UnitRow key={unit.id} unit={unit} onChanged={onChanged} />
          ))}
        </ul>
      )}
    </Section>
  )
}

function UnitRow({ unit, onChanged }) {
  const [editing, setEditing] = useState(false)
  const remove = useFormSubmit()

  async function handleDelete() {
    if (!window.confirm(`Delete ${unit.name}? Its split rules will be deleted too.`)) return
    if (await remove.submit(() => deleteUnit(unit.id))) onChanged()
  }

  return (
    <li className="rounded-lg border border-line bg-panel p-4">
      {editing ? (
        <UnitForm
          initial={unit}
          submitLabel="Save unit"
          onSave={async (values) => {
            await updateUnit(unit.id, values)
            setEditing(false)
            onChanged()
          }}
          onCancel={() => setEditing(false)}
        />
      ) : (
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div>
            <h3 className="font-semibold">{unit.name}</h3>
            {unit.notes && <p className="text-sm text-ink-muted">{unit.notes}</p>}
          </div>
          <div className="flex gap-3">
            <Button variant="link" onClick={() => setEditing(true)}>Rename</Button>
            <Button variant="link" className="text-danger" onClick={handleDelete} disabled={remove.busy}>Delete</Button>
          </div>
        </div>
      )}
      {remove.error && <div className="mt-3"><FormProblem error={remove.error} /></div>}
      <LeasePanel unit={unit} onChanged={onChanged} />
    </li>
  )
}

function UnitForm({ initial, submitLabel, onSave, onCancel }) {
  const [name, setName] = useState(initial?.name ?? '')
  const [notes, setNotes] = useState(initial?.notes ?? '')
  const { busy, error, fieldErrors, submit } = useFormSubmit()

  return (
    <form
      noValidate
      onSubmit={(event) => {
        event.preventDefault()
        submit(() => onSave({ name, notes }))
      }}
      className="grid gap-4 sm:grid-cols-2"
    >
      <TextField label="Unit name" value={name} onChange={(e) => setName(e.target.value)} error={fieldErrors.name} autoFocus />
      <TextField label="Notes (optional)" value={notes} onChange={(e) => setNotes(e.target.value)} error={fieldErrors.notes} />
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
