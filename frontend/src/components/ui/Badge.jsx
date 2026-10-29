const tones = {
  neutral: 'bg-surface text-ink-muted ring-1 ring-line',
  info: 'bg-action/10 text-action',
  attention: 'bg-attention/10 text-attention',
  success: 'bg-paid/10 text-paid',
  danger: 'bg-danger/10 text-danger',
}

/** Small status label. */
export default function Badge({ tone = 'neutral', children }) {
  return (
    <span className={`inline-block whitespace-nowrap rounded px-2 py-0.5 text-xs font-medium ${tones[tone]}`}>
      {children}
    </span>
  )
}
