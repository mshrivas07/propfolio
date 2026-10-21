import { useState } from 'react'

/**
 * Runs an API call for a form and keeps the error (message + field errors) for display.
 * Returns true when the call succeeded.
 */
export function useFormSubmit() {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  async function submit(action) {
    setBusy(true)
    setError(null)
    try {
      await action()
      return true
    } catch (err) {
      setError(err)
      return false
    } finally {
      setBusy(false)
    }
  }

  return { busy, error, fieldErrors: error?.fieldErrors ?? {}, submit, clearError: () => setError(null) }
}
