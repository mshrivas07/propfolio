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

const dateTimeFormat = new Intl.DateTimeFormat('en-CA', { dateStyle: 'medium', timeStyle: 'short' })

/** An instant ("2026-10-21T14:05:00Z") in the viewer's local time. */
export function formatDateTime(isoInstant) {
  return isoInstant ? dateTimeFormat.format(new Date(isoInstant)) : ''
}

export function formatFileSize(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export const billStatusLabels = {
  UPLOADED: 'Uploaded',
  EXTRACTED: 'Extracted',
  READY: 'Ready to verify',
  NEEDS_REVIEW: 'Needs review',
  VERIFIED: 'Verified',
}

/** Badge tone per bill status (see components/ui/Badge). */
export const billStatusTones = {
  UPLOADED: 'neutral',
  EXTRACTED: 'info',
  READY: 'info',
  NEEDS_REVIEW: 'attention',
  VERIFIED: 'success',
}
