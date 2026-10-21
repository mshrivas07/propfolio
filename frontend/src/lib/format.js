const dateFormat = new Intl.DateTimeFormat('en-CA', { year: 'numeric', month: 'short', day: 'numeric', timeZone: 'UTC' })
const moneyFormat = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' })

/** "2026-09-01" → "Sep 1, 2026". Dates are calendar dates, so format in UTC to avoid shifting a day. */
export function formatDate(isoDate) {
  return isoDate ? dateFormat.format(new Date(`${isoDate}T00:00:00Z`)) : ''
}

export function formatMoney(value) {
  return moneyFormat.format(Number(value))
}

export function formatPercent(value) {
  return `${Number(value).toLocaleString('en-CA', { maximumFractionDigits: 4 })}%`
}

export const utilityTypeLabels = {
  ELECTRICITY: 'Electricity',
  GAS: 'Gas',
  WATER: 'Water',
  INTERNET: 'Internet',
  OTHER: 'Other',
}

export function formatAddress(p) {
  return [p.addressLine1, p.addressLine2, p.city, p.province, p.postalCode].filter(Boolean).join(', ')
}
