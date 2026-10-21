export function LoadingState({ label = 'Loading…' }) {
  return (
    <p role="status" className="py-8 text-sm text-ink-muted">
      {label}
    </p>
  )
}

export function ErrorState({ error, onRetry }) {
  return (
    <div role="alert" className="rounded-md border border-danger/30 bg-panel p-4 text-sm">
      <p className="font-medium text-danger">{error?.status === 404 ? 'Not found.' : "Couldn't load this."}</p>
      <p className="mt-1 text-ink-muted">{error?.message}</p>
      {onRetry && error?.status !== 404 && (
        <button type="button" onClick={onRetry} className="mt-3 text-action underline underline-offset-4">
          Try again
        </button>
      )}
    </div>
  )
}

export function EmptyState({ title, children }) {
  return (
    <div className="rounded-md border border-dashed border-line bg-panel px-4 py-8 text-center">
      <p className="font-medium">{title}</p>
      {children && <div className="mt-1 text-sm text-ink-muted">{children}</div>}
    </div>
  )
}
