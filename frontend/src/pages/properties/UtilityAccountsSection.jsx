import { useState } from 'react'
import Button from '../../components/ui/Button'
import { SelectField, TextField } from '../../components/ui/Field'
import FormProblem from '../../components/ui/FormProblem'
import Panel from '../../components/ui/Panel'
import Section from '../../components/ui/Section'
import { EmptyState } from '../../components/ui/States'
import { useFormSubmit } from '../../hooks/useFormSubmit'
import { createUtilityAccount, deleteUtilityAccount, updateUtilityAccount } from '../../lib/api'
import { utilityTypeLabels } from '../../lib/format'

const typeOptions = Object.entries(utilityTypeLabels).map(([value, label]) => ({ value, label }))

export default function UtilityAccountsSection({ propertyId, accounts, onChanged }) {
  const [adding, setAdding] = useState(false)

  return (
    <Section
      title="Utility accounts"
      description="One per bill you receive for this property. The account number helps match uploaded bills later."
      actions={!adding && <Button variant="secondary" onClick={() => setAdding(true)}>Add utility account</Button>}
    >
      {adding && (
        <Panel className="mb-4">
          <AccountForm
            submitLabel="Add utility account"
            onSave={async (values) => {
              await createUtilityAccount(propertyId, values)
              setAdding(false)
              onChanged()
            }}
            onCancel={() => setAdding(false)}
          />
        </Panel>
      )}

      {accounts.length === 0 && !adding ? (
        <EmptyState title="No utility accounts yet">Add electricity, gas, water or internet accounts.</EmptyState>
      ) : (
        accounts.length > 0 && (
          <ul className="divide-y divide-line rounded-lg border border-line bg-panel">
            {accounts.map((account) => (
              <AccountRow key={account.id} account={account} onChanged={onChanged} />
            ))}
          </ul>
        )
      )}
    </Section>
  )
}

function AccountRow({ account, onChanged }) {
  const [editing, setEditing] = useState(false)
  const remove = useFormSubmit()

  async function handleDelete() {
    if (!window.confirm(`Delete ${account.providerName}? Its split rules will be deleted too.`)) return
    if (await remove.submit(() => deleteUtilityAccount(account.id))) onChanged()
  }

  if (editing) {
    return (
      <li className="p-4">
        <AccountForm
          initial={account}
          submitLabel="Save"
          onSave={async (values) => {
            await updateUtilityAccount(account.id, values)
            setEditing(false)
            onChanged()
          }}
          onCancel={() => setEditing(false)}
        />
      </li>
    )
  }

  return (
    <li className="px-4 py-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span>
          <span className="font-medium">{account.providerName}</span>
          <span className="ml-2 text-sm text-ink-muted">
            {utilityTypeLabels[account.utilityType]}
            {account.accountNumber && ` · Account ${account.accountNumber}`}
          </span>
        </span>
        <span className="flex gap-3">
          <Button variant="link" onClick={() => setEditing(true)}>Edit</Button>
          <Button variant="link" className="text-danger" onClick={handleDelete} disabled={remove.busy}>Delete</Button>
        </span>
      </div>
      {remove.error && <div className="mt-2"><FormProblem error={remove.error} /></div>}
    </li>
  )
}

function AccountForm({ initial, submitLabel, onSave, onCancel }) {
  const [values, setValues] = useState({
    utilityType: initial?.utilityType ?? '',
    providerName: initial?.providerName ?? '',
    accountNumber: initial?.accountNumber ?? '',
  })
  const { busy, error, fieldErrors, submit } = useFormSubmit()
  const set = (field) => (event) => setValues((v) => ({ ...v, [field]: event.target.value }))

  return (
    <form
      noValidate
      onSubmit={(event) => {
        event.preventDefault()
        submit(() => onSave({ ...values, utilityType: values.utilityType || null }))
      }}
      className="grid gap-4 sm:grid-cols-3"
    >
      <SelectField
        label="Type"
        placeholder="Choose a type"
        options={typeOptions}
        value={values.utilityType}
        onChange={set('utilityType')}
        error={fieldErrors.utilityType}
      />
      <TextField label="Provider" value={values.providerName} onChange={set('providerName')} error={fieldErrors.providerName} />
      <TextField
        label="Account number (optional)"
        value={values.accountNumber}
        onChange={set('accountNumber')}
        error={fieldErrors.accountNumber}
      />
      <div className="space-y-3 sm:col-span-3">
        <FormProblem error={error} />
        <div className="flex gap-2">
          <Button type="submit" disabled={busy}>{busy ? 'Saving…' : submitLabel}</Button>
          <Button variant="secondary" onClick={onCancel} disabled={busy}>Cancel</Button>
        </div>
      </div>
    </form>
  )
}
