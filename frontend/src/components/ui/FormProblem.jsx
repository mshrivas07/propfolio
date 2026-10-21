/**
 * Shows an API error above a form's buttons. Field-level problems are shown under
 * their inputs, so for a 400 this only shows a short summary.
 */
export default function FormProblem({ error }) {
  if (!error) return null
  const hasFieldErrors = Object.keys(error.fieldErrors ?? {}).length > 0
  return (
    <p role="alert" className="rounded-md border border-danger/30 bg-danger/5 px-3 py-2 text-sm text-danger">
      {hasFieldErrors ? 'Check the highlighted fields.' : error.message}
    </p>
  )
}
