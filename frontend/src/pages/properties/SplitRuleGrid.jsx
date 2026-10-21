import { useState } from 'react'
import Button from '../../components/ui/Button'
import { SelectField, TextField } from '../../components/ui/Field'
import FormProblem from '../../components/ui/FormProblem'
import Section from '../../components/ui/Section'
import { EmptyState } from '../../components/ui/States'
import { useFormSubmit } from '../../hooks/useFormSubmit'
import { deleteSplitRule, saveSplitRule } from '../../lib/api'
import { formatMoney, formatPercent, utilityTypeLabels } from '../../lib/format'

/**
 * Units (rows) × utility accounts (columns). Each cell is what that unit pays of that account:
 * a percentage or a flat dollar amount. The footer shows each account's percentage total;
 * the landlord pays whatever is left.
 */
export default function SplitRuleGrid({ units, accounts, rules, percentTotals, onChanged }) {
  const [editingCell, setEditingCell] = useState(null) // { unitId, accountId }

  const ruleFor = (unitId, accountId) =>
    rules.find((r) => r.unitId === unitId && r.utilityAccountId === accountId)
  const totalFor = (accountId) => percentTotals.find((t) => t.utilityAccountId === accountId)?.percentTotal ?? 0

  const editing = editingCell && {
    unit: units.find((u) => u.id === editingCell.unitId),
    account: accounts.find((a) => a.id === editingCell.accountId),
    rule: ruleFor(editingCell.unitId, editingCell.accountId),
  }

  return (
    <Section
      title="Split rules"
      description="What share of each utility each unit pays. Percentages on one account can add up to 100%; you pay the rest."
    >
      {units.length === 0 || accounts.length === 0 ? (
        <EmptyState title="Add units and utility accounts first">The grid appears once the property has both.</EmptyState>
      ) : (
        <>
          <div className="overflow-x-auto rounded-lg border border-line bg-panel">
            <table className="w-full min-w-[32rem] text-sm">
              <caption className="sr-only">Share of each utility account paid by each unit</caption>
              <thead>
                <tr className="border-b border-line text-left">
                  <th scope="col" className="px-4 py-3 font-semibold">Unit</th>
                  {accounts.map((account) => (
                    <th key={account.id} scope="col" className="px-4 py-3 font-semibold">
                      {account.providerName}
                      <span className="block text-xs font-normal text-ink-muted">{utilityTypeLabels[account.utilityType]}</span>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {units.map((unit) => (
                  <tr key={unit.id} className="border-b border-line">
                    <th scope="row" className="px-4 py-3 text-left font-medium">{unit.name}</th>
                    {accounts.map((account) => {
                      const rule = ruleFor(unit.id, account.id)
                      const isEditing = editingCell?.unitId === unit.id && editingCell?.accountId === account.id
                      return (
                        <td key={account.id} className="px-2 py-2">
                          <button
                            type="button"
                            onClick={() => setEditingCell({ unitId: unit.id, accountId: account.id })}
                            aria-label={`Edit ${unit.name}'s share of ${account.providerName}`}
                            className={`w-full rounded-md px-2 py-1.5 text-left tabular-nums hover:bg-surface ${
                              isEditing ? 'ring-2 ring-action' : ''
                            }`}
                          >
                            {rule ? (
                              rule.shareType === 'PERCENT' ? formatPercent(rule.shareValue) : `${formatMoney(rule.shareValue)} flat`
                            ) : (
                              <span className="text-ink-muted">—</span>
                            )}
                          </button>
                        </td>
                      )
                    })}
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr className="bg-surface">
                  <th scope="row" className="px-4 py-3 text-left font-medium">Tenants pay (%)</th>
                  {accounts.map((account) => {
                    const total = Number(totalFor(account.id))
                    return (
                      <td key={account.id} className="px-4 py-3 tabular-nums">
                        <span className="font-medium">{formatPercent(total)}</span>
                        <span className="block text-xs text-ink-muted">
                          {total >= 100 ? 'Fully split' : `You pay ${formatPercent(100 - total)}`}
                        </span>
                      </td>
                    )
                  })}
                </tr>
              </tfoot>
            </table>
          </div>
          <p className="mt-2 text-xs text-ink-muted">Flat amounts aren't included in the percentage totals.</p>

          {editing?.unit && editing?.account && (
            <RuleEditor
              key={`${editing.unit.id}-${editing.account.id}`}
              unit={editing.unit}
              account={editing.account}
              rule={editing.rule}
              onClose={() => setEditingCell(null)}
              onSaved={() => {
                setEditingCell(null)
                onChanged()
              }}
            />
          )}
        </>
      )}
    </Section>
  )
}

function RuleEditor({ unit, account, rule, onClose, onSaved }) {
  const [shareType, setShareType] = useState(rule?.shareType ?? 'PERCENT')
  const [shareValue, setShareValue] = useState(rule ? String(Number(rule.shareValue)) : '')
  const { busy, error, fieldErrors, submit } = useFormSubmit()

  async function handleSave(event) {
    event.preventDefault()
    const ok = await submit(() =>
      saveSplitRule({
        unitId: unit.id,
        utilityAccountId: account.id,
        shareType,
        shareValue: shareValue === '' ? null : shareValue,
      }),
    )
    if (ok) onSaved()
  }

  async function handleRemove() {
    if (await submit(() => deleteSplitRule(rule.id))) onSaved()
  }

  return (
    <form onSubmit={handleSave} noValidate className="mt-4 rounded-lg border border-line bg-panel p-4">
      <h3 className="font-semibold">
        {unit.name} · {account.providerName}
      </h3>
      <div className="mt-3 grid gap-4 sm:grid-cols-3">
        <SelectField
          label="Pays"
          value={shareType}
          onChange={(e) => setShareType(e.target.value)}
          options={[
            { value: 'PERCENT', label: 'A percentage of the bill' },
            { value: 'FLAT', label: 'A flat dollar amount' },
          ]}
          error={fieldErrors.shareType}
        />
        <TextField
          label={shareType === 'PERCENT' ? 'Percentage (%)' : 'Amount ($)'}
          type="number"
          inputMode="decimal"
          min="0"
          step="0.01"
          value={shareValue}
          onChange={(e) => setShareValue(e.target.value)}
          error={fieldErrors.shareValue}
          autoFocus
        />
      </div>
      <div className="mt-4 space-y-3">
        <FormProblem error={error} />
        <div className="flex flex-wrap gap-2">
          <Button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save'}</Button>
          <Button variant="secondary" onClick={onClose} disabled={busy}>Cancel</Button>
          {rule && (
            <Button variant="danger" onClick={handleRemove} disabled={busy}>Remove rule</Button>
          )}
        </div>
      </div>
    </form>
  )
}
