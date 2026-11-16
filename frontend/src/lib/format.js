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
  EXTRACTED: 'Read',
  READY: 'Ready to verify',
  NEEDS_REVIEW: 'Needs review',
  VERIFIED: 'Verified',
}

/**
 * The badge for a bill, combining its status with where reading it stands.
 * Returns { label, tone } (tones: see components/ui/Badge).
 */
export function billBadge(bill) {
  if (bill.status === 'VERIFIED') return { label: 'Verified', tone: 'success' }
  if (bill.extractionState === 'RUNNING') return { label: 'Reading…', tone: 'info' }
  if (bill.status === 'READY') return { label: 'Ready to verify', tone: 'info' }
  if (bill.status === 'NEEDS_REVIEW') return { label: 'Needs review', tone: 'attention' }
  if (bill.extractionState === 'FAILED') return { label: 'Reading failed', tone: 'danger' }
  return { label: 'Not read yet', tone: 'neutral' }
}

/** "Sep 1 – Sep 23, 2026"-style range; empty when either end is missing. */
export function formatPeriod(start, end) {
  return start && end ? `${formatDate(start)} – ${formatDate(end)}` : ''
}

export const invoiceStatusLabels = {
  OPEN: 'Open',
  EMAIL_DRAFTED: 'Email drafted',
  PARTIALLY_PAID: 'Partially paid',
  PAID: 'Paid',
  VOID: 'Void',
}

const invoiceTones = { OPEN: 'info', EMAIL_DRAFTED: 'neutral', PARTIALLY_PAID: 'attention', PAID: 'success', VOID: 'neutral' }

export function invoiceBadge(invoice) {
  return { label: invoiceStatusLabels[invoice.status] ?? invoice.status, tone: invoiceTones[invoice.status] ?? 'neutral' }
}

export const splitBasisLabels = {
  PLAN_PAYMENT: 'Plan payment (amount due)',
  CURRENT_CHARGES: 'Current charges',
}
